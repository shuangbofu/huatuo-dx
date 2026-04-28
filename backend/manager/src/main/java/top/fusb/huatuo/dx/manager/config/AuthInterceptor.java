package top.fusb.huatuo.dx.manager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.security.AdminOnly;
import top.fusb.huatuo.dx.manager.security.AuthContextHolder;
import top.fusb.huatuo.dx.manager.security.AuthenticatedUser;
import top.fusb.huatuo.dx.manager.service.AuthService;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        if (isPublicApi(request)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        }
        if ((token == null || token.isBlank()) && isSseRequest(request)) {
            token = request.getParameter("authToken");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录");
        }
        AuthenticatedUser user = request.getRequestURI().startsWith("/api/plugin/")
                ? authService.authenticatePlugin(token)
                : authService.authenticate(token);
        AuthContextHolder.set(user);
        if (handler instanceof HandlerMethod handlerMethod) {
            boolean adminOnly = handlerMethod.hasMethodAnnotation(AdminOnly.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
            if (adminOnly && !user.isAdmin()) {
                throw new BusinessException(ErrorCode.AUTH_ADMIN_REQUIRED, "当前账号没有管理端权限");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }

    private boolean isPublicApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.startsWith("/api/auth/login")) {
            return true;
        }
        if (uri.startsWith("/api/auth/plugin/login")) {
            return true;
        }
        if (uri.startsWith("/api/plugin/log-console/")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/agents/register".equals(uri)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && uri.matches("^/api/agents/\\d+/heartbeat$");
    }

    private boolean isSseRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/log-console/tail/stream");
    }
}
