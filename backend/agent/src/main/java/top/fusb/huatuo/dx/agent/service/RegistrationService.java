package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.AgentNodeView;
import top.fusb.huatuo.dx.agent.dto.NodeHeartbeatRequest;
import top.fusb.huatuo.dx.agent.dto.NodeRegistrationRequest;
import top.fusb.huatuo.dx.agent.dto.Result;
import java.net.URI;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

@Service
public class RegistrationService {

    private final HuatuoAgentProperties properties;
    private final RestClient restClient;
    private final NodeRegistrationState registrationState;
    private final ProcessDiscoveryService processDiscoveryService;

    public RegistrationService(
            HuatuoAgentProperties properties,
            RestClient restClient,
            NodeRegistrationState registrationState,
            ProcessDiscoveryService processDiscoveryService
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.registrationState = registrationState;
        this.processDiscoveryService = processDiscoveryService;
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "#{${huatuo.dx.agent.heartbeat-interval-seconds:15} * 1000}")
    public void registerOrHeartbeat() {
        if (registrationState.getNodeId() == null) {
            register();
            return;
        }
        heartbeat();
    }

    public void register() {
        Result<AgentNodeView> response = restClient.post()
                .uri(properties.getManagerUrl() + "/api/agents/register")
                .body(new NodeRegistrationRequest(
                        properties.getNodeCode(),
                        properties.getNodeName(),
                        properties.getHost(),
                        extractPort(),
                        properties.getPublicBaseUrl(),
                        properties.getSecret(),
                        properties.getProcessPattern(),
                        properties.getLogDirectories(),
                        properties.getLogSourceConfigs(),
                        properties.isLogCollectEnabled(),
                        properties.getLogCollectIntervalSeconds(),
                        List.of(),
                        properties.getArthasBootJar(),
                        runtimeVersion(),
                        properties.isLocalCompanion()
                ))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Result<AgentNodeView>>() { });
        if (response == null || !response.isSuccess() || response.data() == null) {
            throw new RestClientException(response == null ? "Manager register response is empty" : response.message());
        }
        registrationState.setNodeId(response.data().id());
    }

    public void heartbeat() {
        Long nodeId = registrationState.getNodeId();
        if (nodeId == null) {
            register();
            return;
        }
        String matchedSummary = processDiscoveryService.findBestMatch(properties.getProcessPattern())
                .map(match -> match.pid() + " " + match.displayName())
                .orElse("No process matched");
        restClient.post()
                .uri(properties.getManagerUrl() + "/api/agents/" + nodeId + "/heartbeat")
                .body(new NodeHeartbeatRequest(runtimeVersion(), matchedSummary))
                .retrieve()
                .toBodilessEntity();
    }

    private Integer extractPort() {
        URI uri = URI.create(properties.getPublicBaseUrl());
        return uri.getPort() > 0 ? uri.getPort() : 8090;
    }

    private String runtimeVersion() {
        return System.getProperty("java.runtime.version");
    }
}
