package top.fusb.huatuo.dx.manager.dto;

import top.fusb.huatuo.dx.manager.entity.NodeStatus;
import java.time.Instant;
import java.util.List;

public record AgentNodeView(
        Long id,
        String nodeCode,
        String nodeName,
        String host,
        Integer port,
        String baseUrl,
        String processPattern,
        List<String> visibleProcessNames,
        List<String> logDirectories,
        List<LogSourceConfigView> logSourceConfigs,
        List<String> companionBootstrapLogDirectories,
        boolean logCollectEnabled,
        Integer logCollectIntervalSeconds,
        List<String> tags,
        String arthasBootJar,
        boolean localCompanion,
        boolean companionRestartRecommended,
        NodeStatus status,
        String runtimeVersion,
        String matchedProcessSummary,
        Instant lastHeartbeatAt,
        Instant updatedAt
) {
}
