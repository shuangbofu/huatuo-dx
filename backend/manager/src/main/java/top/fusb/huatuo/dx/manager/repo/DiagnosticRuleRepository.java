package top.fusb.huatuo.dx.manager.repo;

import top.fusb.huatuo.dx.manager.entity.DiagnosticRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiagnosticRuleRepository extends JpaRepository<DiagnosticRule, Long>, JpaSpecificationExecutor<DiagnosticRule> {

    List<DiagnosticRule> findAllByOrderByUpdatedAtDesc();

    List<DiagnosticRule> findByOwnerUsernameOrderByUpdatedAtDesc(String ownerUsername);

    List<DiagnosticRule> findByEnabledTrueOrderByUpdatedAtDesc();

    Optional<DiagnosticRule> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
