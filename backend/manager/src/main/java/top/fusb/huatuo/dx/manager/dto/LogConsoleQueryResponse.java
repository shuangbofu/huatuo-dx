package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record LogConsoleQueryResponse(
        long total,
        int page,
        int pageSize,
        int scannedFiles,
        List<LogConsoleQueryItem> items
) {
}
