package top.fusb.huatuo.dx.agent.exception;

public enum ErrorCode {
    BAD_REQUEST("400", "BAD_REQUEST"),
    INVALID_SECRET("403", "INVALID_SECRET"),
    LOG_PATH_NOT_FOUND("404", "LOG_PATH_NOT_FOUND"),
    LOG_PATH_OUTSIDE_ROOT("400", "LOG_PATH_OUTSIDE_ROOT"),
    LOG_FILE_IS_DIRECTORY("400", "LOG_FILE_IS_DIRECTORY"),
    KEYWORD_REQUIRED("400", "KEYWORD_REQUIRED"),
    RULE_NOT_FOUND("404", "RULE_NOT_FOUND"),
    TARGET_PROCESS_NOT_FOUND("404", "TARGET_PROCESS_NOT_FOUND"),
    ARTHAS_INSTALL_FAILED("500", "ARTHAS_INSTALL_FAILED"),
    ARTHAS_EXECUTE_FAILED("500", "ARTHAS_EXECUTE_FAILED"),
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
