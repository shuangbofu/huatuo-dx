package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.LogSourceConfigView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LogCollectorService {

    private final HuatuoAgentProperties properties;
    private final DuckDbLogIndexService duckDbLogIndexService;
    private final LogEventAssembler logEventAssembler;
    private final AtomicLong lastCollectAt = new AtomicLong(0);

    public LogCollectorService(
            HuatuoAgentProperties properties,
            DuckDbLogIndexService duckDbLogIndexService,
            LogEventAssembler logEventAssembler
    ) {
        this.properties = properties;
        this.duckDbLogIndexService = duckDbLogIndexService;
        this.logEventAssembler = logEventAssembler;
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 1000)
    public void collect() {
        if (!properties.isLogCollectEnabled()) {
            return;
        }
        long intervalMillis = Math.max(properties.getLogCollectIntervalSeconds(), 1) * 1000L;
        long now = System.currentTimeMillis();
        long previous = lastCollectAt.get();
        if (previous > 0 && now - previous < intervalMillis) {
            return;
        }
        lastCollectAt.set(now);
        for (LogSourceConfigView config : properties.getLogSourceConfigs().stream()
                .filter(this::isCollectMode)
                .toList()) {
            String configuredRoot = config.path();
            if (configuredRoot == null || configuredRoot.isBlank()) {
                continue;
            }
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root)) {
                if (isLogFile(root)) {
                    collectFile(root, root, config);
                }
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(this::isLogFile)
                        .forEach(path -> collectFile(root, path, config));
            } catch (IOException ignored) {
                // best effort collector
            }
        }
    }

    private void collectFile(Path root, Path file, LogSourceConfigView config) {
        try {
            String filePath = file.toAbsolutePath().normalize().toString();
            String directory = root.toString();
            long fileSize = Files.size(file);
            FileTime lastModified = Files.getLastModifiedTime(file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Instant collectedAt = Instant.now();
            DuckDbLogIndexService.FileState state = duckDbLogIndexService.findFileState(filePath);
            boolean changed = state == null
                    || fileSize != state.fileSize()
                    || lastModified.toMillis() != state.lastModifiedEpochMs()
                    || lines.size() != state.lineCount();
            if (!changed) {
                return;
            }

            List<DuckDbLogIndexService.IndexedLogEntry> entries = logEventAssembler.assemble(lines, config).stream()
                    .map(event -> new DuckDbLogIndexService.IndexedLogEntry(event.lineNumber(), event.content()))
                    .toList();

            duckDbLogIndexService.replaceFileEntries(
                    directory,
                    filePath,
                    entries,
                    lines.size(),
                    fileSize,
                    lastModified.toMillis(),
                    collectedAt
            );
        } catch (IOException ignored) {
            // keep collector robust
        }
    }

    private boolean isLogFile(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".log") || fileName.contains(".log.");
    }

    private boolean isCollectMode(LogSourceConfigView config) {
        String mode = config.mode();
        return mode != null && mode.equalsIgnoreCase("COLLECT");
    }
}
