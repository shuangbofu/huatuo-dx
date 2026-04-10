package top.fusb.huatuo.dx.manager.service;

import java.util.List;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fusb.huatuo.dx.manager.config.ManagerProperties;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.dto.UserPayload;
import top.fusb.huatuo.dx.manager.dto.UserProfile;
import top.fusb.huatuo.dx.manager.entity.UserEntity;
import top.fusb.huatuo.dx.manager.entity.UserRole;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.repo.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final ManagerProperties managerProperties;

    public UserService(UserRepository userRepository, AuthService authService, ManagerProperties managerProperties) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.managerProperties = managerProperties;
    }

    @Transactional(readOnly = true)
    public List<UserProfile> list() {
        return userRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(authService::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<UserProfile> listPage(String keyword, UserRole role, Boolean enabled, int page, int pageSize) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = userRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
            if (!normalizedKeyword.isEmpty()) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("displayName")), like)
                ));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
        return new PageResult<>(
                result.getContent().stream().map(authService::toProfile).toList(),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Transactional
    public UserProfile create(UserPayload payload) {
        String username = normalizeUsername(payload.username());
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setDisplayName(payload.displayName().trim());
        entity.setRole(payload.role());
        entity.setEnabled(payload.enabled());
        entity.setPasswordHash(authService.encodePassword(managerProperties.getAuth().getDefaultUserPassword()));
        return authService.toProfile(userRepository.save(entity));
    }

    @Transactional
    public UserProfile update(Long id, UserPayload payload) {
        UserEntity entity = getEntity(id);
        UserRole originalRole = entity.getRole();
        String username = normalizeUsername(payload.username());
        if (!entity.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        ensureAtLeastOneAdmin(originalRole, payload.role());
        entity.setUsername(username);
        entity.setDisplayName(payload.displayName().trim());
        entity.setRole(payload.role());
        entity.setEnabled(payload.enabled());
        return authService.toProfile(userRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        UserEntity entity = getEntity(id);
        if (entity.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要保留一个管理员账号");
        }
        userRepository.delete(entity);
    }

    @Transactional
    public void resetPassword(Long id) {
        UserEntity entity = getEntity(id);
        entity.setPasswordHash(authService.encodePassword(managerProperties.getAuth().getDefaultUserPassword()));
        entity.setAuthToken(null);
        entity.setAuthTokenExpiresAt(null);
        userRepository.save(entity);
    }

    private void ensureAtLeastOneAdmin(UserRole originalRole, UserRole nextRole) {
        if (originalRole != UserRole.ADMIN || nextRole == UserRole.ADMIN) {
            return;
        }
        if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要保留一个管理员账号");
        }
    }

    private UserEntity getEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "用户不存在: " + id));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }
}
