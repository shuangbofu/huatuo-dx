package top.fusb.huatuo.dx.manager.security;

import top.fusb.huatuo.dx.manager.entity.UserRole;

public record AuthenticatedUser(
        Long id,
        String username,
        String displayName,
        UserRole role
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
