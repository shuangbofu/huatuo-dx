package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.LogContentView;
import top.fusb.huatuo.dx.agent.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.agent.dto.LogConsoleContextLine;
import top.fusb.huatuo.dx.agent.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.agent.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.agent.dto.LogFileView;
import top.fusb.huatuo.dx.agent.dto.LogTailResponse;
import top.fusb.huatuo.dx.agent.dto.LogSourceConfigView;
import top.fusb.huatuo.dx.agent.dto.LogConsoleQueryItem;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class LogService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]+)\"|(\\S+)");

    private final HuatuoAgentProperties properties;
    private final LogCollectorService logCollectorService;
    private final DuckDbLogIndexService duckDbLogIndexService;

    public LogService(
            HuatuoAgentProperties properties,
            LogCollectorService logCollectorService,
            DuckDbLogIndexService duckDbLogIndexService
    ) {
        this.properties = properties;
        this.logCollectorService = logCollectorService;
        this.duckDbLogIndexService = duckDbLogIndexService;
    }

    public List<LogFileView> list(String currentPath) {
        if (currentPath == null || currentPath.isBlank()) {
            return properties.getLogDirectories().stream()
                    .map(Path::of)
                    .map(this::toView)
                    .sorted(Comparator.comparing(LogFileView::name))
                    .toList();
        }
        Path path = safePath(currentPath);
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(path)) {
            return stream
                    .map(this::toView)
                    .sorted(Comparator.comparing(LogFileView::directory).reversed().thenComparing(LogFileView::name))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list logs: " + path, exception);
        }
    }

    public LogContentView read(String filePath, String keyword, Integer limit) {
        Path path = safePath(filePath);
        if (Files.isDirectory(path)) {
            throw new BusinessException(ErrorCode.LOG_FILE_IS_DIRECTORY, "日志路径是目录，不能直接读取: " + filePath);
        }
        int maxLines = limit == null || limit < 1 ? 200 : limit;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Stream<String> stream = lines.stream();
            if (keyword != null && !keyword.isBlank()) {
                stream = stream.filter(line -> line.contains(keyword));
            }
            List<String> filtered = stream.toList();
            int from = Math.max(0, filtered.size() - maxLines);
            List<String> output = filtered.subList(from, filtered.size());
            return new LogContentView(path.toString(), String.join("\n", output), output.size(), filtered.size() > maxLines);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read log: " + filePath, exception);
        }
    }

    public byte[] download(String filePath) {
        Path path = safePath(filePath);
        if (Files.isDirectory(path)) {
            throw new BusinessException(ErrorCode.LOG_FILE_IS_DIRECTORY, "日志路径是目录，不能直接下载: " + filePath);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to download log: " + filePath, exception);
        }
    }

    public LogConsoleQueryResponse query(String directory, String keyword, Integer page, Integer pageSize, Boolean tailMode) {
        Path root = safePath(directory);
        int currentPage = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 200);
        boolean tailing = Boolean.TRUE.equals(tailMode);
        if (isCollectMode(root.toString())) {
            DuckDbLogIndexService.QueryExpression expression = tailing ? parseOptionalKeyword(keyword) : parseKeyword(keyword);
            ensureCollected(root.toString());
            long total = duckDbLogIndexService.countEntries(root.toString(), expression, tailing);
            int scannedFiles = (int) duckDbLogIndexService.countStatesByDirectory(root.toString());
            return new LogConsoleQueryResponse(
                    total,
                    currentPage,
                    size,
                    scannedFiles,
                    duckDbLogIndexService.queryEntries(root.toString(), expression, tailing, currentPage, size)
            );
        }
        return queryDirect(root, keyword, currentPage, size, tailing);
    }

    public LogConsoleContextResponse context(String directory, String filePath, Integer lineNumber, Integer beforeLines, Integer afterLines) {
        safePath(directory);
        Path path = safePath(filePath);
        int hitLine = lineNumber == null || lineNumber < 1 ? 1 : lineNumber;
        int before = beforeLines == null ? 10 : beforeLines;
        int after = afterLines == null ? 10 : afterLines;
        if (!isCollectMode(directory)) {
            return directContext(path, hitLine, before, after);
        }
        ensureCollected(directory);
        int start = Math.max(1, hitLine - before);
        int end = hitLine + after;
        List<LogConsoleContextLine> context = duckDbLogIndexService.queryContext(path.toString(), start, end, hitLine);
        int endLine = context.isEmpty() ? hitLine : context.get(context.size() - 1).lineNumber();
        return new LogConsoleContextResponse(path.toString(), hitLine, start, endLine, context);
    }

    public LogCollectionStatusView collectionStatus(String directory) {
        Path root = safePath(directory);
        if (!isCollectMode(root.toString())) {
            return new LogCollectionStatusView(root.toString(), 0, 0, null);
        }
        ensureCollected(root.toString());
        return duckDbLogIndexService.collectionStatus(root.toString());
    }

    public LogTailResponse tail(
            String directory,
            String keyword,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber,
            Integer limit
    ) {
        Path root = safePath(directory);
        int batchLimit = limit == null ? 200 : Math.min(limit, 500);
        if (isCollectMode(root.toString())) {
            ensureCollected(root.toString());
            DuckDbLogIndexService.QueryExpression expression = parseOptionalKeyword(keyword);
            DuckDbLogIndexService.TailBatch batch = duckDbLogIndexService.queryTailEntries(
                    root.toString(),
                    expression,
                    afterCollectedAtEpochMs,
                    afterFilePath,
                    afterLineNumber,
                    batchLimit
            );
            return new LogTailResponse(
                    batch.items(),
                    batch.latestCollectedAtEpochMs(),
                    batch.latestFilePath(),
                    batch.latestLineNumber()
            );
        }
        return tailDirect(root, keyword, afterCollectedAtEpochMs, afterFilePath, afterLineNumber, batchLimit);
    }

    private void ensureCollected(String directory) {
        if (duckDbLogIndexService.countStatesByDirectory(directory) == 0) {
            logCollectorService.collect();
        }
    }

    private boolean isCollectMode(String directory) {
        return properties.getLogSourceConfigs().stream()
                .filter(config -> config.path() != null && !config.path().isBlank())
                .anyMatch(config -> Path.of(config.path()).toAbsolutePath().normalize().toString().equals(directory)
                        && "COLLECT".equalsIgnoreCase(config.mode()));
    }

    private LogFileView toView(Path path) {
        try {
            FileTime modified = Files.exists(path) ? Files.getLastModifiedTime(path) : FileTime.fromMillis(0);
            long size = Files.exists(path) && !Files.isDirectory(path) ? Files.size(path) : 0L;
            return new LogFileView(
                    path.toAbsolutePath().normalize().toString(),
                    path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                    size,
                    Files.isDirectory(path),
                    FORMATTER.format(modified.toInstant())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect log path: " + path, exception);
        }
    }

    private Path safePath(String rawPath) {
        Path requested = Path.of(rawPath).toAbsolutePath().normalize();
        List<Path> configuredRoots = properties.getLogDirectories().stream()
                .map(root -> Path.of(root).toAbsolutePath().normalize())
                .toList();
        boolean insideConfigured = configuredRoots.stream()
                .anyMatch(root -> requested.equals(root) || requested.startsWith(root));
        if (!insideConfigured) {
            String configuredText = configuredRoots.isEmpty()
                    ? "当前节点还没有配置任何日志目录"
                    : "当前允许的日志目录: " + configuredRoots.stream().map(Path::toString).reduce((left, right) -> left + " | " + right).orElse("");
            throw new BusinessException(
                    ErrorCode.LOG_PATH_OUTSIDE_ROOT,
                    "当前选择的日志路径不在该节点已配置的日志目录内，请先到节点日志采集配置中添加对应目录。"
                            + " 请求路径: " + requested
                            + "。"
                            + configuredText
            );
        }
        if (!Files.exists(requested)) {
            throw new BusinessException(ErrorCode.LOG_PATH_NOT_FOUND, "日志路径不存在: " + requested);
        }
        return requested;
    }

    private DuckDbLogIndexService.QueryExpression parseKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.KEYWORD_REQUIRED, "请输入关键词");
        }
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(keyword.trim());
        while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("-") && token.length() > 1) {
                exclude.add(token.substring(1).toLowerCase(Locale.ROOT));
            } else {
                include.add(token.toLowerCase(Locale.ROOT));
            }
        }
        if (include.isEmpty()) {
            throw new BusinessException(ErrorCode.KEYWORD_REQUIRED, "请输入关键词");
        }
        return new DuckDbLogIndexService.QueryExpression(include, exclude);
    }

    private DuckDbLogIndexService.QueryExpression parseOptionalKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new DuckDbLogIndexService.QueryExpression(List.of(), List.of());
        }
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(keyword.trim());
        while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("-") && token.length() > 1) {
                exclude.add(token.substring(1).toLowerCase(Locale.ROOT));
            } else {
                include.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return new DuckDbLogIndexService.QueryExpression(include, exclude);
    }

    private LogConsoleQueryResponse queryDirect(Path root, String keyword, int page, int pageSize, boolean tailing) {
        DuckDbLogIndexService.QueryExpression expression = tailing ? parseOptionalKeyword(keyword) : parseKeyword(keyword);
        List<LogConsoleQueryItem> items = scanDirectItems(root, expression, tailing);
        int total = items.size();
        int fromIndex = Math.min(Math.max((page - 1) * pageSize, 0), total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return new LogConsoleQueryResponse((long) total, page, pageSize, countDirectLogFiles(root), items.subList(fromIndex, toIndex));
    }

    private LogConsoleContextResponse directContext(Path path, int hitLine, int before, int after) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int start = Math.max(1, hitLine - before);
            int end = Math.min(lines.size(), hitLine + after);
            List<LogConsoleContextLine> context = new ArrayList<>();
            for (int current = start; current <= end; current += 1) {
                context.add(new LogConsoleContextLine(current, lines.get(current - 1), current == hitLine));
            }
            return new LogConsoleContextResponse(path.toString(), hitLine, start, end, context);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read log context: " + path, exception);
        }
    }

    private LogTailResponse tailDirect(
            Path root,
            String keyword,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber,
            int limit
    ) {
        DuckDbLogIndexService.QueryExpression expression = parseOptionalKeyword(keyword);
        List<LogConsoleQueryItem> items = scanDirectItems(root, expression, true).stream()
                .filter(item -> isAfter(item, afterCollectedAtEpochMs, afterFilePath, afterLineNumber))
                .limit(limit)
                .toList();
        if (items.isEmpty()) {
            return new LogTailResponse(List.of(), afterCollectedAtEpochMs, afterFilePath, afterLineNumber);
        }
        LogConsoleQueryItem latest = items.get(items.size() - 1);
        return new LogTailResponse(items, latest.collectedAtEpochMs(), latest.filePath(), latest.lineNumber());
    }

    private boolean isAfter(LogConsoleQueryItem item, Long afterCollectedAtEpochMs, String afterFilePath, Integer afterLineNumber) {
        if (afterCollectedAtEpochMs == null) {
            return true;
        }
        if (item.collectedAtEpochMs() > afterCollectedAtEpochMs) {
            return true;
        }
        if (item.collectedAtEpochMs() < afterCollectedAtEpochMs) {
            return false;
        }
        if (afterFilePath == null) {
            return true;
        }
        int fileCompare = item.filePath().compareTo(afterFilePath);
        if (fileCompare > 0) {
            return true;
        }
        if (fileCompare < 0) {
            return false;
        }
        return afterLineNumber == null || item.lineNumber() > afterLineNumber;
    }

    private int countDirectLogFiles(Path root) {
        try (Stream<Path> stream = Files.isRegularFile(root) ? Stream.of(root) : Files.walk(root)) {
            return (int) stream.filter(Files::isRegularFile).filter(this::isLogFile).count();
        } catch (IOException exception) {
            return 0;
        }
    }

    private List<LogConsoleQueryItem> scanDirectItems(Path root, DuckDbLogIndexService.QueryExpression expression, boolean tailing) {
        List<LogConsoleQueryItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.isRegularFile(root) ? Stream.of(root) : Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(this::isLogFile)
                    .sorted()
                    .toList();
            for (Path file : files) {
                long collectedAt = Files.getLastModifiedTime(file).toMillis();
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i += 1) {
                    String line = lines.get(i);
                    if (!matchesExpression(line, expression)) {
                        continue;
                    }
                    items.add(new LogConsoleQueryItem(file.toString(), i + 1, line, collectedAt));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to query logs directly: " + root, exception);
        }
        items.sort(Comparator.comparingLong(LogConsoleQueryItem::collectedAtEpochMs)
                .thenComparing(LogConsoleQueryItem::filePath)
                .thenComparingInt(LogConsoleQueryItem::lineNumber));
        if (!tailing) {
            items.sort(Comparator.comparingLong(LogConsoleQueryItem::collectedAtEpochMs)
                    .thenComparing(LogConsoleQueryItem::filePath)
                    .thenComparingInt(LogConsoleQueryItem::lineNumber));
            java.util.Collections.reverse(items);
        }
        return items;
    }

    private boolean matchesExpression(String line, DuckDbLogIndexService.QueryExpression expression) {
        String normalized = line.toLowerCase(Locale.ROOT);
        boolean includes = expression.include().isEmpty() || expression.include().stream().allMatch(normalized::contains);
        boolean excludes = expression.exclude().stream().noneMatch(normalized::contains);
        return includes && excludes;
    }

    private boolean isLogFile(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".log") || fileName.contains(".log.");
    }
}
