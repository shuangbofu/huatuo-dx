package top.fusb.huatuo.dx.manager.dto;

import java.util.List;

public record NodeSyncRequest(
        Long nodeId,
        String processPattern,
        Boolean logCollectEnabled,
        Integer logCollectIntervalSeconds,
        String arthasBootJar,
        java.util.List<LogSourceConfigView> logSourceConfigs,
        List<DiagnosticRuleView> enabledRules
) {
}
