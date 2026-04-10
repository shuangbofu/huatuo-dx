package top.fusb.huatuo.dx.agent.dto;

public record ProcessView(
        long pid,
        String displayName,
        String command,
        String commandLine,
        String user
) {
}
