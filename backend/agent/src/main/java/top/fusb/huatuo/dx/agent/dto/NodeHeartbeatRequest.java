package top.fusb.huatuo.dx.agent.dto;

public record NodeHeartbeatRequest(
        String runtimeVersion,
        String matchedProcessSummary
) {
}
