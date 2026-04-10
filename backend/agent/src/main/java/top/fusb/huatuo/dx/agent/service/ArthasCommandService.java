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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", arthasJar.toString(), String.valueOf(process.pid()));
        builder.redirectErrorStream(true);
        try {
            Process arthasProcess = builder.start();
            MonitorSession session = new MonitorSession(UUID.randomUUID().toString(), rule, process, command, arthasProcess, startedAt);
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
            log.info("Started Arthas session {} for pid {} with command: {}", session.sessionId, process.pid(), command);
            startExitWatcher(session);
            if (applyTimeout) {
                startTimeoutWatcher(session);
            }
            return toResult(session);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.ARTHAS_EXECUTE_FAILED, "启动 Arthas 监控失败: " + exception.getMessage());
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

    private static final class MonitorSession {
        private final String sessionId;
        private final DiagnosticRuleView rule;
        private final ProcessView processView;
        private final String command;
        private final Process process;
        private final Instant startedAt;
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
                Instant startedAt
        ) {
            this.sessionId = sessionId;
            this.rule = rule;
            this.processView = processView;
            this.command = command;
            this.process = process;
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
            this.durationMs = 0L;
        }
    }
}
