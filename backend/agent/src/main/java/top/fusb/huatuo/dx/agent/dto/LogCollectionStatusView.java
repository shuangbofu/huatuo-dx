package top.fusb.huatuo.dx.agent.dto;

public record LogCollectionStatusView(
        String directory,
        long indexedFileCount,
        long indexedLineCount,
        Long latestCollectedAtEpochMs
) {
}
