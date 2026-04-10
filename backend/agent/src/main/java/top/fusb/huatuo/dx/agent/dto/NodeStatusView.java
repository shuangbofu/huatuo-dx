package top.fusb.huatuo.dx.agent.dto;

import java.util.List;

public record NodeStatusView(
        String nodeCode,
        String nodeName,
        String processPattern,
        String arthasBootJar,
        List<String> logDirectories,
        boolean logCollectEnabled,
        int logCollectIntervalSeconds,
        int syncedRuleCount
) {
}
