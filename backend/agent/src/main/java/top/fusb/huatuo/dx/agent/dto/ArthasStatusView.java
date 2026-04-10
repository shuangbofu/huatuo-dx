package top.fusb.huatuo.dx.agent.dto;

public record ArthasStatusView(
        boolean installed,
        String status,
        String jarPath,
        String message
) {
}
