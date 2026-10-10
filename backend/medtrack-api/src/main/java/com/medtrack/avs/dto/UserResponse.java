package com.medtrack.avs.dto;

import com.medtrack.avs.entity.AuthProvider;
import com.medtrack.avs.entity.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private RoleType role;
    private List<String> roles;
    private AuthProvider authProvider;
    private String profileImage;
    private boolean enabled;
    private LocalDateTime createdAt;

    public List<String> getRoles() {
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        if (role != null) {
            return Collections.singletonList(role.name());
        }
        return Collections.singletonList("STAFF");
    }
}
