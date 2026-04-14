package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.LogCollectionStatusView;
import top.fusb.huatuo.dx.agent.dto.LogConsoleContextLine;
import top.fusb.huatuo.dx.agent.dto.LogConsoleQueryItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DuckDbLogIndexService {

    private final String jdbcUrl;
    private volatile boolean initialized;

    public DuckDbLogIndexService(HuatuoAgentProperties properties) {
        Path dbPath = Path.of(properties.getLogDuckdbPath()).toAbsolutePath().normalize();
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare DuckDB path: " + dbPath, exception);
        }
        this.jdbcUrl = "jdbc:duckdb:" + dbPath;
    }

    public synchronized FileState findFileState(String filePath) {
        String sql = """
                select file_path, directory, file_size, last_modified_epoch_ms, line_count, updated_at_epoch_ms
                from log_file_states
                where file_path = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, filePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new FileState(
                        resultSet.getString("file_path"),
                        resultSet.getString("directory"),
                        resultSet.getLong("file_size"),
                        resultSet.getLong("last_modified_epoch_ms"),
                        resultSet.getInt("line_count"),
                        resultSet.getLong("updated_at_epoch_ms")
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read log file state from DuckDB", exception);
        }
    }

    public synchronized void replaceFileEntries(
            String directory,
            String filePath,
            List<String> lines,
            long fileSize,
            long lastModifiedEpochMs,
            Instant collectedAt
    ) {
        writeFileSnapshot(directory, filePath, 0, lines, fileSize, lastModifiedEpochMs, collectedAt, true);
    }

    public synchronized void appendFileEntries(
            String directory,
            String filePath,
            int startLine,
            List<String> lines,
            long fileSize,
            long lastModifiedEpochMs,
            Instant collectedAt
    ) {
        writeFileSnapshot(directory, filePath, startLine, lines, fileSize, lastModifiedEpochMs, collectedAt, false);
    }

    public synchronized long countStatesByDirectory(String directory) {
        String sql = "select count(*) from log_file_states where directory = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, directory);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count indexed log files in DuckDB", exception);
        }
    }

    public synchronized LogCollectionStatusView collectionStatus(String directory) {
        String sql = """
                select
                    count(*) as indexed_file_count,
                    coalesce(sum(line_count), 0) as indexed_line_count,
                    max(updated_at_epoch_ms) as latest_collected_at_epoch_ms
                from log_file_states
                where directory = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, directory);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                long latest = resultSet.getLong("latest_collected_at_epoch_ms");
                return new LogCollectionStatusView(
                        directory,
                        resultSet.getLong("indexed_file_count"),
                        resultSet.getLong("indexed_line_count"),
                        resultSet.wasNull() ? null : latest
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query log collection status from DuckDB", exception);
        }
    }

    public synchronized long countEntries(String directory, QueryExpression expression, boolean tailMode) {
        QueryPlan plan = buildEntryQuery(
                "select count(*) from log_entries where directory = ?",
                directory,
                expression,
                tailMode,
                null,
                null
        );
        try (Connection connection = openConnection();
             PreparedStatement statement = prepare(connection, plan);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count log entries from DuckDB", exception);
        }
    }

    public synchronized List<LogConsoleQueryItem> queryEntries(
            String directory,
            QueryExpression expression,
            boolean tailMode,
            int page,
            int pageSize
    ) {
        QueryPlan plan = buildEntryQuery(
                """
                select file_path, line_number, content, collected_at_epoch_ms
                from log_entries
                where directory = ?
                """,
                directory,
                expression,
                tailMode,
                pageSize,
                Math.max(page - 1, 0) * pageSize
        );
        List<LogConsoleQueryItem> items = new ArrayList<>();
        try (Connection connection = openConnection();
            PreparedStatement statement = prepare(connection, plan);
            ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                items.add(new LogConsoleQueryItem(
                        resultSet.getString("file_path"),
                        resultSet.getInt("line_number"),
                        resultSet.getString("content"),
                        resultSet.getLong("collected_at_epoch_ms")
                ));
            }
            return items;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query log entries from DuckDB", exception);
        }
    }

    public synchronized TailBatch queryTailEntries(
            String directory,
            QueryExpression expression,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                select file_path, line_number, content, collected_at_epoch_ms
                from log_entries
                where directory = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(directory);
        for (String token : expression.include()) {
            sql.append(" and normalized_content like ?");
            parameters.add("%" + token + "%");
        }
        for (String token : expression.exclude()) {
            sql.append(" and normalized_content not like ?");
            parameters.add("%" + token + "%");
        }
        if (afterCollectedAtEpochMs != null) {
            sql.append("""
                     and (
                        collected_at_epoch_ms > ?
                        or (collected_at_epoch_ms = ? and file_path > ?)
                        or (collected_at_epoch_ms = ? and file_path = ? and line_number > ?)
                     )
                    """);
            parameters.add(afterCollectedAtEpochMs);
            parameters.add(afterCollectedAtEpochMs);
            parameters.add(afterFilePath == null ? "" : afterFilePath);
            parameters.add(afterCollectedAtEpochMs);
            parameters.add(afterFilePath == null ? "" : afterFilePath);
            parameters.add(afterLineNumber == null ? 0 : afterLineNumber);
        }
        sql.append(" order by collected_at_epoch_ms asc, file_path asc, line_number asc limit ?");
        parameters.add(Math.max(limit, 1));

        List<LogConsoleQueryItem> items = new ArrayList<>();
        Long latestCollectedAtEpochMs = afterCollectedAtEpochMs;
        String latestFilePath = afterFilePath;
        Integer latestLineNumber = afterLineNumber;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    latestCollectedAtEpochMs = resultSet.getLong("collected_at_epoch_ms");
                    latestFilePath = resultSet.getString("file_path");
                    latestLineNumber = resultSet.getInt("line_number");
                    items.add(new LogConsoleQueryItem(
                            latestFilePath,
                            latestLineNumber,
                            resultSet.getString("content"),
                            latestCollectedAtEpochMs
                    ));
                }
            }
            return new TailBatch(items, latestCollectedAtEpochMs, latestFilePath, latestLineNumber);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query tail log entries from DuckDB", exception);
        }
    }

    public synchronized List<LogConsoleContextLine> queryContext(String filePath, int startLine, int endLine, int hitLine) {
        String sql = """
                select line_number, content
                from log_entries
                where file_path = ?
                  and line_number between ? and ?
                order by line_number asc
                """;
        List<LogConsoleContextLine> lines = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, filePath);
            statement.setInt(2, startLine);
            statement.setInt(3, endLine);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int lineNumber = resultSet.getInt("line_number");
                    lines.add(new LogConsoleContextLine(
                            lineNumber,
                            resultSet.getString("content"),
                            lineNumber == hitLine
                    ));
                }
            }
            return lines;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query log context from DuckDB", exception);
        }
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists log_entries (
                        directory varchar not null,
                        file_path varchar not null,
                        line_number integer not null,
                        content text not null,
                        normalized_content text not null,
                        collected_at_epoch_ms bigint not null
                    )
                    """);
            statement.execute("""
                    create table if not exists log_file_states (
                        file_path varchar primary key,
                        directory varchar not null,
                        file_size bigint not null,
                        last_modified_epoch_ms bigint not null,
                        line_count integer not null,
                        updated_at_epoch_ms bigint not null
                    )
                    """);
            statement.execute("create index if not exists idx_log_entries_directory on log_entries(directory)");
            statement.execute("create index if not exists idx_log_entries_file_line on log_entries(file_path, line_number)");
            statement.execute("create index if not exists idx_log_entries_collected on log_entries(collected_at_epoch_ms)");
            statement.execute("create index if not exists idx_log_file_states_directory on log_file_states(directory)");
            initialized = true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize DuckDB log index", exception);
        }
    }

    private void writeFileSnapshot(
            String directory,
            String filePath,
            int startLine,
            List<String> lines,
            long fileSize,
            long lastModifiedEpochMs,
            Instant collectedAt,
            boolean reset
    ) {
        String deleteEntriesSql = "delete from log_entries where file_path = ?";
        String deleteStateSql = "delete from log_file_states where file_path = ?";
        String insertEntrySql = """
                insert into log_entries(directory, file_path, line_number, content, normalized_content, collected_at_epoch_ms)
                values (?, ?, ?, ?, ?, ?)
                """;
        String insertStateSql = """
                insert into log_file_states(file_path, directory, file_size, last_modified_epoch_ms, line_count, updated_at_epoch_ms)
                values (?, ?, ?, ?, ?, ?)
                """;
        long collectedAtEpochMs = collectedAt.toEpochMilli();

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (reset) {
                    try (PreparedStatement statement = connection.prepareStatement(deleteEntriesSql)) {
                        statement.setString(1, filePath);
                        statement.executeUpdate();
                    }
                }
                if (!lines.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(insertEntrySql)) {
                        for (int index = 0; index < lines.size(); index++) {
                            String line = lines.get(index);
                            statement.setString(1, directory);
                            statement.setString(2, filePath);
                            statement.setInt(3, startLine + index + 1);
                            statement.setString(4, line);
                            statement.setString(5, normalize(line));
                            statement.setLong(6, collectedAtEpochMs);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                try (PreparedStatement deleteState = connection.prepareStatement(deleteStateSql)) {
                    deleteState.setString(1, filePath);
                    deleteState.executeUpdate();
                }
                try (PreparedStatement insertState = connection.prepareStatement(insertStateSql)) {
                    insertState.setString(1, filePath);
                    insertState.setString(2, directory);
                    insertState.setLong(3, fileSize);
                    insertState.setLong(4, lastModifiedEpochMs);
                    insertState.setInt(5, startLine + lines.size());
                    insertState.setLong(6, collectedAtEpochMs);
                    insertState.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write log snapshot to DuckDB", exception);
        }
    }

    private QueryPlan buildEntryQuery(
            String baseSql,
            String directory,
            QueryExpression expression,
            boolean tailMode,
            Integer limit,
            Integer offset
    ) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> parameters = new ArrayList<>();
        parameters.add(directory);
        for (String token : expression.include()) {
            sql.append(" and normalized_content like ?");
            parameters.add("%" + token + "%");
        }
        for (String token : expression.exclude()) {
            sql.append(" and normalized_content not like ?");
            parameters.add("%" + token + "%");
        }
        if (tailMode) {
            sql.append(" and collected_at_epoch_ms >= ?");
            parameters.add(Instant.now().minusSeconds(300).toEpochMilli());
        }
        if (limit != null && offset != null) {
            sql.append(" order by collected_at_epoch_ms desc, file_path desc, line_number desc");
            sql.append(" limit ? offset ?");
            parameters.add(limit);
            parameters.add(offset);
        }
        return new QueryPlan(sql.toString(), parameters);
    }

    private PreparedStatement prepare(Connection connection, QueryPlan plan) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(plan.sql());
        for (int index = 0; index < plan.parameters().size(); index++) {
            statement.setObject(index + 1, plan.parameters().get(index));
        }
        return statement;
    }

    private Connection openConnection() throws SQLException {
        ensureInitialized();
        return DriverManager.getConnection(jdbcUrl);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    public record FileState(
            String filePath,
            String directory,
            long fileSize,
            long lastModifiedEpochMs,
            int lineCount,
            long updatedAtEpochMs
    ) {
    }

    public record QueryExpression(List<String> include, List<String> exclude) {
    }

    public record TailBatch(
            List<LogConsoleQueryItem> items,
            Long latestCollectedAtEpochMs,
            String latestFilePath,
            Integer latestLineNumber
    ) {
    }

    private record QueryPlan(String sql, List<Object> parameters) {
    }
}
