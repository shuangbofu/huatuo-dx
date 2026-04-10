package top.fusb.huatuo.dx.manager.dto;

public record ArthasStatusView(
        boolean installed,
        String status,
        String jarPath,
        String message
) {
}
