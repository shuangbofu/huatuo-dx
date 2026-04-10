package top.fusb.huatuo.dx.manager.repo;

import top.fusb.huatuo.dx.manager.entity.UserEntity;
import top.fusb.huatuo.dx.manager.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    List<UserEntity> findAllByOrderByUpdatedAtDesc();

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByAuthTokenAndAuthTokenExpiresAtAfter(String authToken, Instant now);

    Optional<UserEntity> findByPluginAuthTokenAndPluginAuthTokenExpiresAtAfter(String pluginAuthToken, Instant now);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);
}
