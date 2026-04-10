package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.dto.LoginRequest;
import top.fusb.huatuo.dx.manager.dto.LoginResponse;
import top.fusb.huatuo.dx.manager.dto.UserProfile;
import top.fusb.huatuo.dx.manager.entity.UserEntity;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.repo.UserRepository;
import top.fusb.huatuo.dx.manager.security.AuthContextHolder;
import top.fusb.huatuo.dx.manager.security.AuthenticatedUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final long TOKEN_EXPIRE_DAYS = 7;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, false);
    }

    @Transactional
    public LoginResponse loginForPlugin(LoginRequest request) {
        return login(request, true);
    }

    private LoginResponse login(LoginRequest request, boolean pluginSession) {
        UserEntity user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED, "当前用户已被禁用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED, "用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(TOKEN_EXPIRE_DAYS, ChronoUnit.DAYS);
        if (pluginSession) {
            user.setPluginAuthToken(token);
            user.setPluginAuthTokenExpiresAt(expiresAt);
        } else {
            user.setAuthToken(token);
            user.setAuthTokenExpiresAt(expiresAt);
        }
        UserEntity saved = userRepository.save(user);
        return new LoginResponse(token, toProfile(saved));
    }

    @Transactional(readOnly = true)
    public UserProfile currentUserProfile() {
        return toProfile(getCurrentUserEntity());
    }

    @Transactional
    public void logout() {
        UserEntity user = getCurrentUserEntity();
        user.setAuthToken(null);
        user.setAuthTokenExpiresAt(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String token) {
        UserEntity user = userRepository.findByAuthTokenAndAuthTokenExpiresAtAfter(token, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效，请重新登录"));
        return toAuthenticatedUser(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticatePlugin(String token) {
        UserEntity user = userRepository.findByPluginAuthTokenAndPluginAuthTokenExpiresAtAfter(token, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "插件登录状态已失效，请重新登录"));
        return toAuthenticatedUser(user);
    }

    private AuthenticatedUser toAuthenticatedUser(UserEntity user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED, "当前用户已被禁用");
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录");
        }
        return currentUser;
    }

    @Transactional(readOnly = true)
    public UserEntity getCurrentUserEntity() {
        AuthenticatedUser currentUser = requireCurrentUser();
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "登录状态已失效，请重新登录"));
    }

    public UserProfile toProfile(UserEntity user) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString()
        );
    }
}
