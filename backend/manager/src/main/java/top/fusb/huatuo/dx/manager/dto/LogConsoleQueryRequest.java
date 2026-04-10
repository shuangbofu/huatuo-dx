package top.fusb.huatuo.dx.manager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LogConsoleQueryRequest(
        @NotNull Long nodeId,
        @NotBlank String sourceId,
        @NotBlank String keyword,
        @Min(1) Integer page,
        @Min(1) Integer pageSize,
        Boolean tailMode
) {
}
