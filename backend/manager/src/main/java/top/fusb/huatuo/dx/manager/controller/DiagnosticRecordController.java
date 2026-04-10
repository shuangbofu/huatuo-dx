package top.fusb.huatuo.dx.manager.controller;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRecordView;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import top.fusb.huatuo.dx.manager.service.DiagnosticMonitorService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostic-records")
public class DiagnosticRecordController {

    private final DiagnosticMonitorService diagnosticMonitorService;

    public DiagnosticRecordController(DiagnosticMonitorService diagnosticMonitorService) {
        this.diagnosticMonitorService = diagnosticMonitorService;
    }

    @GetMapping
    public List<DiagnosticExecutionRecordView> list(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return diagnosticMonitorService.list(nodeId);
    }

    @GetMapping("/page")
    public PageResult<DiagnosticExecutionRecordView> listPage(
            @RequestParam(value = "nodeId", required = false) Long nodeId,
            @RequestParam(value = "ruleId", required = false) Long ruleId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) DiagnosticType type,
            @RequestParam(value = "status", required = false) DiagnosticExecutionStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        return diagnosticMonitorService.listPage(nodeId, ruleId, keyword, type, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public DiagnosticExecutionRecordView get(@PathVariable("id") Long id) {
        return diagnosticMonitorService.get(id);
    }

    @PostMapping("/{id}/stop")
    public DiagnosticExecutionRecordView stop(@PathVariable("id") Long id) {
        return diagnosticMonitorService.stop(id);
    }
}
