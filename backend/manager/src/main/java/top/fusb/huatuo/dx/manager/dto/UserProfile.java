package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.UserRole;

public record UserProfile(
        Long id,
        String username,
        String displayName,
        UserRole role,
        boolean enabled,
        String updatedAt
) {
}
