package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.AgentNodeView;
import top.fusb.huatuo.dx.manager.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.manager.dto.LogSourceConfigView;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.entity.DiagnosticRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ManagerMapper {

    private static final TypeReference<List<LogSourceConfigView>> LOG_SOURCE_LIST_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public ManagerMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AgentNodeView toView(AgentNode node, boolean companionRestartRecommended) {
        return new AgentNodeView(
                node.getId(),
                node.getNodeCode(),
                node.getNodeName(),
                node.getHost(),
                node.getPort(),
                node.getBaseUrl(),
                node.getProcessPattern(),
                split(node.getVisibleProcessNames()),
                split(node.getLogDirectories()),
                parseLogSources(node.getLogSourceConfigs(), node.getLogDirectories()),
                split(node.getCompanionBootstrapLogDirectories()),
                node.isLogCollectEnabled(),
                node.getLogCollectIntervalSeconds(),
                split(node.getTags()),
                node.getArthasBootJar(),
                node.isLocalCompanion(),
                companionRestartRecommended,
                node.getStatus(),
                node.getRuntimeVersion(),
                node.getMatchedProcessSummary(),
                node.getLastHeartbeatAt(),
                node.getUpdatedAt()
        );
    }

    DiagnosticRuleView toView(DiagnosticRule rule, AgentNode node) {
        return new DiagnosticRuleView(
                rule.getId(),
                rule.getAgentNodeId(),
                node == null ? null : node.getNodeName(),
                rule.getName(),
                rule.getOwnerUsername(),
                rule.getOwnerDisplayName(),
                rule.getType(),
                rule.getTargetClassPattern(),
                rule.getTargetMethodPattern(),
                rule.getSelectedProcessName(),
                rule.getTargetProcessPattern(),
                rule.getOutputExpression(),
                rule.getConditionExpression(),
                rule.getCommandOptions(),
                rule.getStackDepth(),
                rule.getMaxMatches(),
                rule.getExecutionTimeoutMs(),
                rule.isEnabled(),
                rule.getNotes(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    String stringifyLogSources(List<LogSourceConfigView> values) {
        try {
            return values == null || values.isEmpty() ? "" : objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("日志配置序列化失败");
        }
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private List<LogSourceConfigView> parseLogSources(String raw, String logDirectories) {
        if (raw != null && !raw.isBlank()) {
            try {
                return objectMapper.readValue(raw, LOG_SOURCE_LIST_TYPE);
            } catch (Exception ignored) {
            }
        }
        return split(logDirectories).stream()
                .map(path -> new LogSourceConfigView(path, null, path, "QUERY", "RAW", null, null, 5))
                .toList();
    }

    List<LogSourceConfigView> parseLogSourceConfigs(AgentNode node) {
        return parseLogSources(node.getLogSourceConfigs(), node.getLogDirectories());
    }
}
