package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record DiagnosticTriggerEventView(
        int sequence,
        String title,
        String timestamp,
        String location,
        String threadName,
        Double costMs,
        String summary,
        String rawContent,
        String methodName,
        String watchBody,
        List<DiagnosticTraceNodeView> traceNodes
) {
}
