package top.fusb.huatuo.dx.agent.dto;

public record LogSourceConfigView(
        String name,
        String description,
        String path,
        String mode,
        String parseMode,
        String parsePattern,
        String logbackConfigPath,
        Integer collectIntervalSeconds
) {
}
