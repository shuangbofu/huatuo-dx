package top.fusb.huatuo.dx.manager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LogConsoleContextRequest(
        @NotNull Long nodeId,
        @NotBlank String sourceId,
        @NotBlank String filePath,
        @Min(1) Integer lineNumber,
        @Min(0) Integer beforeLines,
        @Min(0) Integer afterLines
) {
}
