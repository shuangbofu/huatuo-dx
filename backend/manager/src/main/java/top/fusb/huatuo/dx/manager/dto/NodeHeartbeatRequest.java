package top.fusb.huatuo.dx.manager.dto;

public record NodeHeartbeatRequest(
        String runtimeVersion,
        String matchedProcessSummary
) {
}
