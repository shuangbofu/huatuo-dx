package top.fusb.huatuo.dx.manager.dto;

public record DiagnosticExecutionRequest(
        Long nodeId,
        Long pid,
        String processName,
        String processPattern,
        Integer maxMatches
) {
}
