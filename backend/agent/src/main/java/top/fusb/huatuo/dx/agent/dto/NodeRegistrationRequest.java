package top.fusb.huatuo.dx.agent.dto;

import java.util.List;

public record NodeRegistrationRequest(
        String nodeCode,
        String nodeName,
        String host,
        Integer port,
        String baseUrl,
        String secret,
        String processPattern,
        List<String> logDirectories,
        List<LogSourceConfigView> logSourceConfigs,
        Boolean logCollectEnabled,
        Integer logCollectIntervalSeconds,
        List<String> tags,
        String arthasBootJar,
        String runtimeVersion,
        Boolean localCompanion
) {
}
