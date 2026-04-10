package top.fusb.huatuo.dx.agent.dto;

public record LogConsoleQueryItem(
        String filePath,
        int lineNumber,
        String content,
        long collectedAtEpochMs
) {
}
