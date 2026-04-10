package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRecordView;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionRecord;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticMonitorService {

    private final DiagnosticRecordService diagnosticRecordService;
    private final NodeService nodeService;
    private final AgentClient agentClient;

    public DiagnosticMonitorService(
            DiagnosticRecordService diagnosticRecordService,
            NodeService nodeService,
            AgentClient agentClient
    ) {
        this.diagnosticRecordService = diagnosticRecordService;
        this.nodeService = nodeService;
        this.agentClient = agentClient;
    }

    @Transactional
    public List<DiagnosticExecutionRecordView> list(Long nodeId) {
        refreshRunningRecords();
        return diagnosticRecordService.list(nodeId);
    }

    @Transactional
    public PageResult<DiagnosticExecutionRecordView> listPage(
            Long nodeId,
            Long ruleId,
            String keyword,
            DiagnosticType type,
            DiagnosticExecutionStatus status,
            int page,
            int pageSize
    ) {
        refreshRunningRecords();
        return diagnosticRecordService.listPage(nodeId, ruleId, keyword, type, status, page, pageSize);
    }

    @Transactional
    public DiagnosticExecutionRecordView get(Long id) {
        DiagnosticExecutionRecord record = diagnosticRecordService.getEntity(id);
        if (record.getStatus() == DiagnosticExecutionStatus.RUNNING && record.getSessionId() != null && !record.getSessionId().isBlank()) {
            AgentNode node = nodeService.getEntity(record.getAgentNodeId());
            DiagnosticExecutionResult result = agentClient.fetchSession(node, record.getSessionId());
            diagnosticRecordService.updateAllFromSession(record.getSessionId(), result);
            return diagnosticRecordService.get(id);
        }
        return diagnosticRecordService.get(id);
    }

    @Transactional
    public DiagnosticExecutionRecordView stop(Long id) {
        DiagnosticExecutionRecord record = diagnosticRecordService.getEntity(id);
        if (record.getStatus() != DiagnosticExecutionStatus.RUNNING || record.getSessionId() == null || record.getSessionId().isBlank()) {
            return diagnosticRecordService.get(id);
        }
        if (diagnosticRecordService.countRunningSubscribers(record.getSessionId()) > 1) {
            return diagnosticRecordService.stopSubscription(id);
        }
        AgentNode node = nodeService.getEntity(record.getAgentNodeId());
        DiagnosticExecutionResult result = agentClient.stopSession(node, record.getSessionId());
        diagnosticRecordService.updateAllFromSession(record.getSessionId(), result);
        return diagnosticRecordService.get(id);
    }

    private void refreshRunningRecords() {
        Map<String, DiagnosticExecutionRecord> sessionRecords = new LinkedHashMap<>();
        for (DiagnosticExecutionRecord record : diagnosticRecordService.listRunningRecords()) {
            if (record.getSessionId() == null || record.getSessionId().isBlank()) {
                continue;
            }
            sessionRecords.putIfAbsent(record.getSessionId(), record);
        }
        for (DiagnosticExecutionRecord record : sessionRecords.values()) {
            try {
                AgentNode node = nodeService.getEntity(record.getAgentNodeId());
                DiagnosticExecutionResult result = agentClient.fetchSession(node, record.getSessionId());
                diagnosticRecordService.updateAllFromSession(record.getSessionId(), result);
            } catch (Exception ignored) {
                // keep current state; the UI can continue to show the last known output
            }
        }
    }
}
