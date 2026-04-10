package top.fusb.huatuo.dx.agent.dto;

public record DiagnosticExecutionRequest(
        Long pid,
        String processName,
        String processPattern,
        Integer maxMatches
) {
}
