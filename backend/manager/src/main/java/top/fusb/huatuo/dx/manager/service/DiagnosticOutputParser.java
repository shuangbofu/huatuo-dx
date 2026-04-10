package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.DiagnosticTriggerEventView;
import top.fusb.huatuo.dx.manager.dto.DiagnosticTraceNodeView;
import top.fusb.huatuo.dx.manager.entity.DiagnosticType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticOutputParser {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d?]*[ -/]*[@-~]");
    private static final Pattern TS_PATTERN = Pattern.compile("ts=([^;\\n]+)");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("location=([^;\\s]+)");
    private static final Pattern THREAD_PATTERN = Pattern.compile("thread_name=([^;\\n]+)");
    private static final Pattern COST_PATTERN = Pattern.compile("cost=([0-9]+(?:\\.[0-9]+)?)ms");
    private static final Pattern TRACE_COST_PATTERN = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)ms\\]");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method=([^;\\n]+)");
    private static final String ARTHAS_BACKGROUND_MESSAGE = "arthas is still running in the background.";

    public ParsedDiagnosticOutput parse(DiagnosticType type, String rawOutput) {
        String output = sanitize(rawOutput);
        if (output.isBlank()) {
            return new ParsedDiagnosticOutput(0, null, List.of());
        }
        List<DiagnosticTriggerEventView> events = (type == DiagnosticType.TRACE || type == DiagnosticType.STACK)
                ? splitTraceEvents(output)
                : splitIntoEvents(type, output);
        Double maxCostMs = events.stream()
                .map(DiagnosticTriggerEventView::costMs)
                .filter(value -> value != null)
                .max(Double::compareTo)
                .orElse(null);
        return new ParsedDiagnosticOutput(events.size(), maxCostMs, events);
    }

    private List<DiagnosticTriggerEventView> splitIntoEvents(DiagnosticType type, String output) {
        List<String> lines = output.lines().toList();
        List<DiagnosticTriggerEventView> events = new ArrayList<>();
        List<String> current = new ArrayList<>();
        List<String> pendingHeader = new ArrayList<>();
        int sequence = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!current.isEmpty()) {
                    current.add(line);
                }
                continue;
            }
            if (isPromptLine(trimmed) || isNoiseLine(trimmed)) {
                if (!current.isEmpty()) {
                    events.add(toEvent(type, sequence++, current));
                    current = new ArrayList<>();
                }
                pendingHeader.clear();
                continue;
            }
            if (isEventAnchor(type, trimmed)) {
                if (!current.isEmpty()) {
                    events.add(toEvent(type, sequence++, current));
                    current = new ArrayList<>();
                }
                current.addAll(pendingHeader);
                current.add(line);
                pendingHeader.clear();
                continue;
            }
            if (current.isEmpty()) {
                if (!isNoiseLine(trimmed)) {
                    pendingHeader.add(line);
                    if (pendingHeader.size() > 3) {
                        pendingHeader.remove(0);
                    }
                }
                continue;
            }
            current.add(line);
        }

        if (!current.isEmpty()) {
            events.add(toEvent(type, sequence, current));
        } else if (!pendingHeader.isEmpty() && pendingHeader.stream().anyMatch(line -> !isNoiseLine(line.trim()))) {
            events.add(toEvent(type, sequence, pendingHeader));
        }
        return events;
    }

    private List<DiagnosticTriggerEventView> splitTraceEvents(String output) {
        List<DiagnosticTriggerEventView> events = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int sequence = 1;

        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!current.isEmpty()) {
                    current.add(line);
                }
                continue;
            }
            if (isPromptLine(trimmed)) {
                if (!current.isEmpty()) {
                    events.add(toEvent(DiagnosticType.TRACE, sequence++, current));
                    current = new ArrayList<>();
                }
                continue;
            }
            if (trimmed.contains("ts=")) {
                if (!current.isEmpty()) {
                    events.add(toEvent(DiagnosticType.TRACE, sequence++, current));
                    current = new ArrayList<>();
                }
                current.add(line);
                continue;
            }
            if (current.isEmpty()) {
                continue;
            }
            current.add(line);
        }

        if (!current.isEmpty()) {
            events.add(toEvent(DiagnosticType.TRACE, sequence, current));
        }
        return events;
    }

    private DiagnosticTriggerEventView toEvent(DiagnosticType type, int sequence, List<String> blockLines) {
        String raw = String.join("\n", blockLines).trim();
        String timestamp = matchFirst(TS_PATTERN, raw);
        String location = matchFirst(LOCATION_PATTERN, raw);
        String threadName = matchFirst(THREAD_PATTERN, raw);
        Double costMs = matchDouble(COST_PATTERN, raw);
        if (costMs == null && (type == DiagnosticType.TRACE || type == DiagnosticType.STACK)) {
            costMs = matchDouble(TRACE_COST_PATTERN, raw);
        }
        String summary = buildSummary(type, raw, location);
        String methodName = type == DiagnosticType.WATCH ? matchFirst(METHOD_PATTERN, raw) : null;
        String watchBody = type == DiagnosticType.WATCH ? extractWatchBody(raw) : null;
        List<DiagnosticTraceNodeView> traceNodes = type == DiagnosticType.TRACE
                ? parseTraceNodes(raw)
                : type == DiagnosticType.STACK ? parseStackNodes(raw) : List.of();
        String title = type == DiagnosticType.WATCH
                ? "第 " + sequence + " 次命中"
                : type == DiagnosticType.TRACE ? "第 " + sequence + " 次链路" : "第 " + sequence + " 次堆栈";
        return new DiagnosticTriggerEventView(sequence, title, timestamp, location, threadName, costMs, summary, raw, methodName, watchBody, traceNodes);
    }

    private String buildSummary(DiagnosticType type, String raw, String location) {
        for (String line : raw.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isPromptLine(trimmed)) {
                continue;
            }
            if (trimmed.contains("ts=") || trimmed.startsWith("method=") || trimmed.startsWith("Affect(")) {
                continue;
            }
            if (type == DiagnosticType.TRACE && trimmed.contains("[") && trimmed.contains("ms]")) {
                return trimmed;
            }
            return trimmed.length() > 160 ? trimmed.substring(0, 160) + "..." : trimmed;
        }
        return location != null && !location.isBlank() ? location : "已捕获一次触发结果";
    }

    private boolean isEventAnchor(DiagnosticType type, String line) {
        if (line.contains("ts=") && (line.contains("cost=") || line.contains("thread_name=") || line.contains("result=") || line.contains("trace_id"))) {
            return true;
        }
        if (type == DiagnosticType.TRACE) {
            return false;
        }
        return false;
    }

    private boolean isPromptLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("[arthas@") || lower.endsWith("$") || lower.startsWith("affect(");
    }

    private boolean isNoiseLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("as.sh")
                || lower.contains("arthas-agent")
                || lower.contains("session")
                || lower.startsWith("thanks for using arthas")
                || lower.startsWith(ARTHAS_BACKGROUND_MESSAGE)
                || lower.startsWith("to completely shutdown arthas")
                || lower.startsWith("please execute the 'stop' command");
    }

    private String extractWatchBody(String raw) {
        int resultIndex = raw.indexOf("result=");
        if (resultIndex >= 0) {
            return raw.substring(resultIndex + "result=".length()).trim();
        }
        List<String> lines = raw.lines().toList();
        int bodyStart = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()
                    || line.contains("ts=")
                    || line.contains("location=")
                    || line.contains("thread_name=")
                    || line.contains("cost=")
                    || line.startsWith("method=")
                    || line.startsWith("Affect(")) {
                continue;
            }
            bodyStart = index;
            break;
        }
        return bodyStart >= 0 ? String.join("\n", lines.subList(bodyStart, lines.size())).trim() : raw.trim();
    }

    private List<DiagnosticTraceNodeView> parseTraceNodes(String raw) {
        List<DiagnosticTraceNodeView> nodes = new ArrayList<>();
        Pattern linePattern = Pattern.compile("^(\\s*(?:`\\+---|\\+---|`---|---|\\| )*)(?:\\[([0-9]+(?:\\.[0-9]+)?)ms\\]\\s*)?(.+)$");
        for (String line : raw.lines().toList()) {
            Matcher matcher = linePattern.matcher(line.replace("\r", ""));
            if (!matcher.matches()) {
                continue;
            }
            String label = matcher.group(3) == null ? "" : matcher.group(3).trim();
            if (label.isEmpty()
                    || label.startsWith("ts=")
                    || label.startsWith("thread_name=")
                    || label.startsWith("Affect(")) {
                continue;
            }
            String prefix = matcher.group(1) == null ? "" : matcher.group(1);
            int depth = Math.max(countOccurrences(prefix, "---") - 1, 0);
            Double nodeCost = matcher.group(2) == null ? null : parseDouble(matcher.group(2));
            nodes.add(new DiagnosticTraceNodeView(depth, nodeCost, label));
        }
        return nodes;
    }

    private List<DiagnosticTraceNodeView> parseStackNodes(String raw) {
        List<DiagnosticTraceNodeView> nodes = new ArrayList<>();
        for (String line : raw.lines().toList()) {
            String sanitized = line.replace("\r", "");
            String trimmed = sanitized.trim();
            if (trimmed.isEmpty()
                    || trimmed.startsWith("ts=")
                    || trimmed.startsWith("thread_name=")
                    || trimmed.startsWith("Affect(")
                    || isPromptLine(trimmed)
                    || isNoiseLine(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("method=")) {
                nodes.add(new DiagnosticTraceNodeView(0, null, trimmed));
                continue;
            }
            if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) {
                nodes.add(new DiagnosticTraceNodeView(0, null, trimmed));
                continue;
            }
            if (trimmed.startsWith("at ") || trimmed.startsWith("at\t") || trimmed.startsWith("... ")) {
                nodes.add(new DiagnosticTraceNodeView(1, null, trimmed));
                continue;
            }
            int leadingSpaces = sanitized.length() - sanitized.stripLeading().length();
            int depth = leadingSpaces > 0 ? 1 : 0;
            nodes.add(new DiagnosticTraceNodeView(depth, null, trimmed));
        }
        return nodes;
    }

    private int countOccurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String sanitize(String rawOutput) {
        if (rawOutput == null) {
            return "";
        }
        return ANSI_PATTERN.matcher(rawOutput).replaceAll("").replace("\r", "");
    }

    private String matchFirst(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Double matchDouble(Pattern pattern, String input) {
        String value = matchFirst(pattern, input);
        return parseDouble(value);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record ParsedDiagnosticOutput(
            int triggerCount,
            Double maxCostMs,
            List<DiagnosticTriggerEventView> triggerEvents
    ) {
    }
}
