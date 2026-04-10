package top.fusb.huatuo.dx.manager.controller;

import top.fusb.huatuo.dx.manager.dto.AgentNodeView;
import top.fusb.huatuo.dx.manager.dto.ArthasStatusView;
import top.fusb.huatuo.dx.manager.dto.LogContentView;
import top.fusb.huatuo.dx.manager.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.manager.dto.LogFileView;
import top.fusb.huatuo.dx.manager.dto.NodeHeartbeatRequest;
import top.fusb.huatuo.dx.manager.dto.NodeRegistrationRequest;
import top.fusb.huatuo.dx.manager.dto.NodeUpdateRequest;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.ProcessView;
import top.fusb.huatuo.dx.manager.security.AdminOnly;
import top.fusb.huatuo.dx.manager.service.NodeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping("/agents/register")
    public AgentNodeView register(@Valid @RequestBody NodeRegistrationRequest request) {
        return nodeService.register(request);
    }

    @PostMapping("/agents/{nodeId}/heartbeat")
    public AgentNodeView heartbeat(@PathVariable("nodeId") Long nodeId, @RequestBody NodeHeartbeatRequest request) {
        return nodeService.heartbeat(nodeId, request);
    }

    @GetMapping("/agents")
    public List<AgentNodeView> listNodes() {
        return nodeService.listNodes();
    }

    @GetMapping("/agents/page")
    @AdminOnly
    public PageResult<AgentNodeView> listNodesPage(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        return nodeService.listNodesPage(keyword, status, page, pageSize);
    }

    @GetMapping("/agents/{nodeId}")
    public AgentNodeView getNode(@PathVariable("nodeId") Long nodeId) {
        return nodeService.getNode(nodeId);
    }

    @PutMapping("/agents/{nodeId}")
    @AdminOnly
    public AgentNodeView updateNode(@PathVariable("nodeId") Long nodeId, @Valid @RequestBody NodeUpdateRequest request) {
        return nodeService.update(nodeId, request);
    }

    @PostMapping("/agents/{nodeId}/sync")
    @AdminOnly
    public void syncNode(@PathVariable("nodeId") Long nodeId) {
        nodeService.syncNode(nodeId);
    }

    @PostMapping("/agents/{nodeId}/restart")
    @AdminOnly
    public AgentNodeView restartLocalCompanion(@PathVariable("nodeId") Long nodeId) {
        return nodeService.restartLocalCompanion(nodeId);
    }

    @GetMapping("/agents/{nodeId}/processes")
    public List<ProcessView> fetchProcesses(@PathVariable("nodeId") Long nodeId) {
        return nodeService.fetchProcesses(nodeId);
    }

    @GetMapping("/agents/{nodeId}/all-processes")
    @AdminOnly
    public List<ProcessView> fetchAllProcesses(@PathVariable("nodeId") Long nodeId) {
        return nodeService.fetchAllProcesses(nodeId);
    }

    @GetMapping("/agents/{nodeId}/arthas/status")
    @AdminOnly
    public ArthasStatusView arthasStatus(@PathVariable("nodeId") Long nodeId) {
        return nodeService.fetchArthasStatus(nodeId);
    }

    @PostMapping("/agents/{nodeId}/arthas/install")
    @AdminOnly
    public ArthasStatusView installArthas(@PathVariable("nodeId") Long nodeId) {
        return nodeService.installArthas(nodeId);
    }

    @GetMapping("/agents/{nodeId}/logs/index")
    public List<LogFileView> fetchLogs(@PathVariable("nodeId") Long nodeId,
                                       @RequestParam(value = "path", required = false) String path) {
        return nodeService.fetchLogs(nodeId, path);
    }

    @GetMapping("/agents/{nodeId}/logs/content")
    public LogContentView fetchLogContent(
            @PathVariable("nodeId") Long nodeId,
            @RequestParam("path") String path,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return nodeService.fetchLogContent(nodeId, path, keyword, limit);
    }

    @GetMapping("/agents/{nodeId}/logs/download")
    public ResponseEntity<ByteArrayResource> downloadLog(@PathVariable("nodeId") Long nodeId, @RequestParam("path") String path) {
        byte[] content = nodeService.downloadLog(nodeId, path);
        String fileName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @GetMapping("/agents/{nodeId}/logs/collection-status")
    @AdminOnly
    public LogCollectionStatusView fetchLogCollectionStatus(@PathVariable("nodeId") Long nodeId,
                                                            @RequestParam("directory") String directory) {
        return nodeService.fetchLogCollectionStatus(nodeId, directory);
    }
}
