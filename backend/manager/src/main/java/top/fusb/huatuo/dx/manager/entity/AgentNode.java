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
@Table(name = "agent_nodes")
public class AgentNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nodeCode;

    @Column(nullable = false)
    private String nodeName;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String secret;

    private String processPattern;

    @Lob
    private String visibleProcessNames;

    @Lob
    private String logDirectories;

    @Lob
    private String logSourceConfigs;

    @Lob
    private String companionBootstrapLogDirectories;

    @Column(nullable = false)
    private boolean logCollectEnabled = true;

    @Column(nullable = false)
    private Integer logCollectIntervalSeconds = 5;

    @Lob
    private String tags;

    private String arthasBootJar;

    @Column(nullable = false)
    private boolean localCompanion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeStatus status = NodeStatus.OFFLINE;

    private String runtimeVersion;

    private String matchedProcessSummary;

    private Instant lastHeartbeatAt;

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

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getProcessPattern() {
        return processPattern;
    }

    public void setProcessPattern(String processPattern) {
        this.processPattern = processPattern;
    }

    public String getVisibleProcessNames() {
        return visibleProcessNames;
    }

    public void setVisibleProcessNames(String visibleProcessNames) {
        this.visibleProcessNames = visibleProcessNames;
    }

    public String getLogDirectories() {
        return logDirectories;
    }

    public void setLogDirectories(String logDirectories) {
        this.logDirectories = logDirectories;
    }

    public boolean isLogCollectEnabled() {
        return logCollectEnabled;
    }

    public String getLogSourceConfigs() {
        return logSourceConfigs;
    }

    public void setLogSourceConfigs(String logSourceConfigs) {
        this.logSourceConfigs = logSourceConfigs;
    }

    public String getCompanionBootstrapLogDirectories() {
        return companionBootstrapLogDirectories;
    }

    public void setCompanionBootstrapLogDirectories(String companionBootstrapLogDirectories) {
        this.companionBootstrapLogDirectories = companionBootstrapLogDirectories;
    }

    public void setLogCollectEnabled(boolean logCollectEnabled) {
        this.logCollectEnabled = logCollectEnabled;
    }

    public Integer getLogCollectIntervalSeconds() {
        return logCollectIntervalSeconds;
    }

    public void setLogCollectIntervalSeconds(Integer logCollectIntervalSeconds) {
        this.logCollectIntervalSeconds = logCollectIntervalSeconds;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getArthasBootJar() {
        return arthasBootJar;
    }

    public void setArthasBootJar(String arthasBootJar) {
        this.arthasBootJar = arthasBootJar;
    }

    public boolean isLocalCompanion() {
        return localCompanion;
    }

    public void setLocalCompanion(boolean localCompanion) {
        this.localCompanion = localCompanion;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getMatchedProcessSummary() {
        return matchedProcessSummary;
    }

    public void setMatchedProcessSummary(String matchedProcessSummary) {
        this.matchedProcessSummary = matchedProcessSummary;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
