package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.dto.ProcessView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProcessDiscoveryService {

    public List<ProcessView> findProcesses(String pattern) {
        String normalized = pattern == null ? "" : pattern.toLowerCase();
        return discoverProcesses().values().stream()
                .filter(process -> matches(process, normalized))
                .sorted(Comparator.comparingLong(ProcessView::pid))
                .toList();
    }

    public Optional<ProcessView> findBestMatch(String pattern) {
        return findProcesses(pattern).stream().findFirst();
    }

    public Optional<ProcessView> findByPid(Long pid) {
        if (pid == null) {
            return Optional.empty();
        }
        return discoverProcesses().values().stream()
                .filter(process -> process.pid() == pid)
                .findFirst();
    }

    private ProcessView toView(ProcessHandle handle) {
        ProcessHandle.Info info = handle.info();
        String command = info.command().orElse("");
        String commandLine = info.commandLine().orElse(command);
        String displayName = commandLine.isBlank() ? command : commandLine;
        String user = info.user().orElse("");
        return new ProcessView(handle.pid(), displayName, command, commandLine, user);
    }

    private boolean matches(ProcessView process, String pattern) {
        if (pattern.isBlank()) {
            return true;
        }
        return process.displayName().toLowerCase().contains(pattern)
                || process.commandLine().toLowerCase().contains(pattern)
                || process.command().toLowerCase().contains(pattern);
    }

    private Map<Long, ProcessView> discoverProcesses() {
        Map<Long, ProcessView> processes = readFromJps();
        if (!processes.isEmpty()) {
            return enrichWithProcessHandle(processes);
        }
        return readFromPs();
    }

    private Map<Long, ProcessView> readFromJps() {
        try {
            Process process = new ProcessBuilder("jps", "-l").start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .map(this::parseJpsLine)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toMap(ProcessView::pid, view -> view, (left, right) -> left, LinkedHashMap::new));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Optional<ProcessView> parseJpsLine(String line) {
        int splitIndex = line.indexOf(' ');
        if (splitIndex <= 0) {
            return Optional.empty();
        }
        try {
            long pid = Long.parseLong(line.substring(0, splitIndex).trim());
            String name = line.substring(splitIndex + 1).trim();
            return Optional.of(new ProcessView(pid, name, "java", "java " + name, ""));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Map<Long, ProcessView> enrichWithProcessHandle(Map<Long, ProcessView> jpsProcesses) {
        Map<Long, ProcessView> enriched = new LinkedHashMap<>(jpsProcesses);
        ProcessHandle.allProcesses()
                .map(this::toView)
                .filter(process -> enriched.containsKey(process.pid()))
                .forEach(process -> enriched.computeIfPresent(process.pid(), (pid, current) -> merge(current, process)));
        return enriched;
    }

    private Map<Long, ProcessView> readFromPs() {
        try {
            Process process = new ProcessBuilder("ps", "-ef").start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .skip(1)
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .map(this::parsePsLine)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toMap(ProcessView::pid, view -> view, (left, right) -> left, LinkedHashMap::new));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Optional<ProcessView> parsePsLine(String line) {
        String[] parts = line.split("\\s+", 8);
        if (parts.length < 8) {
            return Optional.empty();
        }
        String user = parts[0];
        String pidText = parts[1];
        String commandLine = parts[7];
        String normalized = commandLine.toLowerCase();
        if (!normalized.contains("java") || normalized.contains("grep java")) {
            return Optional.empty();
        }
        try {
            long pid = Long.parseLong(pidText);
            String displayName = commandLine;
            String command = commandLine.split("\\s+", 2)[0];
            return Optional.of(new ProcessView(pid, displayName, command, commandLine, user));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private ProcessView merge(ProcessView primary, ProcessView fallback) {
        return new ProcessView(
                primary.pid(),
                choose(primary.displayName(), fallback.displayName()),
                choose(primary.command(), fallback.command()),
                choose(primary.commandLine(), fallback.commandLine()),
                choose(primary.user(), fallback.user())
        );
    }

    private String choose(String preferred, String alternate) {
        return preferred != null && !preferred.isBlank() ? preferred : alternate;
    }
}
