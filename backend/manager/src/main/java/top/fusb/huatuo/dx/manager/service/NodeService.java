package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.config.ManagerProperties;
import top.fusb.huatuo.dx.manager.dto.AgentNodeView;
import top.fusb.huatuo.dx.manager.dto.ArthasStatusView;
import top.fusb.huatuo.dx.manager.dto.LogContentView;
import top.fusb.huatuo.dx.manager.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.manager.dto.LogFileView;
import top.fusb.huatuo.dx.manager.dto.NodeHeartbeatRequest;
import top.fusb.huatuo.dx.manager.dto.NodeRegistrationRequest;
import top.fusb.huatuo.dx.manager.dto.NodeSyncRequest;
import top.fusb.huatuo.dx.manager.dto.NodeUpdateRequest;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.ProcessView;
import top.fusb.huatuo.dx.manager.dto.LogSourceConfigView;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.entity.DiagnosticRule;
import top.fusb.huatuo.dx.manager.entity.NodeStatus;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.repo.AgentNodeRepository;
import top.fusb.huatuo.dx.manager.repo.DiagnosticRuleRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NodeService {

    private final AgentNodeRepository agentNodeRepository;
    private final DiagnosticRuleRepository diagnosticRuleRepository;
    private final ManagerMapper mapper;
    private final ManagerProperties properties;
    private final AgentClient agentClient;
    private final LocalAgentAutoStarter localAgentAutoStarter;

    public NodeService(
            AgentNodeRepository agentNodeRepository,
            DiagnosticRuleRepository diagnosticRuleRepository,
            ManagerMapper mapper,
            ManagerProperties properties,
            AgentClient agentClient,
            LocalAgentAutoStarter localAgentAutoStarter
    ) {
        this.agentNodeRepository = agentNodeRepository;
        this.diagnosticRuleRepository = diagnosticRuleRepository;
        this.mapper = mapper;
        this.properties = properties;
        this.agentClient = agentClient;
        this.localAgentAutoStarter = localAgentAutoStarter;
    }

    @Transactional
    public AgentNodeView register(NodeRegistrationRequest request) {
        AgentNode node = agentNodeRepository.findByNodeCode(request.nodeCode())
                .orElseGet(AgentNode::new);
        boolean isNewNode = node.getId() == null;
        node.setNodeCode(request.nodeCode());
        node.setNodeName(request.nodeName());
        node.setHost(request.host());
        node.setPort(request.port());
        node.setBaseUrl(request.baseUrl());
        if (request.secret() != null && !request.secret().isBlank()) {
            node.setSecret(request.secret());
        }
        node.setProcessPattern(request.processPattern());
        if (isNewNode || request.visibleProcessNames() != null) {
            node.setVisibleProcessNames(mapper.join(request.visibleProcessNames()));
        }
        if (isNewNode || isBlank(node.getLogDirectories())) {
            node.setLogDirectories(mapper.join(request.logDirectories()));
        }
        if (isNewNode || isBlank(node.getLogSourceConfigs())) {
            node.setLogSourceConfigs(mapper.stringifyLogSources(request.logSourceConfigs()));
        }
        if (isNewNode || request.companionBootstrapLogDirectories() != null) {
            node.setCompanionBootstrapLogDirectories(mapper.join(request.companionBootstrapLogDirectories()));
        }
        if (isNewNode || request.logCollectEnabled() != null) {
            node.setLogCollectEnabled(!Boolean.FALSE.equals(request.logCollectEnabled()));
        }
        if (isNewNode || request.logCollectIntervalSeconds() != null) {
            node.setLogCollectIntervalSeconds(request.logCollectIntervalSeconds() == null ? 5 : request.logCollectIntervalSeconds());
        }
        if (isNewNode || request.tags() != null) {
            node.setTags(mapper.join(request.tags()));
        }
        if (isNewNode || (request.arthasBootJar() != null && !request.arthasBootJar().isBlank())) {
            node.setArthasBootJar(request.arthasBootJar());
        }
        node.setLocalCompanion(Boolean.TRUE.equals(request.localCompanion()));
        node.setRuntimeVersion(request.runtimeVersion());
        node.setLastHeartbeatAt(Instant.now());
        node.setStatus(NodeStatus.ONLINE);
        AgentNode saved = agentNodeRepository.save(node);
        return mapper.toView(saved, localAgentAutoStarter.isRestartRecommended(saved));
    }

    @Transactional
    public AgentNodeView heartbeat(Long nodeId, NodeHeartbeatRequest request) {
        AgentNode node = getEntity(nodeId);
        node.setRuntimeVersion(request.runtimeVersion());
        node.setMatchedProcessSummary(request.matchedProcessSummary());
        node.setLastHeartbeatAt(Instant.now());
        node.setStatus(NodeStatus.ONLINE);
        AgentNode saved = agentNodeRepository.save(node);
        return mapper.toView(saved, localAgentAutoStarter.isRestartRecommended(saved));
    }

    @Transactional
    public AgentNodeView update(Long nodeId, NodeUpdateRequest request) {
        AgentNode node = getEntity(nodeId);
        node.setNodeName(request.nodeName());
        node.setHost(request.host());
        node.setPort(request.port());
        node.setBaseUrl(request.baseUrl());
        if (request.secret() != null && !request.secret().isBlank()) {
            node.setSecret(request.secret());
        }
        node.setProcessPattern(request.processPattern());
        node.setVisibleProcessNames(mapper.join(request.visibleProcessNames()));
        node.setLogDirectories(mapper.join(request.logDirectories()));
        node.setLogSourceConfigs(mapper.stringifyLogSources(request.logSourceConfigs()));
        node.setCompanionBootstrapLogDirectories(mapper.join(request.companionBootstrapLogDirectories()));
        node.setLogCollectEnabled(Boolean.TRUE.equals(request.logCollectEnabled()));
        node.setLogCollectIntervalSeconds(request.logCollectIntervalSeconds());
        node.setTags(mapper.join(request.tags()));
        node.setArthasBootJar(request.arthasBootJar());
        AgentNode saved = agentNodeRepository.save(node);
        if (saved.getStatus() == NodeStatus.ONLINE) {
            try {
                syncNode(saved.getId());
            } catch (Exception ignored) {
                // Saving node metadata should not be blocked by a temporary sync/auth problem.
            }
        }
        return mapper.toView(saved, localAgentAutoStarter.isRestartRecommended(saved));
    }

    @Transactional(readOnly = true)
    public List<AgentNodeView> listNodes() {
        return agentNodeRepository.findAll().stream()
                .sorted(Comparator.comparing(AgentNode::getUpdatedAt).reversed())
                .map(node -> mapper.toView(node, localAgentAutoStarter.isRestartRecommended(node)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<AgentNodeView> listNodesPage(String keyword, String status, int page, int pageSize) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = agentNodeRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
            if (!normalizedKeyword.isEmpty()) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nodeName")), like),
                        cb.like(cb.lower(root.get("nodeCode")), like),
                        cb.like(cb.lower(root.get("host")), like)
                ));
            }
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), NodeStatus.valueOf(status)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
        return new PageResult<>(
                result.getContent().stream()
                        .map(node -> mapper.toView(node, localAgentAutoStarter.isRestartRecommended(node)))
                        .toList(),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Transactional(readOnly = true)
    public List<AgentNode> listNodeEntities() {
        return agentNodeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AgentNodeView getNode(Long nodeId) {
        AgentNode node = getEntity(nodeId);
        return mapper.toView(node, localAgentAutoStarter.isRestartRecommended(node));
    }

    @Transactional
    public AgentNodeView restartLocalCompanion(Long nodeId) {
        AgentNode node = getEntity(nodeId);
        if (!node.isLocalCompanion()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该节点不是伴生 Agent，不能执行重启");
        }
        localAgentAutoStarter.restartLocalAgent(node.getBaseUrl());
        AgentNode refreshed = getEntity(nodeId);
        return mapper.toView(refreshed, localAgentAutoStarter.isRestartRecommended(refreshed));
    }

    @Transactional(readOnly = true)
    public List<ProcessView> fetchProcesses(Long nodeId) {
        AgentNode node = getEntity(nodeId);
        List<ProcessView> processes = agentClient.fetchProcesses(node);
        List<String> visibleNames = splitCommaValues(node.getVisibleProcessNames());
        if (visibleNames.isEmpty()) {
            return List.of();
        }
        return processes.stream()
                .filter(process -> visibleNames.contains(process.displayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcessView> fetchAllProcesses(Long nodeId) {
        AgentNode node = getEntity(nodeId);
        return agentClient.fetchProcesses(node);
    }

    @Transactional(readOnly = true)
    public ArthasStatusView fetchArthasStatus(Long nodeId) {
        return agentClient.fetchArthasStatus(getEntity(nodeId));
    }

    @Transactional
    public ArthasStatusView installArthas(Long nodeId) {
        return agentClient.installArthas(getEntity(nodeId));
    }

    @Transactional(readOnly = true)
    public List<LogFileView> fetchLogs(Long nodeId, String path) {
        return agentClient.fetchLogs(getEntity(nodeId), path);
    }

    @Transactional(readOnly = true)
    public LogContentView fetchLogContent(Long nodeId, String path, String keyword, Integer limit) {
        return agentClient.fetchLogContent(getEntity(nodeId), path, keyword, limit);
    }

    @Transactional(readOnly = true)
    public byte[] downloadLog(Long nodeId, String path) {
        return agentClient.downloadLog(getEntity(nodeId), path);
    }

    @Transactional(readOnly = true)
    public LogCollectionStatusView fetchLogCollectionStatus(Long nodeId, String directory) {
        AgentNode node = getEntity(nodeId);
        return agentClient.fetchLogCollectionStatus(node, directory);
    }

    @Transactional
    public void syncNode(Long nodeId) {
        AgentNode node = getEntity(nodeId);
        NodeSyncRequest request = new NodeSyncRequest(
                node.getId(),
                node.getProcessPattern(),
                node.isLogCollectEnabled(),
                node.getLogCollectIntervalSeconds(),
                node.getArthasBootJar(),
                parseLogSourceConfigs(node),
                diagnosticRuleRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                        .map(rule -> mapper.toView(rule, node))
                        .toList()
        );
        agentClient.syncRules(node, request);
    }

    @Transactional
    public void syncAllNodes() {
        for (AgentNode node : agentNodeRepository.findAll()) {
            if (node.getStatus() == NodeStatus.ONLINE) {
                try {
                    syncNode(node.getId());
                } catch (Exception ignored) {
                    // best effort sync
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "#{${huatuo.dx.manager.sync-interval-seconds:30} * 1000}")
    public void refreshNodeStatusAndSync() {
        Instant offlineBefore = Instant.now().minusSeconds(properties.getHeartbeatTimeoutSeconds());
        for (AgentNode node : agentNodeRepository.findAll()) {
            boolean online = node.getLastHeartbeatAt() != null && node.getLastHeartbeatAt().isAfter(offlineBefore);
            node.setStatus(online ? NodeStatus.ONLINE : NodeStatus.OFFLINE);
            agentNodeRepository.save(node);
            if (online) {
                try {
                    syncNode(node.getId());
                } catch (Exception ignored) {
                    node.setStatus(NodeStatus.OFFLINE);
                    agentNodeRepository.save(node);
                }
            }
        }
    }

    AgentNode getEntity(Long nodeId) {
        return agentNodeRepository.findById(nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NODE_NOT_FOUND, "节点不存在: " + nodeId));
    }

    List<String> splitCommaValues(String raw) {
        return raw == null || raw.isBlank()
                ? List.of()
                : java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    List<LogSourceConfigView> parseLogSourceConfigs(AgentNode node) {
        return mapper.parseLogSourceConfigs(node);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
