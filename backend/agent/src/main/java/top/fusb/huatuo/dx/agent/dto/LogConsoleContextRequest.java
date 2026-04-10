package top.fusb.huatuo.dx.agent.dto;

public record LogConsoleContextRequest(
        String directory,
        String filePath,
        Integer lineNumber,
        Integer beforeLines,
        Integer afterLines
) {
}
