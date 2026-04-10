package top.fusb.huatuo.dx.manager.dto;

public record LogCollectionStatusView(
        String directory,
        long indexedFileCount,
        long indexedLineCount,
        Long latestCollectedAtEpochMs
) {
}
