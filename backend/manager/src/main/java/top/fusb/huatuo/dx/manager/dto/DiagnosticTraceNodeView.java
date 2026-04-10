package top.fusb.huatuo.dx.manager.dto;

public record DiagnosticTraceNodeView(
        int depth,
        Double costMs,
        String label
) {
}
