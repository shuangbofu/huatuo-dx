package top.fusb.huatuo.dx.manager.exception;

public enum ErrorCode {
    SUCCESS("0", "SUCCESS"),
    BAD_REQUEST("400", "BAD_REQUEST"),
    VALIDATION_ERROR("400", "VALIDATION_ERROR"),
    BIND_ERROR("400", "BIND_ERROR"),
    AUTH_REQUIRED("401", "AUTH_REQUIRED"),
    AUTH_LOGIN_FAILED("401", "AUTH_LOGIN_FAILED"),
    AUTH_TOKEN_INVALID("401", "AUTH_TOKEN_INVALID"),
    PLUGIN_ACCESS_DENIED("401", "PLUGIN_ACCESS_DENIED"),
    PLUGIN_SIGNATURE_INVALID("401", "PLUGIN_SIGNATURE_INVALID"),
    AUTH_ADMIN_REQUIRED("403", "AUTH_ADMIN_REQUIRED"),
    AUTH_USER_DISABLED("403", "AUTH_USER_DISABLED"),
    ENTITY_NOT_FOUND("404", "ENTITY_NOT_FOUND"),
    NODE_NOT_FOUND("404", "NODE_NOT_FOUND"),
    RULE_NOT_FOUND("404", "RULE_NOT_FOUND"),
    AGENT_CAPABILITY_UNAVAILABLE("409", "AGENT_CAPABILITY_UNAVAILABLE"),
    REMOTE_CALL_FAILED("502", "REMOTE_CALL_FAILED"),
    AGENT_SYNC_FAILED("502", "AGENT_SYNC_FAILED"),
    DATABASE_ERROR("500", "DATABASE_ERROR"),
    INTERNAL_ERROR("500", "INTERNAL_ERROR");

    private final String code;
    private final String subCode;

    ErrorCode(String code, String subCode) {
        this.code = code;
        this.subCode = subCode;
    }

    public String code() {
        return code;
    }

    public String subCode() {
        return subCode;
    }
}
