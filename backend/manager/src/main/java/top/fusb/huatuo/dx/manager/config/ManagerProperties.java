package top.fusb.huatuo.dx.manager.config;

import java.util.List;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatuo.dx.manager")
public class ManagerProperties {

    private long heartbeatTimeoutSeconds = 90;
    private long syncIntervalSeconds = 30;
    private Auth auth = new Auth();
    private LocalAgent localAgent = new LocalAgent();
    private Plugin plugin = new Plugin();

    public long getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public long getSyncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    public void setSyncIntervalSeconds(long syncIntervalSeconds) {
        this.syncIntervalSeconds = syncIntervalSeconds;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public LocalAgent getLocalAgent() {
        return localAgent;
    }

    public void setLocalAgent(LocalAgent localAgent) {
        this.localAgent = localAgent;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public static class Auth {
        private String defaultAdminPassword = "12345";
        private String defaultUserPassword = "12345";

        public String getDefaultAdminPassword() {
            return defaultAdminPassword;
        }

        public void setDefaultAdminPassword(String defaultAdminPassword) {
            this.defaultAdminPassword = defaultAdminPassword;
        }

        public String getDefaultUserPassword() {
            return defaultUserPassword;
        }

        public void setDefaultUserPassword(String defaultUserPassword) {
            this.defaultUserPassword = defaultUserPassword;
        }
    }

    public static class LocalAgent {
        private static final String DEFAULT_RUNTIME_DIR = Path.of(System.getProperty("java.io.tmpdir"), "huatuo-dx-local-agent").toString();

        private boolean autoStartEnabled = true;
        private String healthUrl;
        private String nodeCode = "local-companion";
        private String nodeName = "Local Companion Agent";
        private String host = "127.0.0.1";
        private String secret = "change-me";
        private String processPattern = "java";
        private Integer heartbeatIntervalSeconds = 15;
        private String logDuckdbPath = "./data/huatuo-dx-agent-logs.duckdb";
        private String arthasInstallDir = "./arthas";
        private String arthasVersion = "3.7.3";
        private String arthasDownloadBaseUrl = "https://arthas.aliyun.com/arthas-boot.jar";
        private String agentJarPath;
        private List<String> command = List.of();
        private String workingDirectory;
        private String logFile;
        private String pidFile;
        private long startupWaitMillis = 1500;
        private long startupTimeoutMillis = 30000;
        private long startupPollIntervalMillis = 1000;

        public boolean isAutoStartEnabled() {
            return autoStartEnabled;
        }

        public void setAutoStartEnabled(boolean autoStartEnabled) {
            this.autoStartEnabled = autoStartEnabled;
        }

        public String getHealthUrl() {
            return healthUrl;
        }

        public void setHealthUrl(String healthUrl) {
            this.healthUrl = healthUrl;
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

        public Integer getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(Integer heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public String getLogDuckdbPath() {
            return logDuckdbPath;
        }

        public void setLogDuckdbPath(String logDuckdbPath) {
            this.logDuckdbPath = logDuckdbPath;
        }

        public String getArthasInstallDir() {
            return arthasInstallDir;
        }

        public void setArthasInstallDir(String arthasInstallDir) {
            this.arthasInstallDir = arthasInstallDir;
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

        public String getAgentJarPath() {
            return agentJarPath;
        }

        public void setAgentJarPath(String agentJarPath) {
            this.agentJarPath = agentJarPath;
        }

        public List<String> getCommand() {
            return command;
        }

        public void setCommand(List<String> command) {
            this.command = command;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public void setWorkingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public String getLogFile() {
            return logFile == null || logFile.isBlank()
                    ? Path.of(DEFAULT_RUNTIME_DIR, "local-agent.log").toString()
                    : logFile;
        }

        public void setLogFile(String logFile) {
            this.logFile = logFile;
        }

        public String getPidFile() {
            return pidFile == null || pidFile.isBlank()
                    ? Path.of(DEFAULT_RUNTIME_DIR, "local-agent.pid").toString()
                    : pidFile;
        }

        public void setPidFile(String pidFile) {
            this.pidFile = pidFile;
        }

        public long getStartupWaitMillis() {
            return startupWaitMillis;
        }

        public void setStartupWaitMillis(long startupWaitMillis) {
            this.startupWaitMillis = startupWaitMillis;
        }

        public long getStartupTimeoutMillis() {
            return startupTimeoutMillis;
        }

        public void setStartupTimeoutMillis(long startupTimeoutMillis) {
            this.startupTimeoutMillis = startupTimeoutMillis;
        }

        public long getStartupPollIntervalMillis() {
            return startupPollIntervalMillis;
        }

        public void setStartupPollIntervalMillis(long startupPollIntervalMillis) {
            this.startupPollIntervalMillis = startupPollIntervalMillis;
        }
    }

    public static class Plugin {
        private boolean enabled = true;
        private String accessKey = "huatuo-idea";
        private String accessSecret = "change-me";
        private long allowedClockSkewSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getAccessSecret() {
            return accessSecret;
        }

        public void setAccessSecret(String accessSecret) {
            this.accessSecret = accessSecret;
        }

        public long getAllowedClockSkewSeconds() {
            return allowedClockSkewSeconds;
        }

        public void setAllowedClockSkewSeconds(long allowedClockSkewSeconds) {
            this.allowedClockSkewSeconds = allowedClockSkewSeconds;
        }
    }
}
