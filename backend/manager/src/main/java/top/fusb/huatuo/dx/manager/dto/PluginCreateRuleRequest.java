package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.DiagnosticType;

public record PluginCreateRuleRequest(
        DiagnosticType type,
        String ruleName,
        String className,
        String methodName,
        String selectedProcessName,
        String outputExpression,
        String conditionExpression,
        String commandOptions,
        Integer stackDepth,
        Integer maxMatches,
        Long executionTimeoutMs,
        Boolean enabled,
        String notes
) {
    public DiagnosticRulePayload toRulePayload() {
        String finalRuleName = ruleName == null || ruleName.isBlank()
                ? className + "#" + methodName + " " + type.name().toLowerCase()
                : ruleName.trim();
        return new DiagnosticRulePayload(
                null,
                finalRuleName,
                type,
                className == null ? "" : className.trim(),
                methodName == null ? "" : methodName.trim(),
                selectedProcessName == null ? "" : selectedProcessName.trim(),
                null,
                outputExpression,
                conditionExpression,
                commandOptions,
                stackDepth,
                maxMatches,
                executionTimeoutMs,
                enabled == null || enabled,
                notes
        );
    }
}
