package top.fusb.huatuo.dx.manager.config;

import top.fusb.huatuo.dx.manager.entity.UserEntity;
import top.fusb.huatuo.dx.manager.entity.UserRole;
import top.fusb.huatuo.dx.manager.repo.UserRepository;
import top.fusb.huatuo.dx.manager.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_USER_USERNAME = "user";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final ManagerProperties managerProperties;

    public UserBootstrap(UserRepository userRepository, AuthService authService, ManagerProperties managerProperties) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.managerProperties = managerProperties;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        ensureUser(DEFAULT_ADMIN_USERNAME, "管理员", managerProperties.getAuth().getDefaultAdminPassword(), UserRole.ADMIN);
        ensureUser(DEFAULT_USER_USERNAME, "普通用户", managerProperties.getAuth().getDefaultUserPassword(), UserRole.USER);
    }

    private void ensureUser(String username, String displayName, String password, UserRole role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(authService.encodePassword(password));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Bootstrapped default {} account: {}", role, username);
    }
}
