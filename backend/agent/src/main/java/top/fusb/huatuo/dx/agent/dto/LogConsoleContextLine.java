package top.fusb.huatuo.dx.agent.dto;

public record LogConsoleContextLine(
        int lineNumber,
        String content,
        boolean hit
) {
}
