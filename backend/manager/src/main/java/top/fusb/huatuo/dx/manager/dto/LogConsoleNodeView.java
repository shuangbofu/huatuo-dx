package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record LogConsoleNodeView(
        Long id,
        String name,
        String host,
        Integer port,
        String heartbeatStatus,
        List<LogSourceView> sources
) {
}
