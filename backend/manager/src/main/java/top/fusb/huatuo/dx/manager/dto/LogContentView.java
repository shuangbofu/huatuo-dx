package top.fusb.huatuo.dx.manager.dto;

public record LogContentView(
        String path,
        String content,
        int lineCount,
        boolean truncated
) {
}
