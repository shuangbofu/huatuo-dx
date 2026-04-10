package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record LogConsoleCatalogResponse(
        List<LogConsoleNodeView> nodes
) {
}
