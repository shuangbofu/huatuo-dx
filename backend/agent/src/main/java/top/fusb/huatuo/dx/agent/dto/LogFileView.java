package top.fusb.huatuo.dx.agent.dto;

public record LogFileView(
        String path,
        String name,
        long size,
        boolean directory,
        String modifiedAt
) {
}
