package com.medtrack.avs.websocket;

import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.AssetAssignmentRepository;
import com.medtrack.avs.repository.GpsSessionRepository;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.security.CustomUserDetailsService;
import com.medtrack.avs.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * ChannelInterceptor enforcing JWT authentication on STOMP CONNECT
 * and fine-grained role/assignment authorization on STOMP SUBSCRIBE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final GpsSessionRepository gpsSessionRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 1. Authenticate STOMP CONNECT frame with JWT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || token.trim().isEmpty()) {
                log.warn("STOMP CONNECT rejected: missing Authorization header");
                throw new AuthenticationCredentialsNotFoundException("STOMP CONNECT requires Authorization: Bearer <token>");
            }

            if (!jwtService.isTokenValid(token)) {
                log.warn("STOMP CONNECT rejected: invalid or expired JWT token");
                throw new BadCredentialsException("Invalid or expired JWT token");
            }

            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            if (!accessor.isMutable()) {
                StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(accessor.getCommand());
                if (accessor.toNativeHeaderMap() != null) {
                    accessor.toNativeHeaderMap().forEach((k, v) -> {
                        for (String val : v) {
                            mutableAccessor.addNativeHeader(k, val);
                        }
                    });
                }
                mutableAccessor.setUser(auth);
                log.info("STOMP CONNECT authenticated successfully for user: {}", username);
                return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
            }

            accessor.setUser(auth);
            log.info("STOMP CONNECT authenticated successfully for user: {}", username);
        }

        // 2. Authorize STOMP SUBSCRIBE frames based on role and destination
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) {
                log.warn("STOMP SUBSCRIBE rejected: unauthenticated session");
                throw new AccessDeniedException("Unauthenticated subscription attempt rejected");
            }

            String destination = accessor.getDestination();
            if (destination != null) {
                authorizeSubscription(principal, destination);
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            authHeader = accessor.getFirstNativeHeader("authorization");
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        if (authHeader != null && !authHeader.isEmpty()) {
            return authHeader.trim();
        }

        // Fallback to 'token' or 'passcode' native headers
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isEmpty()) {
            return token.trim();
        }
        String passcode = accessor.getPasscode();
        if (passcode != null && !passcode.isEmpty()) {
            return passcode.trim();
        }

        return null;
    }

    private void authorizeSubscription(Principal principal, String destination) {
        if (!(principal instanceof Authentication auth)) {
            throw new AccessDeniedException("Invalid authentication principal");
        }

        boolean isAdminOrSupervisor = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPERVISOR"));

        // Global tracking topic: /topic/tracking/assets
        if ("/topic/tracking/assets".equals(destination)) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for user {}", destination, principal.getName());
                return;
            }
            log.warn("Unauthorized subscription to global tracking by STAFF user {}", principal.getName());
            throw new AccessDeniedException("Staff members are not authorized to subscribe to the global asset tracking stream. Subscribe to your assigned asset stream instead: /topic/tracking/assets/{assetId}");
        }

        // Asset-specific tracking topic: /topic/tracking/assets/{assetId}
        if (destination.startsWith("/topic/tracking/assets/")) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for privileged user {}", destination, principal.getName());
                return;
            }

            // For STAFF: must be assigned to asset or have started its active session
            String assetIdStr = destination.substring("/topic/tracking/assets/".length()).trim();
            try {
                Long assetId = Long.parseLong(assetIdStr);
                User user = userRepository.findByEmail(principal.getName())
                        .orElseThrow(() -> new AccessDeniedException("User account not found: " + principal.getName()));

                boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(assetId, user.getId());
                boolean isSessionOwner = gpsSessionRepository.findByAssetIdAndActiveTrue(assetId)
                        .map(s -> s.getStartedBy().getId().equals(user.getId()))
                        .orElse(false);

                if (isAssigned || isSessionOwner) {
                    log.debug("Authorized STAFF subscription to {} for user {}", destination, principal.getName());
                    return;
                }

                log.warn("Unauthorized STAFF subscription to asset {} by user {}", assetId, principal.getName());
                throw new AccessDeniedException("Access denied: you are not authorized to track asset " + assetId);
            } catch (NumberFormatException e) {
                throw new AccessDeniedException("Invalid asset ID destination: " + destination);
            }
        }

        // Global alerts topic: /topic/alerts
        if ("/topic/alerts".equals(destination)) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for privileged user {}", destination, principal.getName());
                return;
            }
            log.warn("Unauthorized subscription to global alerts by STAFF user {}", principal.getName());
            throw new AccessDeniedException("Staff members are not authorized to subscribe to the global alerts stream. Subscribe to your assigned asset alerts instead: /topic/alerts/{assetId}");
        }

        // Asset-specific alerts topic: /topic/alerts/{assetId}
        if (destination.startsWith("/topic/alerts/")) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for privileged user {}", destination, principal.getName());
                return;
            }

            String assetIdStr = destination.substring("/topic/alerts/".length()).trim();
            try {
                Long assetId = Long.parseLong(assetIdStr);
                User user = userRepository.findByEmail(principal.getName())
                        .orElseThrow(() -> new AccessDeniedException("User account not found: " + principal.getName()));

                boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(assetId, user.getId());
                boolean isSessionOwner = gpsSessionRepository.findByAssetIdAndActiveTrue(assetId)
                        .map(s -> s.getStartedBy().getId().equals(user.getId()))
                        .orElse(false);

                if (isAssigned || isSessionOwner) {
                    log.debug("Authorized STAFF subscription to {} for user {}", destination, principal.getName());
                    return;
                }

                log.warn("Unauthorized STAFF subscription to asset alerts {} by user {}", assetId, principal.getName());
                throw new AccessDeniedException("Access denied: you are not authorized to view alerts for asset " + assetId);
            } catch (NumberFormatException e) {
                throw new AccessDeniedException("Invalid asset ID destination: " + destination);
            }
        }

        // Global transfers topic: /topic/transfers
        if ("/topic/transfers".equals(destination)) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for privileged user {}", destination, principal.getName());
                return;
            }
            log.warn("Unauthorized subscription to global transfers by STAFF user {}", principal.getName());
            throw new AccessDeniedException("Staff members are not authorized to subscribe to the global transfers stream. Subscribe to your assigned asset transfers instead: /topic/transfers/{assetId}");
        }

        // Asset-specific transfers topic: /topic/transfers/{assetId}
        if (destination.startsWith("/topic/transfers/")) {
            if (isAdminOrSupervisor) {
                log.debug("Authorized subscription to {} for privileged user {}", destination, principal.getName());
                return;
            }

            String assetIdStr = destination.substring("/topic/transfers/".length()).trim();
            try {
                Long assetId = Long.parseLong(assetIdStr);
                User user = userRepository.findByEmail(principal.getName())
                        .orElseThrow(() -> new AccessDeniedException("User account not found: " + principal.getName()));

                boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(assetId, user.getId());
                boolean isSessionOwner = gpsSessionRepository.findByAssetIdAndActiveTrue(assetId)
                        .map(s -> s.getStartedBy().getId().equals(user.getId()))
                        .orElse(false);

                if (isAssigned || isSessionOwner) {
                    log.debug("Authorized STAFF subscription to {} for user {}", destination, principal.getName());
                    return;
                }

                log.warn("Unauthorized STAFF subscription to asset transfers {} by user {}", assetId, principal.getName());
                throw new AccessDeniedException("Access denied: you are not authorized to view transfers for asset " + assetId);
            } catch (NumberFormatException e) {
                throw new AccessDeniedException("Invalid asset ID destination: " + destination);
            }
        }
    }
}
