package top.fusb.huatuo.dx.manager.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.huatuo.dx.manager.dto.LoginRequest;
import top.fusb.huatuo.dx.manager.dto.LoginResponse;
import top.fusb.huatuo.dx.manager.dto.UserProfile;
import top.fusb.huatuo.dx.manager.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/plugin/login")
    public LoginResponse pluginLogin(@Valid @RequestBody LoginRequest request) {
        return authService.loginForPlugin(request);
    }

    @GetMapping("/me")
    public UserProfile me() {
        return authService.currentUserProfile();
    }

    @PostMapping("/logout")
    public void logout() {
        authService.logout();
    }
}
