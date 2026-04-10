package top.fusb.huatuo.dx.manager.dto;

public record LogConsoleContextLine(
        int lineNumber,
        String content,
        boolean hit
) {
}
