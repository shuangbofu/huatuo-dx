package top.fusb.huatuo.dx.agent.dto;

public record DiagnosticRuleView(
        Long id,
        Long agentNodeId,
        String agentNodeName,
        String name,
        DiagnosticType type,
        String targetClassPattern,
        String targetMethodPattern,
        String selectedProcessName,
        String targetProcessPattern,
        String outputExpression,
        String conditionExpression,
        String commandOptions,
        Integer stackDepth,
        Integer maxMatches,
        Long executionTimeoutMs,
        boolean enabled,
        String notes
) {
}
