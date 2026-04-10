package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiagnosticRulePayload(
        Long agentNodeId,
        @NotBlank String name,
        @NotNull DiagnosticType type,
        @NotBlank String targetClassPattern,
        @NotBlank String targetMethodPattern,
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
