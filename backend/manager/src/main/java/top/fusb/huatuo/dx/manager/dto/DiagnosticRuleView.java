package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import java.time.Instant;

public record DiagnosticRuleView(
        Long id,
        Long agentNodeId,
        String agentNodeName,
        String name,
        String ownerUsername,
        String ownerDisplayName,
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
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
