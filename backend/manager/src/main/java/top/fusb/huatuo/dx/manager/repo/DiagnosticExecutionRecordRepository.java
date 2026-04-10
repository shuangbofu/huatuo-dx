package top.fusb.huatuo.dx.manager.repo;

import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionRecord;
import top.fusb.huatuo.dx.manager.entity.DiagnosticExecutionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiagnosticExecutionRecordRepository extends JpaRepository<DiagnosticExecutionRecord, Long>, JpaSpecificationExecutor<DiagnosticExecutionRecord> {

    List<DiagnosticExecutionRecord> findTop50ByAgentNodeIdOrderByExecutedAtDesc(Long agentNodeId);

    List<DiagnosticExecutionRecord> findTop100ByOrderByExecutedAtDesc();

    List<DiagnosticExecutionRecord> findTop100ByOwnerUsernameOrderByExecutedAtDesc(String ownerUsername);

    List<DiagnosticExecutionRecord> findTop50ByOwnerUsernameAndAgentNodeIdOrderByExecutedAtDesc(String ownerUsername, Long agentNodeId);

    List<DiagnosticExecutionRecord> findByStatus(DiagnosticExecutionStatus status);

    Optional<DiagnosticExecutionRecord> findBySessionId(String sessionId);

    List<DiagnosticExecutionRecord> findBySessionIdOrderByExecutedAtDesc(String sessionId);

    long countBySessionId(String sessionId);

    Optional<DiagnosticExecutionRecord> findTopByMonitorKeyAndStatusOrderByExecutedAtDesc(String monitorKey, DiagnosticExecutionStatus status);

    Optional<DiagnosticExecutionRecord> findTopByOwnerUsernameAndMonitorKeyAndStatusOrderByExecutedAtDesc(
            String ownerUsername,
            String monitorKey,
            DiagnosticExecutionStatus status
    );

    long countBySessionIdAndStatus(String sessionId, DiagnosticExecutionStatus status);

    Optional<DiagnosticExecutionRecord> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
