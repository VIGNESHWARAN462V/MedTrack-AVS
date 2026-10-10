package com.medtrack.avs.mapper;

import com.medtrack.avs.dto.UserResponse;
import com.medtrack.avs.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        String roleName = user.getRole() != null ? user.getRole().getName().name() : "STAFF";

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .roles(Collections.singletonList(roleName))
                .authProvider(user.getAuthProvider())
                .profileImage(user.getProfileImage())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
