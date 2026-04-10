package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import java.time.Instant;
import java.util.List;

public record DiagnosticExecutionRecordView(
        Long id,
        String sessionId,
        Long ruleId,
        Long agentNodeId,
        String agentNodeName,
        String ruleName,
        String processDisplayName,
        String ownerUsername,
        String ownerDisplayName,
        DiagnosticType type,
        DiagnosticExecutionStatus status,
        boolean success,
        String command,
        String output,
        String errorMessage,
        Long pid,
        long durationMs,
        Instant executedAt,
        Instant updatedAt,
        boolean sharedSession,
        long subscriberCount,
        int triggerCount,
        Double maxCostMs,
        List<DiagnosticTriggerEventView> triggerEvents
) {
}
