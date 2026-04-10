package top.fusb.huatuo.dx.manager.dto;

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
