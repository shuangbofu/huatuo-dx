package top.fusb.huatuo.dx.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import top.fusb.huatuo.dx.agent.dto.LogSourceConfigView;
import org.springframework.stereotype.Component;

@Component
public class LogEventAssembler {

    private static final Pattern DEFAULT_TIMESTAMP_HEADER = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3,9})?.*$"
    );

    public List<LogEvent> assemble(List<String> lines, LogSourceConfigView config) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Pattern headerPattern = resolveHeaderPattern(config);
        if (headerPattern == null) {
            List<LogEvent> items = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i += 1) {
                items.add(new LogEvent(i + 1, lines.get(i)));
            }
            return items;
        }

        List<LogEvent> events = new ArrayList<>();
        int currentStartLine = 1;
        StringBuilder currentContent = new StringBuilder();

        for (int index = 0; index < lines.size(); index += 1) {
            String line = lines.get(index);
            boolean header = headerPattern.matcher(line).matches();
            if (header && currentContent.length() > 0) {
                events.add(new LogEvent(currentStartLine, currentContent.toString()));
                currentContent.setLength(0);
                currentStartLine = index + 1;
            }
            if (currentContent.length() == 0) {
                currentStartLine = index + 1;
                currentContent.append(line);
            } else {
                currentContent.append('\n').append(line);
            }
        }

        if (currentContent.length() > 0) {
            events.add(new LogEvent(currentStartLine, currentContent.toString()));
        }
        return events;
    }

    private Pattern resolveHeaderPattern(LogSourceConfigView config) {
        if (config != null && "LOGBACK_PATTERN".equalsIgnoreCase(config.parseMode())) {
            Pattern logbackPattern = buildLogbackHeaderPattern(config.parsePattern());
            if (logbackPattern != null) {
                return logbackPattern;
            }
        }
        return DEFAULT_TIMESTAMP_HEADER;
    }

    private Pattern buildLogbackHeaderPattern(String parsePattern) {
        if (parsePattern == null || parsePattern.isBlank()) {
            return null;
        }
        StringBuilder regex = new StringBuilder("^");
        int index = 0;
        while (index < parsePattern.length()) {
            char current = parsePattern.charAt(index);
            if (current != '%') {
                if (Character.isWhitespace(current)) {
                    regex.append("\\s+");
                } else {
                    regex.append(Pattern.quote(String.valueOf(current)));
                }
                index += 1;
                continue;
            }

            int tokenStart = index;
            index += 1;
            while (index < parsePattern.length()) {
                char ch = parsePattern.charAt(index);
                if (Character.isLetter(ch)) {
                    index += 1;
                    break;
                }
                index += 1;
            }
            String token = parsePattern.substring(tokenStart, Math.min(index, parsePattern.length()));
            if (token.startsWith("%msg") || token.startsWith("%m")) {
                break;
            }
            if (token.startsWith("%n")) {
                continue;
            }
            regex.append(tokenPattern(token));
        }
        regex.append(".*$");
        return Pattern.compile(regex.toString());
    }

    private String tokenPattern(String token) {
        if (token.startsWith("%d")) {
            return "\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3,9})?";
        }
        if (token.startsWith("%p") || token.contains("level")) {
            return "\\S+";
        }
        if (token.contains("thread")) {
            return ".+?";
        }
        if (token.contains("logger") || token.contains("class") || token.contains("method")) {
            return ".+?";
        }
        return ".+?";
    }

    public record LogEvent(int lineNumber, String content) {
    }
}
