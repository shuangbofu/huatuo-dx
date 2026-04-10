package top.fusb.huatuo.dx.agent.dto;

import java.time.Instant;

public record DiagnosticExecutionResult(
        String sessionId,
        Long ruleId,
        String ruleName,
        DiagnosticType type,
        DiagnosticSessionStatus status,
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
