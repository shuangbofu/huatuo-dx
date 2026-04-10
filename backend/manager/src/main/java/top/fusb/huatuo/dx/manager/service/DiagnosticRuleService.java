package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRecordView;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRulePayload;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionRecord;
import top.fusb.huatuo.dx.manager.entity.DiagnosticRule;
import top.fusb.huatuo.dx.manager.security.AuthenticatedUser;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.repo.DiagnosticRuleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticRuleService {

    private final DiagnosticRuleRepository diagnosticRuleRepository;
    private final NodeService nodeService;
    private final ManagerMapper mapper;
    private final AgentClient agentClient;
    private final DiagnosticRecordService diagnosticRecordService;
    private final AuthService authService;

    public DiagnosticRuleService(
            DiagnosticRuleRepository diagnosticRuleRepository,
            NodeService nodeService,
            ManagerMapper mapper,
            AgentClient agentClient,
            DiagnosticRecordService diagnosticRecordService,
            AuthService authService
    ) {
        this.diagnosticRuleRepository = diagnosticRuleRepository;
        this.nodeService = nodeService;
        this.mapper = mapper;
        this.agentClient = agentClient;
        this.diagnosticRecordService = diagnosticRecordService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<DiagnosticRuleView> list(Long nodeId) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        List<DiagnosticRule> rules = currentUser.isAdmin()
                ? diagnosticRuleRepository.findAllByOrderByUpdatedAtDesc()
                : diagnosticRuleRepository.findByOwnerUsernameOrderByUpdatedAtDesc(currentUser.username());
        return rules.stream()
                .map(rule -> mapper.toView(rule, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<DiagnosticRuleView> listPage(String keyword, String type, Boolean enabled, int page, int pageSize) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        var pageable = PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = diagnosticRuleRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!currentUser.isAdmin()) {
                predicates.add(cb.equal(root.get("ownerUsername"), currentUser.username()));
            }
            String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
            if (!normalizedKeyword.isEmpty()) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("targetClassPattern")), like),
                        cb.like(cb.lower(root.get("targetMethodPattern")), like)
                ));
            }
            if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(root.get("type"), top.fusb.huatuo.dx.manager.entity.DiagnosticType.valueOf(type)));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
        return new PageResult<>(
                result.getContent().stream().map(rule -> mapper.toView(rule, null)).toList(),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Transactional
    public DiagnosticRuleView save(Long id, DiagnosticRulePayload payload) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        DiagnosticRule rule = id == null ? new DiagnosticRule() : getEntity(id);
        rule.setAgentNodeId(null);
        if (id == null) {
            rule.setOwnerUsername(currentUser.username());
            rule.setOwnerDisplayName(currentUser.displayName());
        }
        rule.setName(payload.name());
        rule.setType(payload.type());
        rule.setTargetClassPattern(payload.targetClassPattern());
        rule.setTargetMethodPattern(payload.targetMethodPattern());
        rule.setSelectedProcessName(payload.selectedProcessName() == null ? "" : payload.selectedProcessName());
        rule.setTargetProcessPattern(payload.targetProcessPattern());
        rule.setOutputExpression(payload.outputExpression());
        rule.setConditionExpression(payload.conditionExpression());
        rule.setCommandOptions(payload.commandOptions());
        rule.setStackDepth(payload.stackDepth() == null ? 2 : payload.stackDepth());
        rule.setMaxMatches(payload.maxMatches() == null ? 5 : payload.maxMatches());
        rule.setExecutionTimeoutMs(payload.executionTimeoutMs() == null ? 30000L : payload.executionTimeoutMs());
        rule.setEnabled(payload.enabled());
        rule.setNotes(payload.notes());
        DiagnosticRule saved = diagnosticRuleRepository.save(rule);
        nodeService.syncAllNodes();
        return mapper.toView(saved, null);
    }

    @Transactional
    public void delete(Long id) {
        DiagnosticRule rule = getEntity(id);
        diagnosticRuleRepository.delete(rule);
        nodeService.syncAllNodes();
    }

    @Transactional
    public DiagnosticRuleView toggle(Long id, boolean enabled) {
        DiagnosticRule rule = getEntity(id);
        rule.setEnabled(enabled);
        DiagnosticRule saved = diagnosticRuleRepository.save(rule);
        nodeService.syncAllNodes();
        return mapper.toView(saved, null);
    }

    @Transactional
    public DiagnosticExecutionRecordView start(Long id, DiagnosticExecutionRequest request) {
        DiagnosticRule rule = getEntity(id);
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        if (request == null || request.nodeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "执行规则时必须选择目标节点");
        }
        AgentNode node = nodeService.getEntity(request.nodeId());
        String monitorKey = buildMonitorKey(rule, request);
        DiagnosticExecutionRecord existingOwned = diagnosticRecordService.findRunningByOwnerAndMonitorKey(currentUser.username(), monitorKey);
        if (existingOwned != null) {
            return diagnosticRecordService.get(existingOwned.getId());
        }
        DiagnosticExecutionRecord existingShared = diagnosticRecordService.findRunningByMonitorKey(monitorKey);
        if (existingShared != null) {
            return diagnosticRecordService.saveSubscribed(existingShared);
        }
        DiagnosticExecutionResult result;
        try {
            result = agentClient.startRule(node, id, request);
        } catch (BusinessException exception) {
            if (!"RULE_NOT_FOUND".equals(exception.getSubCode())) {
                throw exception;
            }
            nodeService.syncNode(node.getId());
            result = agentClient.startRule(node, id, request);
        }
        return diagnosticRecordService.saveStarted(
                node.getId(),
                node.getNodeName(),
                request.processName(),
                monitorKey,
                rule.getType(),
                result,
                rule
        );
    }

    private String buildMonitorKey(DiagnosticRule rule, DiagnosticExecutionRequest request) {
        Long nodeId = request == null ? null : request.nodeId();
        Long pid = request == null ? null : request.pid();
        String processName = request == null ? null : request.processName();
        String processPattern = request == null ? null : request.processPattern();
        Integer maxMatches = request != null && request.maxMatches() != null ? request.maxMatches() : rule.getMaxMatches();
        Integer stackDepth = rule.getStackDepth();
        Long timeoutMs = rule.getExecutionTimeoutMs();
        return String.join("|",
                "node=" + (nodeId == null ? "" : nodeId),
                "pid=" + (pid == null ? "" : pid),
                "processName=" + normalize(processName),
                "processPattern=" + normalize(processPattern),
                "type=" + rule.getType(),
                "class=" + normalize(rule.getTargetClassPattern()),
                "method=" + normalize(rule.getTargetMethodPattern()),
                "condition=" + normalize(rule.getConditionExpression()),
                "output=" + normalize(rule.getOutputExpression()),
                "options=" + normalize(rule.getCommandOptions()),
                "depth=" + (stackDepth == null ? "" : stackDepth),
                "matches=" + (maxMatches == null ? "" : maxMatches),
                "timeout=" + (timeoutMs == null ? "" : timeoutMs)
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Transactional(readOnly = true)
    public List<DiagnosticRuleView> findEnabledRulesByNode(AgentNode node) {
        return diagnosticRuleRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .map(rule -> mapper.toView(rule, node))
                .toList();
    }

    DiagnosticRule getEntity(Long id) {
        AuthenticatedUser currentUser = authService.requireCurrentUser();
        if (currentUser.isAdmin()) {
            return diagnosticRuleRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RULE_NOT_FOUND, "诊断规则不存在: " + id));
        }
        return diagnosticRuleRepository.findByIdAndOwnerUsername(id, currentUser.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.RULE_NOT_FOUND, "诊断规则不存在: " + id));
    }
}
