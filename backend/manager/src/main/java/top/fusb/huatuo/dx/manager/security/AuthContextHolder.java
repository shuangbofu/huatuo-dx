package top.fusb.huatuo.dx.manager.security;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    public static AuthenticatedUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
