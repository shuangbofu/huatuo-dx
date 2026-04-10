package top.fusb.huatuo.dx.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NodeRegistrationRequest(
        @NotBlank String nodeCode,
        @NotBlank String nodeName,
        @NotBlank String host,
        @NotNull Integer port,
        @NotBlank String baseUrl,
        @NotBlank String secret,
        @NotBlank String processPattern,
        List<String> visibleProcessNames,
        List<String> logDirectories,
        List<LogSourceConfigView> logSourceConfigs,
        List<String> companionBootstrapLogDirectories,
        Boolean logCollectEnabled,
        Integer logCollectIntervalSeconds,
        List<String> tags,
        String arthasBootJar,
        String runtimeVersion,
        Boolean localCompanion
) {
}
