package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record LogConsoleContextResponse(
        String filePath,
        int hitLineNumber,
        int startLineNumber,
        int endLineNumber,
        List<LogConsoleContextLine> lines
) {
}
