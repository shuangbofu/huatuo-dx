package top.fusb.huatuo.dx.agent.config;

import java.util.List;
import top.fusb.huatuo.dx.agent.dto.LogSourceConfigView;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatuo.dx.agent")
public class HuatuoAgentProperties {

    private String managerUrl;
    private String nodeCode;
    private String nodeName;
    private String host;
    private String publicBaseUrl;
    private String secret;
    private String processPattern;
    private List<String> logDirectories = List.of();
    private List<LogSourceConfigView> logSourceConfigs = List.of();
    private boolean logCollectEnabled = true;
    private int logCollectIntervalSeconds = 5;
    private String logDuckdbPath = "./data/huatuo-dx-agent-logs.duckdb";
    private String arthasBootJar;
    private String arthasVersion = "3.7.3";
    private String arthasDownloadBaseUrl = "https://arthas.aliyun.com/arthas-boot.jar";
    private String arthasInstallDir = "./arthas";
    private String arthasJavaHome;
    private int heartbeatIntervalSeconds = 15;
    private boolean localCompanion;

    public String getManagerUrl() {
        return managerUrl;
    }

    public void setManagerUrl(String managerUrl) {
        this.managerUrl = managerUrl;
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

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
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

    public List<String> getLogDirectories() {
        return logDirectories;
    }

    public void setLogDirectories(List<String> logDirectories) {
        this.logDirectories = logDirectories;
    }

    public List<LogSourceConfigView> getLogSourceConfigs() {
        return logSourceConfigs;
    }

    public void setLogSourceConfigs(List<LogSourceConfigView> logSourceConfigs) {
        this.logSourceConfigs = logSourceConfigs;
    }

    public boolean isLogCollectEnabled() {
        return logCollectEnabled;
    }

    public void setLogCollectEnabled(boolean logCollectEnabled) {
        this.logCollectEnabled = logCollectEnabled;
    }

    public int getLogCollectIntervalSeconds() {
        return logCollectIntervalSeconds;
    }

    public void setLogCollectIntervalSeconds(int logCollectIntervalSeconds) {
        this.logCollectIntervalSeconds = logCollectIntervalSeconds;
    }

    public String getLogDuckdbPath() {
        return logDuckdbPath;
    }

    public void setLogDuckdbPath(String logDuckdbPath) {
        this.logDuckdbPath = logDuckdbPath;
    }

    public String getArthasBootJar() {
        return arthasBootJar;
    }

    public void setArthasBootJar(String arthasBootJar) {
        this.arthasBootJar = arthasBootJar;
    }

    public String getArthasVersion() {
        return arthasVersion;
    }

    public void setArthasVersion(String arthasVersion) {
        this.arthasVersion = arthasVersion;
    }

    public String getArthasDownloadBaseUrl() {
        return arthasDownloadBaseUrl;
    }

    public void setArthasDownloadBaseUrl(String arthasDownloadBaseUrl) {
        this.arthasDownloadBaseUrl = arthasDownloadBaseUrl;
    }

    public String getArthasInstallDir() {
        return arthasInstallDir;
    }

    public void setArthasInstallDir(String arthasInstallDir) {
        this.arthasInstallDir = arthasInstallDir;
    }

    public String getArthasJavaHome() {
        return arthasJavaHome;
    }

    public void setArthasJavaHome(String arthasJavaHome) {
        this.arthasJavaHome = arthasJavaHome;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public boolean isLocalCompanion() {
        return localCompanion;
    }

    public void setLocalCompanion(boolean localCompanion) {
        this.localCompanion = localCompanion;
    }
}
