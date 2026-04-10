package top.fusb.huatuo.dx.manager.repo;

import top.fusb.huatuo.dx.manager.entity.AgentNode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AgentNodeRepository extends JpaRepository<AgentNode, Long>, JpaSpecificationExecutor<AgentNode> {

    Optional<AgentNode> findByNodeCode(String nodeCode);

    Optional<AgentNode> findByBaseUrl(String baseUrl);
}
