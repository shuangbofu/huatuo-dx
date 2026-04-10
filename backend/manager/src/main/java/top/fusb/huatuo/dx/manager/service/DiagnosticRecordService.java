package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRecordView;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.DiagnosticTriggerEventView;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionRecord;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import top.fusb.huatuo.dx.manager.entity.DiagnosticRule;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.fusb.huatuo.dx.manager.security.AuthenticatedUser;
import top.fusb.huatuo.dx.manager.repo.DiagnosticExecutionRecordRepository;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticRecordService {

    private final DiagnosticExecutionRecordRepository repository;
    private final DiagnosticOutputParser diagnosticOutputParser;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    public DiagnosticRecordService(
            DiagnosticExecutionRecordRepository repository,
            DiagnosticOutputParser diagnosticOutputParser,
            ObjectMapper objectMapper,
            AuthService authService
    ) {
        this.repository = repository;
        this.diagnosticOutputParser = diagnosticOutputParser;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    @Transactional
    public DiagnosticExecutionRecordView saveStarted(
            Long nodeId,
            String nodeName,
            String processDisplayName,
            String monitorKey,
            DiagnosticType type,
            DiagnosticExecutionResult result,
            DiagnosticRule rule
    ) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        DiagnosticExecutionRecord record = new DiagnosticExecutionRecord();
        record.setOwnerUsername(currentUser.username());
        record.setOwnerDisplayName(currentUser.displayName());
        applyResult(record, nodeId, nodeName, processDisplayName, monitorKey, type, result);
        return toView(repository.save(record));
    }

    @Transactional
    public DiagnosticExecutionRecordView saveSubscribed(DiagnosticExecutionRecord sourceRecord) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        DiagnosticExecutionRecord record = new DiagnosticExecutionRecord();
        record.setOwnerUsername(currentUser.username());
        record.setOwnerDisplayName(currentUser.displayName());
        record.setSessionId(sourceRecord.getSessionId());
        record.setRuleId(sourceRecord.getRuleId());
        record.setAgentNodeId(sourceRecord.getAgentNodeId());
        record.setAgentNodeName(sourceRecord.getAgentNodeName());
        record.setRuleName(sourceRecord.getRuleName());
        record.setProcessDisplayName(sourceRecord.getProcessDisplayName());
        record.setMonitorKey(sourceRecord.getMonitorKey());
        record.setType(sourceRecord.getType());
        record.setStatus(sourceRecord.getStatus());
        record.setSuccess(sourceRecord.isSuccess());
        record.setCommand(sourceRecord.getCommand());
        record.setOutput(sourceRecord.getOutput());
        record.setErrorMessage(sourceRecord.getErrorMessage());
        record.setTriggerCount(sourceRecord.getTriggerCount());
        record.setMaxCostMs(sourceRecord.getMaxCostMs());
        record.setTriggerEventsJson(sourceRecord.getTriggerEventsJson());
        record.setPid(sourceRecord.getPid());
        record.setDurationMs(sourceRecord.getDurationMs());
        record.setExecutedAt(sourceRecord.getExecutedAt());
        record.setUpdatedAt(sourceRecord.getUpdatedAt());
        return toView(repository.save(record));
    }

    @Transactional
    public DiagnosticExecutionRecordView updateFromSession(Long recordId, DiagnosticExecutionResult result) {
        DiagnosticExecutionRecord record = getEntity(recordId);
        applyResult(record, record.getAgentNodeId(), record.getAgentNodeName(), record.getProcessDisplayName(), record.getMonitorKey(), record.getType(), result);
        return toView(repository.save(record));
    }

    @Transactional
    public void updateAllFromSession(String sessionId, DiagnosticExecutionResult result) {
        for (DiagnosticExecutionRecord record : repository.findBySessionIdOrderByExecutedAtDesc(sessionId)) {
            applyResult(record, record.getAgentNodeId(), record.getAgentNodeName(), record.getProcessDisplayName(), record.getMonitorKey(), record.getType(), result);
            repository.save(record);
        }
    }

    @Transactional(readOnly = true)
    public DiagnosticExecutionRecord findRunningByMonitorKey(String monitorKey) {
        return repository.findTopByMonitorKeyAndStatusOrderByExecutedAtDesc(monitorKey, DiagnosticExecutionStatus.RUNNING).orElse(null);
    }

    @Transactional(readOnly = true)
    public DiagnosticExecutionRecord findRunningByOwnerAndMonitorKey(String ownerUsername, String monitorKey) {
        return repository.findTopByOwnerUsernameAndMonitorKeyAndStatusOrderByExecutedAtDesc(ownerUsername, monitorKey, DiagnosticExecutionStatus.RUNNING).orElse(null);
    }

    @Transactional
    public DiagnosticExecutionRecordView stopSubscription(Long id) {
        DiagnosticExecutionRecord record = getEntity(id);
        record.setStatus(DiagnosticExecutionStatus.STOPPED);
        record.setSuccess(true);
        record.setUpdatedAt(java.time.Instant.now());
        record.setDurationMs(java.time.Duration.between(record.getExecutedAt(), record.getUpdatedAt()).toMillis());
        return toView(repository.save(record));
    }

    @Transactional(readOnly = true)
    public long countRunningSubscribers(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return repository.countBySessionIdAndStatus(sessionId, DiagnosticExecutionStatus.RUNNING);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticExecutionRecordView> list(Long nodeId) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        List<DiagnosticExecutionRecord> records;
        if (currentUser.isAdmin()) {
            records = nodeId == null
                    ? repository.findTop100ByOrderByExecutedAtDesc()
                    : repository.findTop50ByAgentNodeIdOrderByExecutedAtDesc(nodeId);
        } else {
            records = nodeId == null
                    ? repository.findTop100ByOwnerUsernameOrderByExecutedAtDesc(currentUser.username())
                    : repository.findTop50ByOwnerUsernameAndAgentNodeIdOrderByExecutedAtDesc(currentUser.username(), nodeId);
        }
        return records.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<DiagnosticExecutionRecordView> listPage(
            Long nodeId,
            Long ruleId,
            String keyword,
            DiagnosticType type,
            DiagnosticExecutionStatus status,
            int page,
            int pageSize
    ) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        var pageable = PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "executedAt"));
        var result = repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!currentUser.isAdmin()) {
                predicates.add(cb.equal(root.get("ownerUsername"), currentUser.username()));
            }
            if (nodeId != null) {
                predicates.add(cb.equal(root.get("agentNodeId"), nodeId));
            }
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
            if (!normalizedKeyword.isEmpty()) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ruleName")), like),
                        cb.like(cb.lower(root.get("command")), like)
                ));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Transactional(readOnly = true)
    public List<DiagnosticExecutionRecord> listRunningRecords() {
        return repository.findByStatus(DiagnosticExecutionStatus.RUNNING);
    }

    @Transactional(readOnly = true)
    public DiagnosticExecutionRecordView get(Long id) {
        return toView(getEntity(id));
    }

    @Transactional(readOnly = true)
    public DiagnosticExecutionRecord getEntity(Long id) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        if (currentUser.isAdmin()) {
            return repository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "监控记录不存在: " + id));
        }
        return repository.findByIdAndOwnerUsername(id, currentUser.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "监控记录不存在: " + id));
    }

    @Transactional
    public DiagnosticExecutionRecordView saveRecord(DiagnosticExecutionRecord record) {
        return toView(repository.save(record));
    }

    private void applyResult(
            DiagnosticExecutionRecord record,
            Long nodeId,
            String nodeName,
            String processDisplayName,
            String monitorKey,
            DiagnosticType type,
            DiagnosticExecutionResult result
    ) {
        record.setSessionId(result.sessionId());
        record.setRuleId(result.ruleId());
        record.setAgentNodeId(nodeId);
        record.setAgentNodeName(nodeName);
        record.setRuleName(result.ruleName());
        record.setProcessDisplayName(processDisplayName);
        record.setMonitorKey(monitorKey);
        record.setType(type == null ? result.type() : type);
        record.setStatus(result.status());
        record.setSuccess(result.success());
        record.setCommand(result.command());
        record.setOutput(result.output());
        record.setErrorMessage(result.errorMessage());
        DiagnosticOutputParser.ParsedDiagnosticOutput parsedOutput = diagnosticOutputParser.parse(record.getType(), result.output());
        record.setTriggerCount(parsedOutput.triggerCount());
        record.setMaxCostMs(parsedOutput.maxCostMs());
        record.setTriggerEventsJson(writeTriggerEvents(parsedOutput.triggerEvents()));
        record.setPid(result.pid());
        record.setDurationMs(result.durationMs());
        record.setExecutedAt(result.executedAt());
        record.setUpdatedAt(result.updatedAt());
    }

    private DiagnosticExecutionRecordView toView(DiagnosticExecutionRecord record) {
        List<DiagnosticTriggerEventView> triggerEvents = readTriggerEvents(record.getTriggerEventsJson());
        if (triggerEvents.isEmpty() && record.getOutput() != null && !record.getOutput().isBlank()) {
            triggerEvents = diagnosticOutputParser.parse(record.getType(), record.getOutput()).triggerEvents();
        }
        int triggerCount = record.getTriggerCount() != null ? record.getTriggerCount() : triggerEvents.size();
        Double maxCostMs = record.getMaxCostMs();
        if (maxCostMs == null) {
            maxCostMs = triggerEvents.stream()
                    .map(DiagnosticTriggerEventView::costMs)
                    .filter(value -> value != null)
                    .max(Double::compareTo)
                    .orElse(null);
        }
        long subscriberCount = record.getSessionId() == null || record.getSessionId().isBlank()
                ? 1
                : repository.countBySessionId(record.getSessionId());
        return new DiagnosticExecutionRecordView(
                record.getId(),
                record.getSessionId(),
                record.getRuleId(),
                record.getAgentNodeId(),
                record.getAgentNodeName(),
                record.getRuleName(),
                record.getProcessDisplayName(),
                record.getOwnerUsername(),
                record.getOwnerDisplayName(),
                record.getType(),
                record.getStatus(),
                record.isSuccess(),
                record.getCommand(),
                record.getOutput(),
                record.getErrorMessage(),
                record.getPid(),
                record.getDurationMs(),
                record.getExecutedAt(),
                record.getUpdatedAt(),
                subscriberCount > 1,
                subscriberCount,
                triggerCount,
                maxCostMs,
                triggerEvents
        );
    }

    private String writeTriggerEvents(List<DiagnosticTriggerEventView> triggerEvents) {
        try {
            return objectMapper.writeValueAsString(triggerEvents);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_FAILED, "保存监控触发结果失败");
        }
    }

    private List<DiagnosticTriggerEventView> readTriggerEvents(String triggerEventsJson) {
        if (triggerEventsJson == null || triggerEventsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(triggerEventsJson, new TypeReference<List<DiagnosticTriggerEventView>>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
