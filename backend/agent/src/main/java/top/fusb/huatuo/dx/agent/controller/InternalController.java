package top.fusb.huatuo.dx.agent.controller;

import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.agent.dto.ArthasStatusView;
import top.fusb.huatuo.dx.agent.dto.LogContentView;
import top.fusb.huatuo.dx.agent.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.agent.dto.LogConsoleContextRequest;
import top.fusb.huatuo.dx.agent.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.agent.dto.LogConsoleQueryRequest;
import top.fusb.huatuo.dx.agent.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.agent.dto.LogFileView;
import top.fusb.huatuo.dx.agent.dto.LogTailRequest;
import top.fusb.huatuo.dx.agent.dto.LogTailResponse;
import top.fusb.huatuo.dx.agent.dto.NodeStatusView;
import top.fusb.huatuo.dx.agent.dto.NodeSyncRequest;
import top.fusb.huatuo.dx.agent.dto.ProcessView;
import top.fusb.huatuo.dx.agent.service.DiagnosticService;
import top.fusb.huatuo.dx.agent.service.ArthasInstallerService;
import top.fusb.huatuo.dx.agent.service.InternalAuthService;
import top.fusb.huatuo.dx.agent.service.LogService;
import top.fusb.huatuo.dx.agent.service.ProcessDiscoveryService;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final InternalAuthService authService;
    private final ProcessDiscoveryService processDiscoveryService;
    private final LogService logService;
    private final DiagnosticService diagnosticService;
    private final ArthasInstallerService arthasInstallerService;

    public InternalController(
            InternalAuthService authService,
            ProcessDiscoveryService processDiscoveryService,
            LogService logService,
            DiagnosticService diagnosticService,
            ArthasInstallerService arthasInstallerService
    ) {
        this.authService = authService;
        this.processDiscoveryService = processDiscoveryService;
        this.logService = logService;
        this.diagnosticService = diagnosticService;
        this.arthasInstallerService = arthasInstallerService;
    }

    @GetMapping("/status")
    public NodeStatusView status(@RequestHeader("X-Huatuo-Node-Secret") String secret) {
        authService.verify(secret);
        return diagnosticService.status();
    }

    @GetMapping("/arthas/status")
    public ArthasStatusView arthasStatus(@RequestHeader("X-Huatuo-Node-Secret") String secret) {
        authService.verify(secret);
        return arthasInstallerService.status();
    }

    @PostMapping("/arthas/install")
    public ArthasStatusView installArthas(@RequestHeader("X-Huatuo-Node-Secret") String secret) {
        authService.verify(secret);
        return arthasInstallerService.install();
    }

    @GetMapping("/processes")
    public List<ProcessView> processes(@RequestHeader("X-Huatuo-Node-Secret") String secret) {
        authService.verify(secret);
        return processDiscoveryService.findProcesses("");
    }

    @GetMapping("/logs/index")
    public List<LogFileView> logs(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                  @RequestParam(required = false) String path) {
        authService.verify(secret);
        return logService.list(path);
    }

    @GetMapping("/logs/content")
    public LogContentView logContent(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                     @RequestParam String path,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer limit) {
        authService.verify(secret);
        return logService.read(path, keyword, limit);
    }

    @GetMapping("/logs/collection-status")
    public LogCollectionStatusView collectionStatus(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                                    @RequestParam("directory") String directory) {
        authService.verify(secret);
        return logService.collectionStatus(directory);
    }

    @PostMapping("/logs/query")
    public LogConsoleQueryResponse queryLogs(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                             @RequestBody LogConsoleQueryRequest request) {
        authService.verify(secret);
        return logService.query(request.directory(), request.keyword(), request.page(), request.pageSize(), request.tailMode());
    }

    @PostMapping("/logs/context")
    public LogConsoleContextResponse queryContext(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                                  @RequestBody LogConsoleContextRequest request) {
        authService.verify(secret);
        return logService.context(request.directory(), request.filePath(), request.lineNumber(), request.beforeLines(), request.afterLines());
    }

    @PostMapping("/logs/tail")
    public LogTailResponse tailLogs(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                    @RequestBody LogTailRequest request) {
        authService.verify(secret);
        return logService.tail(
                request.directory(),
                request.keyword(),
                request.afterCollectedAtEpochMs(),
                request.afterFilePath(),
                request.afterLineNumber(),
                request.limit()
        );
    }

    @GetMapping("/logs/download")
    public ResponseEntity<ByteArrayResource> download(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                                      @RequestParam String path) {
        authService.verify(secret);
        byte[] content = logService.download(path);
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @PostMapping("/diagnostics/sync")
    public void sync(@RequestHeader("X-Huatuo-Node-Secret") String secret, @RequestBody NodeSyncRequest request) {
        authService.verify(secret);
        diagnosticService.sync(request);
    }

    @PostMapping("/diagnostics/execute/{ruleId}")
    public DiagnosticExecutionResult execute(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                             @PathVariable Long ruleId,
                                             @RequestBody(required = false) DiagnosticExecutionRequest request) {
        authService.verify(secret);
        return diagnosticService.execute(ruleId, request);
    }

    @PostMapping("/diagnostics/start/{ruleId}")
    public DiagnosticExecutionResult start(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                           @PathVariable Long ruleId,
                                           @RequestBody(required = false) DiagnosticExecutionRequest request) {
        authService.verify(secret);
        return diagnosticService.start(ruleId, request);
    }

    @GetMapping("/diagnostics/sessions/{sessionId}")
    public DiagnosticExecutionResult session(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                             @PathVariable String sessionId) {
        authService.verify(secret);
        return diagnosticService.session(sessionId);
    }

    @PostMapping("/diagnostics/sessions/{sessionId}/stop")
    public DiagnosticExecutionResult stop(@RequestHeader("X-Huatuo-Node-Secret") String secret,
                                          @PathVariable String sessionId) {
        authService.verify(secret);
        return diagnosticService.stop(sessionId);
    }
}
