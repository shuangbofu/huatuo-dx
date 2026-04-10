package top.fusb.huatuo.dx.manager.dto;

public record LogConsoleQueryItem(
        String filePath,
        int lineNumber,
        String content,
        long collectedAtEpochMs
) {
}
