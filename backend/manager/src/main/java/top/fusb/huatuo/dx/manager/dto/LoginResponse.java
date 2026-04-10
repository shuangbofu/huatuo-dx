package top.fusb.huatuo.dx.manager.dto;

public record LoginResponse(
        String token,
        UserProfile user
) {
}
