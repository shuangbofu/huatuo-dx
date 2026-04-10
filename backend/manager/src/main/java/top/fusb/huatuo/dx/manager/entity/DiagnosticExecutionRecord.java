package top.fusb.huatuo.dx.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_execution_records")
public class DiagnosticExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ruleId;

    @Column(nullable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 100)
    private String ownerDisplayName;

    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false)
    private Long agentNodeId;

    @Column(length = 100)
    private String agentNodeName;

    @Column(nullable = false)
    private String ruleName;

    @Column(length = 255)
    private String processDisplayName;

    @Column(length = 1000)
    private String monitorKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagnosticType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagnosticExecutionStatus status;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false, length = 1000)
    private String command;

    @Lob
    private String output;

    @Lob
    private String errorMessage;

    private Integer triggerCount;

    private Double maxCostMs;

    @Lob
    private String triggerEventsJson;

    private Long pid;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = executedAt;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    public Long getAgentNodeId() {
        return agentNodeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setAgentNodeId(Long agentNodeId) {
        this.agentNodeId = agentNodeId;
    }

    public String getAgentNodeName() {
        return agentNodeName;
    }

    public void setAgentNodeName(String agentNodeName) {
        this.agentNodeName = agentNodeName;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getProcessDisplayName() {
        return processDisplayName;
    }

    public void setProcessDisplayName(String processDisplayName) {
        this.processDisplayName = processDisplayName;
    }

    public String getMonitorKey() {
        return monitorKey;
    }

    public void setMonitorKey(String monitorKey) {
        this.monitorKey = monitorKey;
    }

    public DiagnosticType getType() {
        return type;
    }

    public void setType(DiagnosticType type) {
        this.type = type;
    }

    public DiagnosticExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(DiagnosticExecutionStatus status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getTriggerCount() {
        return triggerCount;
    }

    public void setTriggerCount(Integer triggerCount) {
        this.triggerCount = triggerCount;
    }

    public Double getMaxCostMs() {
        return maxCostMs;
    }

    public void setMaxCostMs(Double maxCostMs) {
        this.maxCostMs = maxCostMs;
    }

    public String getTriggerEventsJson() {
        return triggerEventsJson;
    }

    public void setTriggerEventsJson(String triggerEventsJson) {
        this.triggerEventsJson = triggerEventsJson;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
