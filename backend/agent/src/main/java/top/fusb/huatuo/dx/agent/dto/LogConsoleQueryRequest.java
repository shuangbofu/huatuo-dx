package top.fusb.huatuo.dx.agent.dto;

public record LogConsoleQueryRequest(
        String directory,
        String keyword,
        Integer page,
        Integer pageSize,
        Boolean tailMode
) {
}
