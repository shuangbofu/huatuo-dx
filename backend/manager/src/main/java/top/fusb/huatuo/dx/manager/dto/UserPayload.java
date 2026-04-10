package top.fusb.huatuo.dx.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.fusb.huatuo.dx.manager.entity.UserRole;

public record UserPayload(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 100, message = "用户名长度不能超过 100")
        String username,
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 100, message = "显示名称长度不能超过 100")
        String displayName,
        @NotNull(message = "角色不能为空")
        UserRole role,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
