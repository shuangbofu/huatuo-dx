package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import java.time.Instant;

public record DiagnosticExecutionResult(
        String sessionId,
        Long ruleId,
        String ruleName,
        DiagnosticType type,
        DiagnosticExecutionStatus status,
        boolean success,
        String command,
        String output,
        String errorMessage,
        Instant executedAt,
        Instant updatedAt,
        long durationMs,
        Long pid
) {
}
