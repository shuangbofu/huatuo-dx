package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionRequest;
import top.fusb.huatuo.dx.agent.dto.DiagnosticExecutionResult;
import top.fusb.huatuo.dx.agent.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.agent.dto.DiagnosticSessionStatus;
import top.fusb.huatuo.dx.agent.dto.DiagnosticType;
import top.fusb.huatuo.dx.agent.dto.ProcessView;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArthasCommandService {

    private static final Logger log = LoggerFactory.getLogger(ArthasCommandService.class);
    private static final String ARTHAS_BACKGROUND_MESSAGE = "Arthas is still running in the background.";

    private final HuatuoAgentProperties properties;
    private final ProcessDiscoveryService processDiscoveryService;
    private final ArthasInstallerService arthasInstallerService;
    private final ConcurrentHashMap<String, MonitorSession> sessions = new ConcurrentHashMap<>();

    public ArthasCommandService(
            HuatuoAgentProperties properties,
            ProcessDiscoveryService processDiscoveryService,
            ArthasInstallerService arthasInstallerService
    ) {
        this.properties = properties;
        this.processDiscoveryService = processDiscoveryService;
        this.arthasInstallerService = arthasInstallerService;
    }

    public DiagnosticExecutionResult start(DiagnosticRuleView rule, DiagnosticExecutionRequest request) {
        return startInternal(rule, request, false);
    }

    public DiagnosticExecutionResult execute(DiagnosticRuleView rule, DiagnosticExecutionRequest request) {
        return startInternal(rule, request, true);
    }

    private DiagnosticExecutionResult startInternal(
            DiagnosticRuleView rule,
            DiagnosticExecutionRequest request,
            boolean applyTimeout
    ) {
        Instant startedAt = Instant.now();
        ProcessView process = resolveTargetProcess(rule, request);
        String command = buildCommand(rule, request);
        Path arthasJar = arthasInstallerService.resolveArthasBootJar();
        JavaLaunchInfo javaLaunchInfo = resolveJavaLaunchInfo(process);
        int telnetPort = findFreePort();
        int httpPort = findFreePort();
        ProcessBuilder builder = new ProcessBuilder(
                javaLaunchInfo.command(),
                "-jar",
                arthasJar.toString(),
                "--telnet-port",
                String.valueOf(telnetPort),
                "--http-port",
                String.valueOf(httpPort),
                String.valueOf(process.pid())
        );
        builder.redirectErrorStream(true);
        if (javaLaunchInfo.javaHome() != null && !javaLaunchInfo.javaHome().isBlank()) {
            builder.environment().put("JAVA_HOME", javaLaunchInfo.javaHome());
        }
        MonitorSession session = null;
        try {
            Process arthasProcess = builder.start();
            session = new MonitorSession(
                    UUID.randomUUID().toString(),
                    rule,
                    process,
                    command,
                    arthasProcess,
                    startedAt,
                    telnetPort,
                    httpPort
            );
            sessions.put(session.sessionId, session);
            startBackgroundRead(session);
            waitForConsole(session, 20000);
            OutputStream stdin = arthasProcess.getOutputStream();
            stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            synchronized (session.monitor) {
                session.commandDispatched = true;
                session.promptCountAtDispatch = countPromptOccurrences(session.output);
            }
            log.info(
                    "Started Arthas session {} for pid {} with command: {}, javaCommand={}, javaHome={}",
                    session.sessionId,
                    process.pid(),
                    command,
                    javaLaunchInfo.command(),
                    javaLaunchInfo.javaHome()
            );
            startExitWatcher(session);
            if (applyTimeout) {
                startTimeoutWatcher(session);
            }
            return toResult(session);
        } catch (BusinessException exception) {
            String enrichedMessage = enrichStartupFailureMessage(exception.getMessage(), session, telnetPort, httpPort);
            log.error(
                    "Failed to start Arthas session for pid {}, command={}, javaCommand={}, javaHome={}, telnetPort={}, httpPort={}, message={}",
                    process.pid(),
                    command,
                    javaLaunchInfo.command(),
                    javaLaunchInfo.javaHome(),
                    telnetPort,
                    httpPort,
                    enrichedMessage,
                    exception
            );
            throw new BusinessException(ErrorCode.ARTHAS_EXECUTE_FAILED, "启动 Arthas 监控失败: " + enrichedMessage);
        } catch (Exception exception) {
            String enrichedMessage = enrichStartupFailureMessage(exception.getMessage(), session, telnetPort, httpPort);
            log.error(
                    "Failed to start Arthas session for pid {}, command={}, javaCommand={}, javaHome={}, telnetPort={}, httpPort={}, message={}",
                    process.pid(),
                    command,
                    javaLaunchInfo.command(),
                    javaLaunchInfo.javaHome(),
                    telnetPort,
                    httpPort,
                    enrichedMessage,
                    exception
            );
            throw new BusinessException(ErrorCode.ARTHAS_EXECUTE_FAILED, "启动 Arthas 监控失败: " + enrichedMessage);
        }
    }

    public DiagnosticExecutionResult session(String sessionId) {
        return toResult(getSession(sessionId));
    }

    public DiagnosticExecutionResult stop(String sessionId) {
        MonitorSession session = getSession(sessionId);
        synchronized (session.monitor) {
            if (session.status != DiagnosticSessionStatus.RUNNING) {
                return toResult(session);
            }
            session.status = DiagnosticSessionStatus.STOPPED;
            session.updatedAt = Instant.now();
            session.durationMs = Duration.between(session.startedAt, session.updatedAt).toMillis();
            try {
                OutputStream stdin = session.process.getOutputStream();
                stdin.write("quit\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException ignored) {
                // ignore and destroy below
            }
            try {
                session.process.waitFor(1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (session.process.isAlive()) {
                session.process.destroy();
            }
            if (session.process.isAlive()) {
                session.process.destroyForcibly();
            }
            return toResult(session);
        }
    }

    private MonitorSession getSession(String sessionId) {
        MonitorSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "监控会话不存在: " + sessionId);
        }
        return session;
    }

    private void startBackgroundRead(MonitorSession session) {
        Thread readerThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            try (InputStream inputStream = session.process.getInputStream()) {
                while (true) {
                    int read = inputStream.read(buffer);
                    if (read < 0) {
                        return;
                    }
                    synchronized (session.monitor) {
                        session.output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        session.updatedAt = Instant.now();
                        session.durationMs = Duration.between(session.startedAt, session.updatedAt).toMillis();
                        if (shouldAutoComplete(session)) {
                            session.status = DiagnosticSessionStatus.COMPLETED;
                            session.monitor.notifyAll();
                            quitProcessQuietly(session);
                            continue;
                        }
                        session.monitor.notifyAll();
                    }
                }
            } catch (IOException exception) {
                synchronized (session.monitor) {
                    if (session.status == DiagnosticSessionStatus.RUNNING) {
                        session.status = DiagnosticSessionStatus.FAILED;
                        session.errorMessage = "读取监控输出失败: " + exception.getMessage();
                        session.updatedAt = Instant.now();
                        session.durationMs = Duration.between(session.startedAt, session.updatedAt).toMillis();
                        log.error("Failed to read Arthas session {} output", session.sessionId, exception);
                    }
                }
            }
        }, "arthas-monitor-reader-" + session.sessionId);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void startExitWatcher(MonitorSession session) {
        Thread waiterThread = new Thread(() -> {
            try {
                int exitCode = session.process.waitFor();
                synchronized (session.monitor) {
                    if (session.status == DiagnosticSessionStatus.RUNNING) {
                        session.status = exitCode == 0 ? DiagnosticSessionStatus.COMPLETED : DiagnosticSessionStatus.FAILED;
                    }
                    session.updatedAt = Instant.now();
                    session.durationMs = Duration.between(session.startedAt, session.updatedAt).toMillis();
                    if (exitCode != 0 && (session.errorMessage == null || session.errorMessage.isBlank())) {
                        session.errorMessage = "监控进程退出，退出码: " + exitCode;
                    }
                    log.info("Arthas session {} exited with code {}, status {}", session.sessionId, exitCode, session.status);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "arthas-monitor-waiter-" + session.sessionId);
        waiterThread.setDaemon(true);
        waiterThread.start();
    }

    private void startTimeoutWatcher(MonitorSession session) {
        long timeoutMs = session.rule.executionTimeoutMs() == null ? 60000L : session.rule.executionTimeoutMs();
        if (timeoutMs <= 0) {
            return;
        }
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
                synchronized (session.monitor) {
                    if (session.status != DiagnosticSessionStatus.RUNNING) {
                        return;
                    }
                }
                stop(session.sessionId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "arthas-monitor-timeout-" + session.sessionId);
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    private ProcessView resolveTargetProcess(DiagnosticRuleView rule, DiagnosticExecutionRequest request) {
        if (request != null && request.pid() != null) {
            return processDiscoveryService.findByPid(request.pid())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_PROCESS_NOT_FOUND, "没有找到目标 Java 进程 PID: " + request.pid()));
        }
        String processPattern = optional(request == null ? null : request.processPattern())
                .or(() -> optional(request == null ? null : request.processName()))
                .or(() -> optional(rule.targetProcessPattern()))
                .or(() -> optional(properties.getProcessPattern()))
                .orElse("");
        return processDiscoveryService.findBestMatch(processPattern)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TARGET_PROCESS_NOT_FOUND,
                        processPattern.isBlank() ? "请选择一个目标 Java 进程" : "没有匹配到目标 Java 进程: " + processPattern
                ));
    }

    private String buildCommand(DiagnosticRuleView rule, DiagnosticExecutionRequest request) {
        String clazz = rule.targetClassPattern();
        String method = rule.targetMethodPattern();
        int depth = rule.stackDepth() == null ? 2 : rule.stackDepth();
        int maxMatches = request != null && request.maxMatches() != null
                ? request.maxMatches()
                : (rule.maxMatches() == null ? 1 : rule.maxMatches());
        String condition = optional(rule.conditionExpression()).map(this::quote).orElse("");
        String commandOptions = optional(rule.commandOptions()).orElse("");
        if (rule.type() == DiagnosticType.TRACE) {
            StringBuilder builder = new StringBuilder("trace ")
                    .append(clazz)
                    .append(' ')
                    .append(method);
            if (!condition.isBlank()) {
                builder.append(' ').append(condition);
            }
            builder.append(" -n ").append(maxMatches);
            if (!commandOptions.isBlank()) {
                builder.append(' ').append(commandOptions);
            }
            return builder.toString();
        }
        if (rule.type() == DiagnosticType.STACK) {
            StringBuilder builder = new StringBuilder("stack ")
                    .append(clazz)
                    .append(' ')
                    .append(method);
            if (!condition.isBlank()) {
                builder.append(' ').append(condition);
            }
            builder.append(" -n ").append(maxMatches);
            if (!commandOptions.isBlank()) {
                builder.append(' ').append(commandOptions);
            }
            return builder.toString();
        }
        String output = quote(optional(rule.outputExpression()).orElse("{params,returnObj,throwExp}"));
        StringBuilder builder = new StringBuilder("watch ")
                .append(clazz)
                .append(' ')
                .append(method)
                .append(' ')
                .append(output);
        if (!condition.isBlank()) {
            builder.append(' ').append(condition);
        }
        builder.append(" -x ").append(depth).append(" -n ").append(maxMatches);
        if (!commandOptions.isBlank()) {
            builder.append(' ').append(commandOptions);
        }
        return builder.toString();
    }

    private DiagnosticExecutionResult toResult(MonitorSession session) {
        synchronized (session.monitor) {
            boolean success = session.status == DiagnosticSessionStatus.COMPLETED || session.status == DiagnosticSessionStatus.STOPPED;
            return new DiagnosticExecutionResult(
                    session.sessionId,
                    session.rule.id(),
                    session.rule.name(),
                    session.rule.type(),
                    session.status,
                    success,
                    session.command,
                    session.output.toString().trim(),
                    session.errorMessage,
                    session.startedAt,
                    session.updatedAt,
                    session.durationMs,
                    session.processView.pid()
            );
        }
    }

    private void waitForConsole(MonitorSession session, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (session.monitor) {
            while (System.currentTimeMillis() < deadline) {
                if (session.output.toString().contains("[arthas@")) {
                    return;
                }
                if (!session.process.isAlive()) {
                    waitForOutputFlush(session, 300);
                    throw new BusinessException(
                            ErrorCode.ARTHAS_EXECUTE_FAILED,
                            "Arthas 控制台未就绪，进程已退出: " + session.process.exitValue()
                    );
                }
                long waitMs = Math.min(250, deadline - System.currentTimeMillis());
                if (waitMs <= 0) {
                    break;
                }
                session.monitor.wait(waitMs);
            }
        }
        throw new BusinessException(ErrorCode.ARTHAS_EXECUTE_FAILED, "Arthas 控制台未在规定时间内就绪");
    }

    private void waitForOutputFlush(MonitorSession session, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int previousLength = session.output.length();
        while (System.currentTimeMillis() < deadline) {
            session.monitor.wait(Math.min(100, deadline - System.currentTimeMillis()));
            int currentLength = session.output.length();
            if (currentLength > previousLength) {
                previousLength = currentLength;
                continue;
            }
            if (!session.process.isAlive()) {
                break;
            }
        }
    }

    private String enrichStartupFailureMessage(String baseMessage, MonitorSession session, int telnetPort, int httpPort) {
        String message = (baseMessage == null || baseMessage.isBlank()) ? "未知原因" : baseMessage;
        message = message + "（会话端口: telnet=" + telnetPort + ", http=" + httpPort + "）";
        String output = startupOutputSnippet(session);
        if (output.isBlank()) {
            return message;
        }
        return message + "；Arthas 原始输出: " + output;
    }

    private String startupOutputSnippet(MonitorSession session) {
        if (session == null) {
            return "";
        }
        String output;
        synchronized (session.monitor) {
            output = session.output.toString();
        }
        if (output == null) {
            return "";
        }
        String normalized = output
                .replace("\r", " ")
                .replace("\n", " | ")
                .replace("\t", " ")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > 500) {
            return normalized.substring(0, 500) + "...";
        }
        return normalized;
    }

    private boolean shouldAutoComplete(MonitorSession session) {
        if (!session.commandDispatched || session.status != DiagnosticSessionStatus.RUNNING) {
            return false;
        }
        String currentOutput = session.output.toString();
        return countPromptOccurrences(currentOutput) > session.promptCountAtDispatch
                || currentOutput.contains(ARTHAS_BACKGROUND_MESSAGE);
    }

    private int countPromptOccurrences(StringBuilder output) {
        return countPromptOccurrences(output.toString());
    }

    private int countPromptOccurrences(String output) {
        int count = 0;
        int index = 0;
        while ((index = output.indexOf("[arthas@", index)) >= 0) {
            count++;
            index += "[arthas@".length();
        }
        return count;
    }

    private void quitProcessQuietly(MonitorSession session) {
        try {
            OutputStream stdin = session.process.getOutputStream();
            stdin.write("quit\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException exception) {
            log.debug("Failed to quit Arthas session {} gracefully: {}", session.sessionId, exception.getMessage());
        }
    }

    private Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String quote(String value) {
        return "'" + value.replace("'", "\\'") + "'";
    }

    private JavaLaunchInfo resolveJavaLaunchInfo(ProcessView process) {
        Optional<JavaLaunchInfo> fromProcessCommand = resolveTargetJavaHomeFromCommand(process)
                .flatMap(this::toLaunchInfo);
        if (fromProcessCommand.isPresent()) {
            JavaLaunchInfo launchInfo = fromProcessCommand.get();
            log.info("Resolved target JVM java command from process command for pid {}: {}", process.pid(), launchInfo.command());
            return launchInfo;
        }
        Optional<JavaLaunchInfo> fromJavaHome = resolveTargetJavaHome(process)
                .flatMap(this::toLaunchInfo);
        if (fromJavaHome.isPresent()) {
            JavaLaunchInfo launchInfo = fromJavaHome.get();
            log.info("Resolved target JVM java command from jcmd java.home for pid {}: {}", process.pid(), launchInfo.command());
            return launchInfo;
        }
        log.info("Falling back to default java command for pid {}", process.pid());
        return new JavaLaunchInfo("java", null);
    }

    private Optional<Path> resolveTargetJavaHome(ProcessView process) {
        ProcessBuilder builder = new ProcessBuilder("jcmd", String.valueOf(process.pid()), "VM.system_properties");
        builder.redirectErrorStream(true);
        try {
            Process jcmdProcess = builder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(jcmdProcess.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (left, right) -> left + "\n" + right);
            }
            int exitCode = jcmdProcess.waitFor();
            if (exitCode != 0) {
                log.warn("Failed to resolve target JVM java.home for pid {}, jcmd exitCode={}, output={}", process.pid(), exitCode, output.trim());
                return Optional.empty();
            }
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("java.home=")) {
                    String javaHome = trimmed.substring("java.home=".length()).trim();
                    if (!javaHome.isBlank()) {
                        Path normalizedJavaHome = normalizeJavaHome(Path.of(javaHome));
                        if (!normalizedJavaHome.equals(Path.of(javaHome))) {
                            log.info("Normalized target JVM java.home for pid {} from {} to {}", process.pid(), javaHome, normalizedJavaHome);
                        }
                        return Optional.of(normalizedJavaHome);
                    }
                }
            }
            log.warn("java.home was not found in jcmd output for pid {}", process.pid());
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Failed to inspect target JVM java.home for pid {}: {}", process.pid(), exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> resolveTargetJavaHomeFromCommand(ProcessView process) {
        String command = process.command();
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        Path commandPath;
        try {
            commandPath = Path.of(command).normalize();
        } catch (Exception exception) {
            log.warn("Failed to parse target JVM command path for pid {}: {}", process.pid(), command);
            return Optional.empty();
        }
        if (!Files.isRegularFile(commandPath) || !Files.isExecutable(commandPath)) {
            return Optional.empty();
        }
        Path binDir = commandPath.getParent();
        if (binDir == null || !binDir.getFileName().toString().equals("bin")) {
            return Optional.empty();
        }
        Path javaHome = binDir.getParent();
        if (javaHome == null) {
            return Optional.empty();
        }
        return Optional.of(normalizeJavaHome(javaHome));
    }

    private Path normalizeJavaHome(Path javaHome) {
        Path normalized = javaHome.normalize();
        Path fileName = normalized.getFileName();
        if (fileName == null || !fileName.toString().equalsIgnoreCase("jre")) {
            return normalized;
        }
        Path parent = normalized.getParent();
        if (parent == null) {
            return normalized;
        }
        Path parentJava = parent.resolve("bin").resolve("java");
        Path parentToolsJar = parent.resolve("lib").resolve("tools.jar");
        if (Files.isRegularFile(parentJava) && Files.isExecutable(parentJava) && Files.isRegularFile(parentToolsJar)) {
            return parent;
        }
        return normalized;
    }

    private Optional<JavaLaunchInfo> toLaunchInfo(Path javaHome) {
        Path normalizedHome = normalizeJavaHome(javaHome);
        Path javaCommand = normalizedHome.resolve("bin").resolve("java");
        if (!Files.isRegularFile(javaCommand) || !Files.isExecutable(javaCommand)) {
            return Optional.empty();
        }
        return Optional.of(new JavaLaunchInfo(javaCommand.toString(), normalizedHome.toString()));
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.ARTHAS_EXECUTE_FAILED, "分配 Arthas 监控端口失败: " + exception.getMessage());
        }
    }

    private record JavaLaunchInfo(String command, String javaHome) {
    }

    private static final class MonitorSession {
        private final String sessionId;
        private final DiagnosticRuleView rule;
        private final ProcessView processView;
        private final String command;
        private final Process process;
        private final Instant startedAt;
        private final int telnetPort;
        private final int httpPort;
        private final StringBuilder output = new StringBuilder();
        private final Object monitor = new Object();
        private boolean commandDispatched;
        private int promptCountAtDispatch;
        private DiagnosticSessionStatus status = DiagnosticSessionStatus.RUNNING;
        private String errorMessage;
        private Instant updatedAt;
        private long durationMs;

        private MonitorSession(
                String sessionId,
                DiagnosticRuleView rule,
                ProcessView processView,
                String command,
                Process process,
                Instant startedAt,
                int telnetPort,
                int httpPort
        ) {
            this.sessionId = sessionId;
            this.rule = rule;
            this.processView = processView;
            this.command = command;
            this.process = process;
            this.startedAt = startedAt;
            this.telnetPort = telnetPort;
            this.httpPort = httpPort;
            this.updatedAt = startedAt;
            this.durationMs = 0L;
        }
    }
}
