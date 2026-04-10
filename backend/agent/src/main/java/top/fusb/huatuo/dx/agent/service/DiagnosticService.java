package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.agent.dto.LogSourceConfigView;
import top.fusb.huatuo.dx.agent.dto.NodeStatusView;
import top.fusb.huatuo.dx.agent.dto.NodeSyncRequest;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticService {

    private final DiagnosticRuleStore ruleStore;
    private final ArthasCommandService arthasCommandService;
    private final HuatuoAgentProperties properties;

    public DiagnosticService(
            DiagnosticRuleStore ruleStore,
            ArthasCommandService arthasCommandService,
            HuatuoAgentProperties properties
    ) {
        this.ruleStore = ruleStore;
        this.arthasCommandService = arthasCommandService;
        this.properties = properties;
    }

    public void sync(NodeSyncRequest request) {
        ruleStore.replace(request.enabledRules() == null ? List.of() : request.enabledRules());
        if (request.processPattern() != null && !request.processPattern().isBlank()) {
            properties.setProcessPattern(request.processPattern());
        }
        if (request.logSourceConfigs() != null) {
            properties.setLogSourceConfigs(request.logSourceConfigs());
            properties.setLogDirectories(request.logSourceConfigs().stream()
                    .map(LogSourceConfigView::path)
                    .filter(path -> path != null && !path.isBlank())
                    .toList());
        }
        if (request.logCollectEnabled() != null) {
            properties.setLogCollectEnabled(request.logCollectEnabled());
        }
        if (request.logCollectIntervalSeconds() != null && request.logCollectIntervalSeconds() > 0) {
            properties.setLogCollectIntervalSeconds(request.logCollectIntervalSeconds());
        }
        if (request.arthasBootJar() != null && !request.arthasBootJar().isBlank()) {
            properties.setArthasBootJar(request.arthasBootJar());
        }
    }

    public DiagnosticExecutionResult execute(Long ruleId, DiagnosticExecutionRequest request) {
        return arthasCommandService.execute(ruleStore.get(ruleId), request);
    }

    public DiagnosticExecutionResult start(Long ruleId, DiagnosticExecutionRequest request) {
        return arthasCommandService.start(ruleStore.get(ruleId), request);
    }

    public DiagnosticExecutionResult session(String sessionId) {
        return arthasCommandService.session(sessionId);
    }

    public DiagnosticExecutionResult stop(String sessionId) {
        return arthasCommandService.stop(sessionId);
    }

    public NodeStatusView status() {
        return new NodeStatusView(
                properties.getNodeCode(),
                properties.getNodeName(),
                properties.getProcessPattern(),
                properties.getArthasBootJar() == null || properties.getArthasBootJar().isBlank()
                        ? Path.of(properties.getArthasInstallDir(), "arthas-boot-" + properties.getArthasVersion() + ".jar").toString()
                        : properties.getArthasBootJar(),
                properties.getLogDirectories(),
                properties.isLogCollectEnabled(),
                properties.getLogCollectIntervalSeconds(),
                ruleStore.list().size()
        );
    }
}
