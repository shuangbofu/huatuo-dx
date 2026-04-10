package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.manager.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.manager.dto.ArthasStatusView;
import top.fusb.huatuo.dx.manager.dto.LogContentView;
import top.fusb.huatuo.dx.manager.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.manager.dto.LogFileView;
import top.fusb.huatuo.dx.manager.dto.LogTailResponse;
import top.fusb.huatuo.dx.manager.dto.NodeSyncRequest;
import top.fusb.huatuo.dx.manager.dto.ProcessView;
import top.fusb.huatuo.dx.manager.dto.Result;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AgentClient {

    private static final ParameterizedTypeReference<Result<List<ProcessView>>> PROCESS_LIST_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<List<LogFileView>>> LOG_FILE_LIST_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<LogContentView>> LOG_CONTENT_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<LogCollectionStatusView>> LOG_COLLECTION_STATUS_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<LogConsoleQueryResponse>> LOG_QUERY_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<LogConsoleContextResponse>> LOG_CONTEXT_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<LogTailResponse>> LOG_TAIL_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<DiagnosticExecutionResult>> EXECUTION_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<ArthasStatusView>> ARTHAS_STATUS_RESULT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Result<Void>> VOID_RESULT =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public AgentClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ProcessView> fetchProcesses(AgentNode node) {
        return unwrap(restClient.get()
                .uri(node.getBaseUrl() + "/internal/processes")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(PROCESS_LIST_RESULT));
    }

    public List<LogFileView> fetchLogs(AgentNode node, String path) {
        return unwrap(restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(node.getBaseUrl() + "/internal/logs/index")
                        .queryParamIfPresent("path", Optional.ofNullable(path))
                        .build(true)
                        .toUri())
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(LOG_FILE_LIST_RESULT));
    }

    public LogContentView fetchLogContent(AgentNode node, String path, String keyword, Integer limit) {
        return unwrap(restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(node.getBaseUrl() + "/internal/logs/content")
                        .queryParam("path", path)
                        .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build(true)
                        .toUri())
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(LOG_CONTENT_RESULT));
    }

    public byte[] downloadLog(AgentNode node, String path) {
        return restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(node.getBaseUrl() + "/internal/logs/download")
                        .queryParam("path", path)
                        .build(true)
                        .toUri())
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(byte[].class);
    }

    public LogCollectionStatusView fetchLogCollectionStatus(AgentNode node, String directory) {
        return unwrap(restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(node.getBaseUrl() + "/internal/logs/collection-status")
                        .queryParam("directory", directory)
                        .build(true)
                        .toUri())
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(LOG_COLLECTION_STATUS_RESULT));
    }

    public LogConsoleQueryResponse queryLogs(
            AgentNode node,
            String sourceId,
            String keyword,
            Integer page,
            Integer pageSize,
            Boolean tailMode
    ) {
        var response = unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/logs/query")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .body(new java.util.LinkedHashMap<String, Object>() {{
                    put("directory", sourceId);
                    put("keyword", keyword);
                    put("page", page == null ? 1 : page);
                    put("pageSize", pageSize == null ? 50 : pageSize);
                    put("tailMode", Boolean.TRUE.equals(tailMode));
                }})
                .retrieve()
                .body(LOG_QUERY_RESULT));
        return response == null ? new LogConsoleQueryResponse(0, 1, 50, 0, List.of()) : response;
    }

    public LogConsoleContextResponse queryLogContext(
            AgentNode node,
            String sourceId,
            String filePath,
            Integer lineNumber,
            Integer beforeLines,
            Integer afterLines
    ) {
        var response = unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/logs/context")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .body(new java.util.LinkedHashMap<String, Object>() {{
                    put("directory", sourceId);
                    put("filePath", filePath);
                    put("lineNumber", lineNumber);
                    put("beforeLines", beforeLines == null ? 10 : beforeLines);
                    put("afterLines", afterLines == null ? 10 : afterLines);
                }})
                .retrieve()
                .body(LOG_CONTEXT_RESULT));
        return response == null
                ? new LogConsoleContextResponse(filePath, lineNumber == null ? 1 : lineNumber, 1, 1, List.of())
                : response;
    }

    public LogTailResponse tailLogs(
            AgentNode node,
            String sourceId,
            String keyword,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber,
            Integer limit
    ) {
        var response = unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/logs/tail")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .body(new java.util.LinkedHashMap<String, Object>() {{
                    put("directory", sourceId);
                    put("keyword", keyword);
                    put("afterCollectedAtEpochMs", afterCollectedAtEpochMs);
                    put("afterFilePath", afterFilePath);
                    put("afterLineNumber", afterLineNumber);
                    put("limit", limit == null ? 200 : limit);
                }})
                .retrieve()
                .body(LOG_TAIL_RESULT));
        return response == null ? new LogTailResponse(List.of(), afterCollectedAtEpochMs, afterFilePath, afterLineNumber) : response;
    }

    public DiagnosticExecutionResult executeRule(AgentNode node, Long ruleId, DiagnosticExecutionRequest request) {
        return unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/diagnostics/execute/" + ruleId)
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .body(request == null ? new DiagnosticExecutionRequest(null, null, null, null, null) : request)
                .retrieve()
                .body(EXECUTION_RESULT));
    }

    public DiagnosticExecutionResult startRule(AgentNode node, Long ruleId, DiagnosticExecutionRequest request) {
        return unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/diagnostics/start/" + ruleId)
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .body(request == null ? new DiagnosticExecutionRequest(null, null, null, null, null) : request)
                .retrieve()
                .body(EXECUTION_RESULT));
    }

    public DiagnosticExecutionResult fetchSession(AgentNode node, String sessionId) {
        return unwrap(restClient.get()
                .uri(node.getBaseUrl() + "/internal/diagnostics/sessions/" + sessionId)
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(EXECUTION_RESULT));
    }

    public DiagnosticExecutionResult stopSession(AgentNode node, String sessionId) {
        return unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/diagnostics/sessions/" + sessionId + "/stop")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .retrieve()
                .body(EXECUTION_RESULT));
    }

    public ArthasStatusView fetchArthasStatus(AgentNode node) {
        try {
            return unwrap(restClient.get()
                    .uri(node.getBaseUrl() + "/internal/arthas/status")
                    .header("X-Huatuo-Node-Secret", node.getSecret())
                    .retrieve()
                    .body(ARTHAS_STATUS_RESULT));
        } catch (RestClientResponseException exception) {
            throw translateArthasCapabilityException(exception);
        } catch (ResourceAccessException exception) {
            throw translateNodeUnavailableException(node, exception);
        }
    }

    public ArthasStatusView installArthas(AgentNode node) {
        try {
            return unwrap(restClient.post()
                    .uri(node.getBaseUrl() + "/internal/arthas/install")
                    .header("X-Huatuo-Node-Secret", node.getSecret())
                    .retrieve()
                    .body(ARTHAS_STATUS_RESULT));
        } catch (RestClientResponseException exception) {
            throw translateArthasCapabilityException(exception);
        } catch (ResourceAccessException exception) {
            throw translateNodeUnavailableException(node, exception);
        }
    }

    public void syncRules(AgentNode node, NodeSyncRequest request) {
        unwrap(restClient.post()
                .uri(node.getBaseUrl() + "/internal/diagnostics/sync")
                .header("X-Huatuo-Node-Secret", node.getSecret())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .body(VOID_RESULT));
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_FAILED, "远端服务响应为空");
        }
        if (!"0".equals(result.code())) {
            String message = result.message() == null ? "" : result.message();
            if (message.contains("No static resource") && message.contains("internal/arthas/status")) {
                throw new BusinessException(
                        ErrorCode.AGENT_CAPABILITY_UNAVAILABLE,
                        "当前节点 Agent 没有提供 Arthas 状态接口，请重启伴生 Agent 或重新部署节点 Agent"
                );
            }
            if (message.contains("No static resource") && message.contains("internal/arthas/install")) {
                throw new BusinessException(
                        ErrorCode.AGENT_CAPABILITY_UNAVAILABLE,
                        "当前节点 Agent 没有提供 Arthas 安装接口，请重启伴生 Agent 或重新部署节点 Agent"
                );
            }
            throw new BusinessException(result.code(), result.subCode(), result.message());
        }
        return result.data();
    }

    private BusinessException translateArthasCapabilityException(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 404) {
            return new BusinessException(
                    ErrorCode.AGENT_CAPABILITY_UNAVAILABLE,
                    "当前节点 Agent 版本过旧，未提供 Arthas 管理接口，请重启伴生 Agent 或重新部署节点 Agent"
            );
        }
        if (exception.getStatusCode().value() == 403) {
            return new BusinessException(ErrorCode.REMOTE_CALL_FAILED, "远端服务拒绝访问，请检查节点密钥或节点配置");
        }
        return new BusinessException(ErrorCode.REMOTE_CALL_FAILED, "远端服务调用失败: HTTP " + exception.getStatusCode().value());
    }

    private BusinessException translateNodeUnavailableException(AgentNode node, ResourceAccessException exception) {
        String message = node.isLocalCompanion()
                ? "伴生 Agent 当前未启动或端口不可达，请先在节点管理中重启伴生 Agent"
                : "节点当前不可达，请检查 Agent 进程、访问地址或网络连通性";
        if (exception.getMessage() != null && exception.getMessage().contains("Connection refused")) {
            return new BusinessException(ErrorCode.REMOTE_CALL_FAILED, message);
        }
        return new BusinessException(ErrorCode.REMOTE_CALL_FAILED, message);
    }
}
