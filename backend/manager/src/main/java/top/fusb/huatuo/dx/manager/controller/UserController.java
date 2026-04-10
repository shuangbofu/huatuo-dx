package top.fusb.huatuo.dx.manager.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.huatuo.dx.manager.dto.UserPayload;
import top.fusb.huatuo.dx.manager.dto.UserProfile;
import top.fusb.huatuo.dx.manager.dto.PageResult;
import top.fusb.huatuo.dx.manager.entity.UserRole;
import top.fusb.huatuo.dx.manager.security.AdminOnly;
import top.fusb.huatuo.dx.manager.service.UserService;

@RestController
@AdminOnly
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserProfile> list() {
        return userService.list();
    }

    @GetMapping("/page")
    public PageResult<UserProfile> listPage(
            @org.springframework.web.bind.annotation.RequestParam(value = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "role", required = false) UserRole role,
            @org.springframework.web.bind.annotation.RequestParam(value = "enabled", required = false) Boolean enabled,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        return userService.listPage(keyword, role, enabled, page, pageSize);
    }

    @PostMapping
    public UserProfile create(@Valid @RequestBody UserPayload payload) {
        return userService.create(payload);
    }

    @PutMapping("/{id}")
    public UserProfile update(@PathVariable("id") Long id, @Valid @RequestBody UserPayload payload) {
        return userService.update(id, payload);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable("id") Long id) {
        userService.resetPassword(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        userService.delete(id);
    }
}
