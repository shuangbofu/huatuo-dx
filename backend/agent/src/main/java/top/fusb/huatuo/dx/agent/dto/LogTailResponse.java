package top.fusb.huatuo.dx.agent.dto;

import java.util.List;

public record LogTailResponse(
        List<LogConsoleQueryItem> items,
        Long latestCollectedAtEpochMs,
        String latestFilePath,
        Integer latestLineNumber
) {
}
