package com.medtrack.avs.service;

import com.medtrack.avs.dto.AuthResponse;
import com.medtrack.avs.entity.AuthProvider;
import com.medtrack.avs.entity.Role;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.repository.RoleRepository;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoogleProvisioningAndExchangeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        if (!roleRepository.existsByName(RoleType.STAFF)) {
            roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build());
        }
        if (!roleRepository.existsByName(RoleType.ADMIN)) {
            roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build());
        }
    }

    @Test
    @DisplayName("Google Provisioning - New Google user creates account with STAFF role, GOOGLE provider and one-time code")
    void testNewGoogleUserProvisioning() {
        String googleEmail = "google_new_" + System.currentTimeMillis() + "@gmail.com";
        String googleId = "sub_goog_" + System.currentTimeMillis();

        OAuth2User mockOAuth2User = Mockito.mock(OAuth2User.class);
        Mockito.when(mockOAuth2User.getAttribute("email")).thenReturn(googleEmail);
        Mockito.when(mockOAuth2User.getAttribute("name")).thenReturn("Google Test User");
        Mockito.when(mockOAuth2User.getAttribute("sub")).thenReturn(googleId);
        Mockito.when(mockOAuth2User.getAttribute("picture")).thenReturn("https://lh3.googleusercontent.com/photo.jpg");

        String code = authService.processGoogleUser(mockOAuth2User);
        assertNotNull(code);
        assertFalse(code.isBlank());

        // Verify MySQL user was created with STAFF role and GOOGLE authProvider
        Optional<User> userOpt = userRepository.findByEmail(googleEmail);
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();
        assertEquals("Google Test User", user.getName());
        assertEquals(googleId, user.getGoogleId());
        assertEquals(AuthProvider.GOOGLE, user.getAuthProvider());
        assertEquals(RoleType.STAFF, user.getRole().getName());
        assertNull(user.getPasswordHash()); // No password hash for OAuth2 user

        // Verify one-time exchange
        AuthResponse response = authService.exchangeCode(code);
        assertNotNull(response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(googleEmail, response.getUser().getEmail());
        assertTrue(jwtService.isTokenValid(response.getAccessToken()));

        // Verify code is single-use and cannot be used twice
        assertThrows(BadRequestException.class, () -> authService.exchangeCode(code));
    }

    @Test
    @DisplayName("Google Provisioning - Existing local user links Google identity without changing role")
    void testExistingUserGoogleLinking() {
        String existingEmail = "doctor_existing_" + System.currentTimeMillis() + "@hospital.org";
        String googleId = "sub_existing_" + System.currentTimeMillis();

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
        User localUser = User.builder()
                .name("Dr. Established")
                .email(existingEmail)
                .passwordHash(passwordEncoder.encode("SecretPass123"))
                .role(adminRole) // Has ADMIN role
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build();
        userRepository.save(localUser);

        OAuth2User mockOAuth2User = Mockito.mock(OAuth2User.class);
        Mockito.when(mockOAuth2User.getAttribute("email")).thenReturn(existingEmail);
        Mockito.when(mockOAuth2User.getAttribute("name")).thenReturn("Dr. Established");
        Mockito.when(mockOAuth2User.getAttribute("sub")).thenReturn(googleId);
        Mockito.when(mockOAuth2User.getAttribute("picture")).thenReturn("https://lh3.googleusercontent.com/avatar.jpg");

        String code = authService.processGoogleUser(mockOAuth2User);
        assertNotNull(code);

        // Verify role remained ADMIN (did not get demoted or overwritten)
        User updatedUser = userRepository.findByEmail(existingEmail).orElseThrow();
        assertEquals(RoleType.ADMIN, updatedUser.getRole().getName());
        assertEquals(googleId, updatedUser.getGoogleId());
        assertEquals("https://lh3.googleusercontent.com/avatar.jpg", updatedUser.getProfileImage());

        // Verify exchange issues token with ADMIN role
        AuthResponse response = authService.exchangeCode(code);
        assertTrue(response.getUser().getRoles().contains("ADMIN"));
    }

    @Test
    @DisplayName("Google Provisioning - Allowlisted Google user receives ADMIN role while normal receives STAFF")
    void testAllowlistedGoogleUserReceivesAdminRole() {
        String allowlistedEmail = "vigneshwarans4444@gmail.com";
        String googleId = "sub_allowlisted_" + System.currentTimeMillis();

        OAuth2User mockOAuth2User = Mockito.mock(OAuth2User.class);
        Mockito.when(mockOAuth2User.getAttribute("email")).thenReturn("  " + allowlistedEmail.toUpperCase() + "  ");
        Mockito.when(mockOAuth2User.getAttribute("name")).thenReturn("Vigneshwaran Admin");
        Mockito.when(mockOAuth2User.getAttribute("sub")).thenReturn(googleId);

        String code = authService.processGoogleUser(mockOAuth2User);
        assertNotNull(code);

        User user = userRepository.findByEmail(allowlistedEmail).orElseThrow();
        assertEquals(RoleType.ADMIN, user.getRole().getName(), "Allowlisted user must have ADMIN role");
        assertEquals(AuthProvider.GOOGLE, user.getAuthProvider());

        AuthResponse response = authService.exchangeCode(code);
        assertTrue(response.getUser().getRoles().contains("ADMIN"), "Token must contain ADMIN role");
    }

    @Test
    @DisplayName("Google Provisioning - Existing STAFF user promoted to ADMIN if in allowlist")
    void testExistingStaffUserPromotedToAdminWhenAllowlisted() {
        String testEmail = "promote_test_" + System.currentTimeMillis() + "@gmail.com";
        authService.setAdminEmailsConfig(testEmail + ",other@medtrack.local");

        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseThrow();
        User existingStaff = User.builder()
                .name("Staff Person")
                .email(testEmail)
                .role(staffRole)
                .authProvider(AuthProvider.GOOGLE)
                .enabled(true)
                .build();
        userRepository.save(existingStaff);

        OAuth2User mockOAuth2User = Mockito.mock(OAuth2User.class);
        Mockito.when(mockOAuth2User.getAttribute("email")).thenReturn(testEmail);
        Mockito.when(mockOAuth2User.getAttribute("name")).thenReturn("Staff Person");
        Mockito.when(mockOAuth2User.getAttribute("sub")).thenReturn("sub_promote_" + System.currentTimeMillis());

        String code = authService.processGoogleUser(mockOAuth2User);
        assertNotNull(code);

        User updatedUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertEquals(RoleType.ADMIN, updatedUser.getRole().getName(), "Existing STAFF must be promoted to ADMIN if in allowlist");

        // Restore default allowlist config
        authService.setAdminEmailsConfig("vigneshwarans4444@gmail.com");
    }

    @Test
    @DisplayName("Google Provisioning - Wildcards in allowlist are rejected")
    void testWildcardsInAllowlistDisallowed() {
        authService.setAdminEmailsConfig("*@gmail.com,admin@medtrack.local");

        assertFalse(authService.isAdminAllowlisted("random@gmail.com"), "Wildcards must not allow arbitrary emails");
        assertFalse(authService.isAdminAllowlisted("*@gmail.com"), "Literal wildcard must not be matched");
        assertTrue(authService.isAdminAllowlisted("admin@medtrack.local"), "Exact normalized match must pass");

        // Restore default allowlist config
        authService.setAdminEmailsConfig("vigneshwarans4444@gmail.com");
    }

    @Test
    @DisplayName("Google Admin - Authorizations to /api/admin/**, /api/assets, /api/transfers, /api/analytics, /api/geofences, /api/alerts")
    void testGoogleAdminApiAuthorizations() throws Exception {
        String adminEmail = "vigneshwarans4444@gmail.com";
        OAuth2User mockAdminUser = Mockito.mock(OAuth2User.class);
        Mockito.when(mockAdminUser.getAttribute("email")).thenReturn(adminEmail);
        Mockito.when(mockAdminUser.getAttribute("name")).thenReturn("Vigneshwaran S");
        Mockito.when(mockAdminUser.getAttribute("sub")).thenReturn("google_sub_admin_123");

        String code = authService.processGoogleUser(mockAdminUser);
        AuthResponse authResponse = authService.exchangeCode(code);
        String token = authResponse.getAccessToken();

        // Safe JWT authority check: no full token logged
        List<String> roles = jwtService.extractRoles(token);
        assertTrue(roles.contains("ROLE_ADMIN") || roles.contains("ADMIN"), "JWT authority must reflect ADMIN");

        // Verify ADMIN access to /api/admin/dashboard
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify ADMIN access to /api/assets
        mockMvc.perform(get("/api/assets")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify ADMIN access to /api/transfers
        mockMvc.perform(get("/api/transfers")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify ADMIN access to /api/analytics/overview
        mockMvc.perform(get("/api/analytics/overview")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify ADMIN access to /api/geofences
        mockMvc.perform(get("/api/geofences")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify ADMIN access to /api/alerts
        mockMvc.perform(get("/api/alerts")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Google Staff - STAFF Google account is rejected from /api/admin/** with 403 Forbidden")
    void testGoogleStaffDeniedAdminEndpoint() throws Exception {
        String staffEmail = "ordinary_staff_" + System.currentTimeMillis() + "@gmail.com";
        OAuth2User mockStaffUser = Mockito.mock(OAuth2User.class);
        Mockito.when(mockStaffUser.getAttribute("email")).thenReturn(staffEmail);
        Mockito.when(mockStaffUser.getAttribute("name")).thenReturn("Ordinary Staff");
        Mockito.when(mockStaffUser.getAttribute("sub")).thenReturn("google_sub_staff_" + System.currentTimeMillis());

        String code = authService.processGoogleUser(mockStaffUser);
        AuthResponse authResponse = authService.exchangeCode(code);
        String token = authResponse.getAccessToken();

        // Safe JWT authority check
        List<String> roles = jwtService.extractRoles(token);
        assertTrue(roles.contains("ROLE_STAFF") || roles.contains("STAFF"));
        assertFalse(roles.contains("ROLE_ADMIN"));

        // Verify STAFF is rejected from /api/admin/** with 403 Forbidden
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Verify STAFF can access /api/assets (allowed for authenticated users)
        mockMvc.perform(get("/api/assets")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
