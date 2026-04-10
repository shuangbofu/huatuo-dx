package top.fusb.huatuo.dx.manager.controller;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRecordView;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRulePayload;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.manager.service.DiagnosticRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostic-rules")
public class DiagnosticRuleController {

    private final DiagnosticRuleService diagnosticRuleService;

    public DiagnosticRuleController(DiagnosticRuleService diagnosticRuleService) {
        this.diagnosticRuleService = diagnosticRuleService;
    }

    @GetMapping
    public List<DiagnosticRuleView> list(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return diagnosticRuleService.list(nodeId);
    }

    @GetMapping("/page")
    public PageResult<DiagnosticRuleView> listPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        return diagnosticRuleService.listPage(keyword, type, enabled, page, pageSize);
    }

    @PostMapping
    public DiagnosticRuleView create(@Valid @RequestBody DiagnosticRulePayload payload) {
        return diagnosticRuleService.save(null, payload);
    }

    @PutMapping("/{id}")
    public DiagnosticRuleView update(@PathVariable("id") Long id, @Valid @RequestBody DiagnosticRulePayload payload) {
        return diagnosticRuleService.save(id, payload);
    }

    @PutMapping("/{id}/enabled")
    public DiagnosticRuleView toggle(@PathVariable("id") Long id, @RequestParam("enabled") boolean enabled) {
        return diagnosticRuleService.toggle(id, enabled);
    }

    @PostMapping("/{id}/start")
    public DiagnosticExecutionRecordView start(
            @PathVariable("id") Long id,
            @RequestBody(required = false) DiagnosticExecutionRequest request
    ) {
        return diagnosticRuleService.start(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        diagnosticRuleService.delete(id);
    }
}
