package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
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
    private final AtomicLong lastCollectAt = new AtomicLong(0);

    public LogCollectorService(HuatuoAgentProperties properties, DuckDbLogIndexService duckDbLogIndexService) {
        this.properties = properties;
        this.duckDbLogIndexService = duckDbLogIndexService;
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
        for (String configuredRoot : properties.getLogSourceConfigs().stream()
                .filter(this::isCollectMode)
                .map(config -> config.path())
                .filter(path -> path != null && !path.isBlank())
                .toList()) {
            if (configuredRoot == null || configuredRoot.isBlank()) {
                continue;
            }
            Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root)) {
                if (isLogFile(root)) {
                    collectFile(root, root);
                }
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(this::isLogFile)
                        .forEach(path -> collectFile(root, path));
            } catch (IOException ignored) {
                // best effort collector
            }
        }
    }

    private void collectFile(Path root, Path file) {
        try {
            String filePath = file.toAbsolutePath().normalize().toString();
            String directory = root.toString();
            long fileSize = Files.size(file);
            FileTime lastModified = Files.getLastModifiedTime(file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Instant collectedAt = Instant.now();
            DuckDbLogIndexService.FileState state = duckDbLogIndexService.findFileState(filePath);
            boolean reset = state == null
                    || fileSize < state.fileSize()
                    || lastModified.toMillis() < state.lastModifiedEpochMs()
                    || lines.size() < state.lineCount();
            int startLine = reset ? 0 : state.lineCount();

            if (reset) {
                duckDbLogIndexService.replaceFileEntries(
                        directory,
                        filePath,
                        lines,
                        fileSize,
                        lastModified.toMillis(),
                        collectedAt
                );
                return;
            }

            if (startLine < lines.size()) {
                duckDbLogIndexService.appendFileEntries(
                        directory,
                        filePath,
                        startLine,
                        lines.subList(startLine, lines.size()),
                        fileSize,
                        lastModified.toMillis(),
                        collectedAt
                );
                return;
            }

            duckDbLogIndexService.appendFileEntries(
                    directory,
                    filePath,
                    startLine,
                    List.of(),
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

    private boolean isCollectMode(top.fusb.huatuo.dx.agent.dto.LogSourceConfigView config) {
        String mode = config.mode();
        return mode != null && mode.equalsIgnoreCase("COLLECT");
    }
}
