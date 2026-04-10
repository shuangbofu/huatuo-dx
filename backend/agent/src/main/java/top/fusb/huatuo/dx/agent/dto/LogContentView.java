package top.fusb.huatuo.dx.agent.dto;

public record LogContentView(
        String path,
        String content,
        int lineCount,
        boolean truncated
) {
}
