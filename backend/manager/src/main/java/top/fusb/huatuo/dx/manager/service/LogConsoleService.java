package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.LogConsoleCatalogResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextRequest;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleNodeView;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryRequest;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.manager.dto.LogTailResponse;
import top.fusb.huatuo.dx.manager.dto.LogSourceConfigView;
import top.fusb.huatuo.dx.manager.dto.LogSourceView;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.entity.NodeStatus;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogConsoleService {

    private final NodeService nodeService;
    private final AgentClient agentClient;

    public LogConsoleService(NodeService nodeService, AgentClient agentClient) {
        this.nodeService = nodeService;
        this.agentClient = agentClient;
    }

    @Transactional(readOnly = true)
    public LogConsoleCatalogResponse getCatalog() {
        List<LogConsoleNodeView> nodes = nodeService.listNodeEntities().stream()
                .sorted(Comparator.comparing(AgentNode::getNodeName))
                .map(node -> new LogConsoleNodeView(
                        node.getId(),
                        node.getNodeName(),
                        node.getHost(),
                        node.getPort(),
                        node.getStatus() == NodeStatus.ONLINE ? "在线" : "离线",
                        nodeService.parseLogSourceConfigs(node).stream()
                                .sorted(Comparator.comparing(LogSourceConfigView::name))
                                .map(source -> new LogSourceView(source.path(), source.name(), source.description(), source.path()))
                                .toList()
                ))
                .toList();
        return new LogConsoleCatalogResponse(nodes);
    }

    @Transactional(readOnly = true)
    public LogConsoleQueryResponse query(LogConsoleQueryRequest request) {
        AgentNode node = nodeService.getEntity(request.nodeId());
        return agentClient.queryLogs(node, request.sourceId(), request.keyword(), request.page(), request.pageSize(), request.tailMode());
    }

    @Transactional(readOnly = true)
    public LogConsoleContextResponse context(LogConsoleContextRequest request) {
        AgentNode node = nodeService.getEntity(request.nodeId());
        return agentClient.queryLogContext(node, request.sourceId(), request.filePath(), request.lineNumber(), request.beforeLines(), request.afterLines());
    }

    @Transactional(readOnly = true)
    public LogTailResponse tail(
            Long nodeId,
            String sourceId,
            String keyword,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber,
            Integer limit
    ) {
        AgentNode node = nodeService.getEntity(nodeId);
        return agentClient.tailLogs(node, sourceId, keyword, afterCollectedAtEpochMs, afterFilePath, afterLineNumber, limit);
    }
}
