package top.fusb.huatuo.dx.agent.dto;

public record LogTailRequest(
        String directory,
        String keyword,
        Long afterCollectedAtEpochMs,
        String afterFilePath,
        Integer afterLineNumber,
        Integer limit
) {
}
