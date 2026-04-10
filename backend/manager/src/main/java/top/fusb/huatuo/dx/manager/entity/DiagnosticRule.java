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
@Table(name = "diagnostic_rules")
public class DiagnosticRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long agentNodeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 100)
    private String ownerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagnosticType type;

    @Column(nullable = false)
    private String targetClassPattern;

    @Column(nullable = false)
    private String targetMethodPattern;

    @Column(nullable = false)
    private String selectedProcessName;

    private String targetProcessPattern;

    @Lob
    private String outputExpression;

    @Lob
    private String conditionExpression;

    @Lob
    private String commandOptions;

    private Integer stackDepth;

    private Integer maxMatches;

    private Long executionTimeoutMs;

    private boolean enabled;

    @Lob
    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAgentNodeId() {
        return agentNodeId;
    }

    public void setAgentNodeId(Long agentNodeId) {
        this.agentNodeId = agentNodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public DiagnosticType getType() {
        return type;
    }

    public void setType(DiagnosticType type) {
        this.type = type;
    }

    public String getTargetClassPattern() {
        return targetClassPattern;
    }

    public void setTargetClassPattern(String targetClassPattern) {
        this.targetClassPattern = targetClassPattern;
    }

    public String getTargetMethodPattern() {
        return targetMethodPattern;
    }

    public void setTargetMethodPattern(String targetMethodPattern) {
        this.targetMethodPattern = targetMethodPattern;
    }

    public String getTargetProcessPattern() {
        return targetProcessPattern;
    }

    public void setTargetProcessPattern(String targetProcessPattern) {
        this.targetProcessPattern = targetProcessPattern;
    }

    public String getSelectedProcessName() {
        return selectedProcessName;
    }

    public void setSelectedProcessName(String selectedProcessName) {
        this.selectedProcessName = selectedProcessName;
    }

    public String getOutputExpression() {
        return outputExpression;
    }

    public void setOutputExpression(String outputExpression) {
        this.outputExpression = outputExpression;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public String getCommandOptions() {
        return commandOptions;
    }

    public void setCommandOptions(String commandOptions) {
        this.commandOptions = commandOptions;
    }

    public Integer getStackDepth() {
        return stackDepth;
    }

    public void setStackDepth(Integer stackDepth) {
        this.stackDepth = stackDepth;
    }

    public Integer getMaxMatches() {
        return maxMatches;
    }

    public void setMaxMatches(Integer maxMatches) {
        this.maxMatches = maxMatches;
    }

    public Long getExecutionTimeoutMs() {
        return executionTimeoutMs;
    }

    public void setExecutionTimeoutMs(Long executionTimeoutMs) {
        this.executionTimeoutMs = executionTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
