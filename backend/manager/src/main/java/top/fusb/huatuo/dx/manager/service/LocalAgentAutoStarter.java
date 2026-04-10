package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.HuatuoDxManagerApplication;
import top.fusb.huatuo.dx.manager.config.ManagerProperties;
import top.fusb.huatuo.dx.manager.entity.AgentNode;
import top.fusb.huatuo.dx.manager.repo.AgentNodeRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LocalAgentAutoStarter {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentAutoStarter.class);

    private final ManagerProperties properties;
    private final WebServerApplicationContext webServerApplicationContext;
    private final AgentNodeRepository agentNodeRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private volatile String restartSuggestedBaseUrl;

    public LocalAgentAutoStarter(
            ManagerProperties properties,
            WebServerApplicationContext webServerApplicationContext,
            AgentNodeRepository agentNodeRepository
    ) {
        this.properties = properties;
        this.webServerApplicationContext = webServerApplicationContext;
        this.agentNodeRepository = agentNodeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startLocalAgentIfNeeded() {
        ManagerProperties.LocalAgent localAgent = properties.getLocalAgent();
        if (localAgent == null || !localAgent.isAutoStartEnabled()) {
            return;
        }
        ResolvedLocalAgent resolved = resolveRuntimeConfig(localAgent);
        if (isHealthy(resolved.healthUrl())) {
            restartSuggestedBaseUrl = resolved.publicBaseUrl();
            log.info("Local agent is already healthy at {}", resolved.healthUrl());
            return;
        }
        List<String> command = resolveCommand(localAgent, resolved);
        if (command.isEmpty()) {
            log.warn("Local agent auto start is enabled, but no executable command could be resolved.");
            return;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (localAgent.getWorkingDirectory() != null && !localAgent.getWorkingDirectory().isBlank()) {
                processBuilder.directory(Paths.get(localAgent.getWorkingDirectory()).toFile());
            }
            if (localAgent.getLogFile() != null && !localAgent.getLogFile().isBlank()) {
                Path logFile = Paths.get(localAgent.getLogFile());
                if (logFile.getParent() != null) {
                    Files.createDirectories(logFile.getParent());
                }
                processBuilder.redirectErrorStream(true);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            }
            Process process = processBuilder.start();
            writePidFile(localAgent, process.pid());
            restartSuggestedBaseUrl = null;
            log.info("Started local agent process, pid={}, command={}", process.pid(), String.join(" ", command));
            if (localAgent.getStartupWaitMillis() > 0) {
                Thread.sleep(localAgent.getStartupWaitMillis());
            }
            StartupState startupState = waitForStartup(process, resolved, localAgent);
            if (!startupState.healthReady()) {
                log.warn("Local agent process was started but health check is still not ready: {}", resolved.healthUrl());
            } else if (!startupState.registered()) {
                log.warn("Local agent is healthy at {} but has not registered to manager yet: {}", resolved.healthUrl(), resolved.publicBaseUrl());
            } else {
                log.info("Local agent is healthy and registered: {}", resolved.publicBaseUrl());
            }
        } catch (Exception exception) {
            log.warn("Failed to auto start local agent: {}", exception.getMessage());
        }
    }

    public synchronized void restartLocalAgent(String expectedBaseUrl) {
        ManagerProperties.LocalAgent localAgent = properties.getLocalAgent();
        if (localAgent == null || !localAgent.isAutoStartEnabled()) {
            return;
        }
        ResolvedLocalAgent resolved = resolveRuntimeConfig(localAgent);
        if (expectedBaseUrl != null && !expectedBaseUrl.isBlank() && !expectedBaseUrl.equals(resolved.publicBaseUrl())) {
            resolved = resolved.withBaseUrl(expectedBaseUrl);
        }
        stopExistingProcess(localAgent);
        restartSuggestedBaseUrl = null;
        startLocalAgentIfNeeded();
    }

    public boolean isRestartRecommended(AgentNode node) {
        return node.isLocalCompanion()
                && restartSuggestedBaseUrl != null
                && restartSuggestedBaseUrl.equals(node.getBaseUrl());
    }

    private StartupState waitForStartup(Process process, ResolvedLocalAgent resolved, ManagerProperties.LocalAgent localAgent)
            throws InterruptedException {
        long timeoutMillis = Math.max(localAgent.getStartupTimeoutMillis(), 1000);
        long pollIntervalMillis = Math.max(localAgent.getStartupPollIntervalMillis(), 200);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        boolean healthReady = false;
        boolean registered = false;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                break;
            }
            healthReady = isHealthy(resolved.healthUrl());
            registered = isRegistered(resolved.publicBaseUrl());
            if (healthReady && registered) {
                break;
            }
            Thread.sleep(pollIntervalMillis);
        }
        return new StartupState(healthReady, registered);
    }

    private boolean isRegistered(String baseUrl) {
        Optional<AgentNode> node = agentNodeRepository.findByBaseUrl(baseUrl);
        return node.isPresent() && node.get().getLastHeartbeatAt() != null;
    }

    private boolean isHealthy(String healthUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> resolveCommand(ManagerProperties.LocalAgent localAgent, ResolvedLocalAgent resolved) {
        if (localAgent.getCommand() != null && !localAgent.getCommand().isEmpty()) {
            return localAgent.getCommand();
        }
        Path agentJar = detectAgentJar();
        if (agentJar == null) {
            agentJar = buildAgentJarIfNeeded();
        }
        if (agentJar == null) {
            return List.of();
        }
        EffectiveLocalAgentConfig config = resolveEffectiveLocalAgentConfig(localAgent);
        List<String> command = new ArrayList<>();
        command.add(resolveJavaExecutable());
        command.add("-jar");
        command.add(agentJar.toString());
        command.add("--server.port=" + resolved.agentPort());
        command.add("--huatuo.dx.agent.manager-url=" + resolved.managerUrl());
        command.add("--huatuo.dx.agent.public-base-url=" + resolved.publicBaseUrl());
        command.add("--huatuo.dx.agent.local-companion=true");
        command.add("--huatuo.dx.agent.node-code=" + config.nodeCode());
        command.add("--huatuo.dx.agent.node-name=" + config.nodeName());
        command.add("--huatuo.dx.agent.host=" + config.host());
        command.add("--huatuo.dx.agent.secret=" + config.secret());
        command.add("--huatuo.dx.agent.process-pattern=" + config.processPattern());
        command.add("--huatuo.dx.agent.heartbeat-interval-seconds=" + config.heartbeatIntervalSeconds());
        command.add("--huatuo.dx.agent.log-duckdb-path=" + config.logDuckdbPath());
        command.add("--huatuo.dx.agent.arthas-install-dir=" + config.arthasInstallDir());
        command.add("--huatuo.dx.agent.arthas-version=" + config.arthasVersion());
        command.add("--huatuo.dx.agent.arthas-download-base-url=" + config.arthasDownloadBaseUrl());
        command.add("--huatuo.dx.agent.log-collect-enabled=" + config.logCollectEnabled());
        command.add("--huatuo.dx.agent.log-collect-interval-seconds=" + config.logCollectIntervalSeconds());
        if (config.arthasBootJar() != null && !config.arthasBootJar().isBlank()) {
            command.add("--huatuo.dx.agent.arthas-boot-jar=" + config.arthasBootJar());
        }
        List<String> bootstrapLogDirs = config.logDirectories();
        if (!bootstrapLogDirs.isEmpty()) {
            command.add("--huatuo.dx.agent.log-directories=" + String.join(",", bootstrapLogDirs));
        }
        return command;
    }

    private EffectiveLocalAgentConfig resolveEffectiveLocalAgentConfig(ManagerProperties.LocalAgent localAgent) {
        Optional<AgentNode> existingNode = agentNodeRepository.findAll().stream()
                .filter(AgentNode::isLocalCompanion)
                .findFirst();
        List<String> bootstrapLogDirs = existingNode
                .map(AgentNode::getCompanionBootstrapLogDirectories)
                .filter(raw -> raw != null && !raw.isBlank())
                .map(this::splitCommaValues)
                .orElse(List.of());
        if (bootstrapLogDirs.isEmpty()) {
            bootstrapLogDirs = existingNode
                    .map(AgentNode::getLogDirectories)
                    .filter(raw -> raw != null && !raw.isBlank())
                    .map(this::splitCommaValues)
                    .orElse(List.of());
        }
        return new EffectiveLocalAgentConfig(
                existingNode.map(AgentNode::getNodeCode).filter(this::hasText).orElse(localAgent.getNodeCode()),
                existingNode.map(AgentNode::getNodeName).filter(this::hasText).orElse(localAgent.getNodeName()),
                existingNode.map(AgentNode::getHost).filter(this::hasText).orElse(localAgent.getHost()),
                existingNode.map(AgentNode::getSecret).filter(this::hasText).orElse(localAgent.getSecret()),
                existingNode.map(AgentNode::getProcessPattern).filter(this::hasText).orElse(localAgent.getProcessPattern()),
                localAgent.getHeartbeatIntervalSeconds(),
                localAgent.getLogDuckdbPath(),
                localAgent.getArthasInstallDir(),
                localAgent.getArthasVersion(),
                localAgent.getArthasDownloadBaseUrl(),
                existingNode.map(AgentNode::isLogCollectEnabled).orElse(true),
                existingNode.map(AgentNode::getLogCollectIntervalSeconds).filter(interval -> interval != null && interval > 0).orElse(5),
                existingNode.map(AgentNode::getArthasBootJar).filter(this::hasText).orElse(null),
                bootstrapLogDirs
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> splitCommaValues(String raw) {
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private void stopExistingProcess(ManagerProperties.LocalAgent localAgent) {
        readPidFile(localAgent)
                .flatMap(ProcessHandle::of)
                .ifPresent(process -> {
                    process.destroy();
                    try {
                        process.onExit().get();
                    } catch (Exception ignored) {
                        process.destroyForcibly();
                    }
                });
        deletePidFile(localAgent);
    }

    private void writePidFile(ManagerProperties.LocalAgent localAgent, long pid) {
        try {
            Path pidFile = Paths.get(localAgent.getPidFile());
            if (pidFile.getParent() != null) {
                Files.createDirectories(pidFile.getParent());
            }
            Files.writeString(pidFile, String.valueOf(pid), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception exception) {
            log.warn("Failed to write local agent pid file: {}", exception.getMessage());
        }
    }

    private Optional<Long> readPidFile(ManagerProperties.LocalAgent localAgent) {
        try {
            Path pidFile = Paths.get(localAgent.getPidFile());
            if (!Files.exists(pidFile)) {
                return Optional.empty();
            }
            String raw = Files.readString(pidFile).trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(raw));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void deletePidFile(ManagerProperties.LocalAgent localAgent) {
        try {
            Files.deleteIfExists(Paths.get(localAgent.getPidFile()));
        } catch (Exception ignored) {
        }
    }

    private String resolveJavaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    }

    private Path detectAgentJar() {
        try {
            Path backendDir = resolveBackendDir();
            if (backendDir == null) {
                return null;
            }
            Path agentTargetDir = backendDir.resolve("agent").resolve("target");
            if (!Files.isDirectory(agentTargetDir)) {
                return null;
            }
            try (Stream<Path> stream = Files.list(agentTargetDir)) {
                return stream
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith("huatuo-dx-agent-")
                                    && name.endsWith(".jar")
                                    && !name.endsWith(".jar.original");
                        })
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path buildAgentJarIfNeeded() {
        try {
            Path backendDir = resolveBackendDir();
            if (backendDir == null) {
                return null;
            }
            List<String> buildCommand = List.of("mvn", "-f", backendDir.resolve("pom.xml").toString(), "-pl", "agent", "-am", "-DskipTests", "package");
            Process process = new ProcessBuilder(buildCommand)
                    .directory(backendDir.toFile())
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Failed to build local agent jar automatically, exitCode={}", exitCode);
                return null;
            }
            return detectAgentJar();
        } catch (Exception exception) {
            log.warn("Failed to build local agent jar: {}", exception.getMessage());
            return null;
        }
    }

    private Path resolveBackendDir() {
        try {
            Path location = Paths.get(HuatuoDxManagerApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path managerDir = location.getParent() != null && location.getParent().getParent() != null
                    ? location.getParent().getParent()
                    : null;
            return managerDir == null ? null : managerDir.getParent();
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResolvedLocalAgent resolveRuntimeConfig(ManagerProperties.LocalAgent localAgent) {
        int managerPort = webServerApplicationContext.getWebServer().getPort();
        int agentPort = parsePort(localAgent.getHealthUrl())
                .or(this::resolveExistingLocalCompanionPort)
                .orElseGet(this::findFreePort);
        return new ResolvedLocalAgent(
                agentPort,
                "http://127.0.0.1:" + agentPort + "/actuator/health",
                "http://127.0.0.1:" + agentPort,
                "http://127.0.0.1:" + managerPort
        );
    }

    private Optional<Integer> resolveExistingLocalCompanionPort() {
        return agentNodeRepository.findAll().stream()
                .filter(AgentNode::isLocalCompanion)
                .map(AgentNode::getBaseUrl)
                .filter(this::isAgentBaseUrlHealthy)
                .map(this::parsePort)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private boolean isAgentBaseUrlHealthy(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        return isHealthy(baseUrl + "/actuator/health");
    }

    private Optional<Integer> parsePort(String url) {
        try {
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            URI uri = URI.create(url);
            return uri.getPort() > 0 ? Optional.of(uri.getPort()) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        } catch (IOException ignored) {
            return 8090;
        }
    }

    private record ResolvedLocalAgent(
            int agentPort,
            String healthUrl,
            String publicBaseUrl,
            String managerUrl
    ) {
        private ResolvedLocalAgent withBaseUrl(String baseUrl) {
            int port = URI.create(baseUrl).getPort();
            return new ResolvedLocalAgent(port, baseUrl + "/actuator/health", baseUrl, managerUrl);
        }
    }

    private record StartupState(boolean healthReady, boolean registered) {
    }

    private record EffectiveLocalAgentConfig(
            String nodeCode,
            String nodeName,
            String host,
            String secret,
            String processPattern,
            Integer heartbeatIntervalSeconds,
            String logDuckdbPath,
            String arthasInstallDir,
            String arthasVersion,
            String arthasDownloadBaseUrl,
            boolean logCollectEnabled,
            Integer logCollectIntervalSeconds,
            String arthasBootJar,
            List<String> logDirectories
    ) {
    }
}
