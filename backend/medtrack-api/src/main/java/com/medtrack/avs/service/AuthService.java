package com.medtrack.avs.service;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.AuthProvider;
import com.medtrack.avs.entity.Role;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.mapper.UserMapper;
import com.medtrack.avs.repository.RoleRepository;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.security.CustomUserDetailsService;
import com.medtrack.avs.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AuthService {

    @Value("${medtrack.admin.emails:vigneshwarans4444@gmail.com}")
    private String adminEmailsConfig;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @org.springframework.context.annotation.Lazy AuthenticationManager authenticationManager,
            CustomUserDetailsService userDetailsService,
            UserMapper userMapper,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    void setAdminEmailsConfig(String adminEmailsConfig) {
        this.adminEmailsConfig = adminEmailsConfig;
    }

    /**
     * Strict check whether an email address is authorized for ADMIN role via MEDTRACK_ADMIN_EMAILS allowlist.
     * Normalization: trimmed, case-insensitive, exact match. Wildcards are strictly disallowed.
     */
    public boolean isAdminAllowlisted(String email) {
        if (email == null || email.isBlank() || adminEmailsConfig == null || adminEmailsConfig.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        String[] allowed = adminEmailsConfig.split(",");
        for (String candidate : allowed) {
            String clean = candidate.trim().toLowerCase();
            // Disallow wildcards or patterns (*@gmail.com etc)
            if (!clean.isEmpty() && !clean.contains("*") && clean.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    // Secure in-memory store for short-lived one-time OAuth2 authorization exchange codes (60s TTL)
    private record OneTimeAuthEntry(String email, Instant expiresAt) {}
    private final Map<String, OneTimeAuthEntry> oneTimeAuthCodes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already in use: " + normalizedEmail);
        }

        Role staffRole = roleRepository.findByName(RoleType.STAFF)
                .orElseThrow(() -> new IllegalStateException("Default STAFF role not initialized in database"));

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(staffRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        auditService.logAuthEvent(savedUser, "REGISTRATION", "User registered successfully with local credentials");
        log.info("Registered new user with email: {}", normalizedEmail);

        return userMapper.toResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalizedEmail));

            if (!user.isEnabled()) {
                throw new DisabledException("User account is disabled");
            }

            String token = jwtService.generateToken(userDetails);

            auditService.logAuthEvent(user, "LOGIN_SUCCESS", "User logged in successfully via local credentials");
            log.info("Successful local login for user: {}", normalizedEmail);

            return AuthResponse.builder()
                    .accessToken(token)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getExpirationInSeconds())
                    .user(userMapper.toResponse(user))
                    .build();

        } catch (DisabledException ex) {
            log.warn("Login attempt for disabled account: {}", normalizedEmail);
            auditService.logAuthEvent(null, "LOGIN_FAILURE", "Login attempt for disabled account: " + normalizedEmail);
            throw ex;
        } catch (BadCredentialsException ex) {
            log.warn("Invalid credentials for email: {}", normalizedEmail);
            auditService.logAuthEvent(null, "LOGIN_FAILURE", "Invalid credentials for email: " + normalizedEmail);
            throw new BadCredentialsException("Invalid email or password");
        } catch (Exception ex) {
            log.warn("Authentication failed for email {}: {}", normalizedEmail, ex.getMessage());
            auditService.logAuthEvent(null, "LOGIN_FAILURE", "Authentication failed for email: " + normalizedEmail);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    @Transactional
    public void logout(String email) {
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user ->
                    auditService.logAuthEvent(user, "LOGOUT", "User logged out")
            );
        }
        log.info("Logged out user: {}", email);
    }

    @Transactional
    public String processGoogleUser(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Google account did not provide a verified email address");
        }
        String normalizedEmail = email.trim().toLowerCase();

        String name = oAuth2User.getAttribute("name");
        if (name == null || name.isBlank()) {
            name = "Google User";
        }

        String googleId = oAuth2User.getAttribute("sub");
        if (googleId == null || googleId.isBlank()) {
            googleId = oAuth2User.getName();
        }

        String picture = oAuth2User.getAttribute("picture");
        boolean isAllowlistedAdmin = isAdminAllowlisted(normalizedEmail);

        // 1. Try finding by googleId
        User user = userRepository.findByGoogleId(googleId).orElse(null);

        // 2. If not found, try finding by email
        if (user == null) {
            user = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (user != null) {
                // Link Google identity safely to existing user
                user.setGoogleId(googleId);
                if (user.getProfileImage() == null && picture != null) {
                    user.setProfileImage(picture);
                }
                // If user is allowlisted for ADMIN, promote them to ADMIN
                if (isAllowlistedAdmin && (user.getRole() == null || user.getRole().getName() != RoleType.ADMIN)) {
                    Role adminRole = roleRepository.findByName(RoleType.ADMIN)
                            .orElseThrow(() -> new IllegalStateException("ADMIN role not initialized"));
                    user.setRole(adminRole);
                    log.info("Promoted allowlisted existing user {} to ADMIN via Google OAuth", normalizedEmail);
                }
                user = userRepository.saveAndFlush(user);
                log.info("Linked Google identity to existing user: {}", normalizedEmail);
            } else {
                // Provision new user: ADMIN if explicitly allowlisted, else default STAFF
                Role assignedRole;
                if (isAllowlistedAdmin) {
                    assignedRole = roleRepository.findByName(RoleType.ADMIN)
                            .orElseThrow(() -> new IllegalStateException("ADMIN role not initialized"));
                    log.info("Provisioning new Google user {} as allowlisted ADMIN", normalizedEmail);
                } else {
                    assignedRole = roleRepository.findByName(RoleType.STAFF)
                            .orElseThrow(() -> new IllegalStateException("Default STAFF role not initialized"));
                    log.info("Provisioning new Google user {} as default STAFF", normalizedEmail);
                }

                user = User.builder()
                        .name(name)
                        .email(normalizedEmail)
                        .googleId(googleId)
                        .authProvider(AuthProvider.GOOGLE)
                        .profileImage(picture)
                        .role(assignedRole)
                        .enabled(true)
                        .build();

                user = userRepository.saveAndFlush(user);
            }
        } else {
            // Existing user found by googleId:
            // If user is allowlisted for ADMIN and currently not ADMIN, promote them
            if (isAllowlistedAdmin && (user.getRole() == null || user.getRole().getName() != RoleType.ADMIN)) {
                Role adminRole = roleRepository.findByName(RoleType.ADMIN)
                        .orElseThrow(() -> new IllegalStateException("ADMIN role not initialized"));
                user.setRole(adminRole);
                log.info("Promoted allowlisted Google user {} to ADMIN", normalizedEmail);
            }
            // Update profile image if newly available
            if (picture != null && !picture.equals(user.getProfileImage())) {
                user.setProfileImage(picture);
            }
            user = userRepository.saveAndFlush(user);
        }

        auditService.logAuthEvent(user, "GOOGLE_LOGIN_SUCCESS", "Google OAuth2 sign-in successful");

        return createOneTimeCode(user.getEmail());
    }

    public String createOneTimeCode(String email) {
        cleanExpiredCodes();
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String code = UUID.randomUUID().toString() + "-" + bytesToHex(randomBytes);

        // Short-lived 60 seconds expiration
        oneTimeAuthCodes.put(code, new OneTimeAuthEntry(email, Instant.now().plusSeconds(60)));
        return code;
    }

    @Transactional
    public AuthResponse exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Authorization code is required");
        }

        OneTimeAuthEntry entry = oneTimeAuthCodes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Invalid, expired, or already used authorization code");
        }

        User user = userRepository.findByEmail(entry.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + entry.email()));

        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(entry.email());
        String token = jwtService.generateToken(userDetails);

        auditService.logAuthEvent(user, "LOGIN_SUCCESS", "OAuth2 authorization code exchanged successfully for JWT");

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(userMapper.toResponse(user))
                .build();
    }

    private void cleanExpiredCodes() {
        Instant now = Instant.now();
        oneTimeAuthCodes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
