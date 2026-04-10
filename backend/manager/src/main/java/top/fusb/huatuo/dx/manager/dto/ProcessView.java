package top.fusb.huatuo.dx.manager.dto;

public record ProcessView(
        long pid,
        String displayName,
        String command,
        String commandLine,
        String user
) {
}
