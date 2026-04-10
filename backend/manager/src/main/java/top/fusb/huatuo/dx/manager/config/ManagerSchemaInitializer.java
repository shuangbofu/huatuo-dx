package top.fusb.huatuo.dx.manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ManagerSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ManagerSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        relaxDiagnosticRuleNodeBinding();
        relaxDiagnosticRuleTypeConstraint();
        ensureUserColumns();
        ensureDiagnosticRuleColumns();
        ensureDiagnosticExecutionRecordColumns();
    }

    private void ensureUserColumns() {
        addColumnIfNeeded("alter table users add column if not exists auth_token varchar(255)");
        addColumnIfNeeded("alter table users add column if not exists auth_token_expires_at timestamp");
        addColumnIfNeeded("alter table users add column if not exists plugin_auth_token varchar(255)");
        addColumnIfNeeded("alter table users add column if not exists plugin_auth_token_expires_at timestamp");
    }

    private void relaxDiagnosticRuleNodeBinding() {
        try {
            jdbcTemplate.execute("alter table diagnostic_rules alter column agent_node_id drop not null");
        } catch (Exception exception) {
            log.warn("Failed to relax diagnostic_rules.agent_node_id nullability: {}", exception.getMessage());
        }
    }

    private void relaxDiagnosticRuleTypeConstraint() {
        try {
            jdbcTemplate.execute("alter table diagnostic_rules alter column type varchar(32)");
        } catch (Exception exception) {
            log.warn("Failed to relax diagnostic_rules.type constraint: {}", exception.getMessage());
        }
    }

    private void ensureDiagnosticRuleColumns() {
        addColumnIfNeeded("alter table diagnostic_rules add column if not exists owner_username varchar(100) default 'admin' not null");
        addColumnIfNeeded("alter table diagnostic_rules add column if not exists owner_display_name varchar(100) default '管理员' not null");
        addColumnIfNeeded("alter table diagnostic_rules add column if not exists command_options clob");
        updateIfNeeded("update diagnostic_rules set owner_username = 'admin' where owner_username is null");
        updateIfNeeded("update diagnostic_rules set owner_display_name = '管理员' where owner_display_name is null");
    }

    private void ensureDiagnosticExecutionRecordColumns() {
        relaxDiagnosticExecutionRecordTypeConstraint();
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists session_id varchar(64)");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists status varchar(32) default 'COMPLETED' not null");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists updated_at timestamp default current_timestamp not null");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists trigger_count integer");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists max_cost_ms double");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists trigger_events_json clob");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists owner_username varchar(100) default 'admin' not null");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists owner_display_name varchar(100) default '管理员' not null");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists agent_node_name varchar(100)");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists process_display_name varchar(255)");
        addColumnIfNeeded("alter table diagnostic_execution_records add column if not exists monitor_key varchar(1000)");
        updateIfNeeded("update diagnostic_execution_records set status = 'COMPLETED' where status is null");
        updateIfNeeded("update diagnostic_execution_records set updated_at = executed_at where updated_at is null");
        updateIfNeeded("update diagnostic_execution_records set trigger_count = 0 where trigger_count is null");
        updateIfNeeded("update diagnostic_execution_records set owner_username = 'admin' where owner_username is null");
        updateIfNeeded("update diagnostic_execution_records set owner_display_name = '管理员' where owner_display_name is null");
    }

    private void relaxDiagnosticExecutionRecordTypeConstraint() {
        try {
            jdbcTemplate.execute("alter table diagnostic_execution_records alter column type varchar(32)");
        } catch (Exception exception) {
            log.warn("Failed to relax diagnostic_execution_records.type constraint: {}", exception.getMessage());
        }
    }

    private void addColumnIfNeeded(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception exception) {
            log.warn("Failed to execute schema update [{}]: {}", sql, exception.getMessage());
        }
    }

    private void updateIfNeeded(String sql) {
        try {
            jdbcTemplate.update(sql);
        } catch (Exception exception) {
            log.warn("Failed to execute schema data repair [{}]: {}", sql, exception.getMessage());
        }
    }
}
