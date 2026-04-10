package top.fusb.huatuo.dx.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRulePayload;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.manager.dto.PluginCreateRuleRequest;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.service.AuthService;
import top.fusb.huatuo.dx.manager.service.DiagnosticRuleService;
import top.fusb.huatuo.dx.manager.service.PluginSignatureService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plugin/diagnostic-rules")
public class PluginDiagnosticRuleController {

    private final PluginSignatureService pluginSignatureService;
    private final DiagnosticRuleService diagnosticRuleService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public PluginDiagnosticRuleController(
            PluginSignatureService pluginSignatureService,
            DiagnosticRuleService diagnosticRuleService,
            AuthService authService,
            ObjectMapper objectMapper
    ) {
        this.pluginSignatureService = pluginSignatureService;
        this.diagnosticRuleService = diagnosticRuleService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/import")
    public DiagnosticRuleView importRule(@RequestBody String rawBody, HttpServletRequest request) throws Exception {
        authService.requireCurrentUser();
        pluginSignatureService.verify(request, rawBody);
        PluginCreateRuleRequest pluginRequest = objectMapper.readValue(rawBody, PluginCreateRuleRequest.class);
        validate(pluginRequest);
        DiagnosticRulePayload payload = pluginRequest.toRulePayload();
        return diagnosticRuleService.save(null, payload);
    }

    private void validate(PluginCreateRuleRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "插件请求不能为空");
        }
        if (request.type() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "诊断类型不能为空");
        }
        if (request.className() == null || request.className().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "类名不能为空");
        }
        if (request.methodName() == null || request.methodName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "方法名不能为空");
        }
    }
}
