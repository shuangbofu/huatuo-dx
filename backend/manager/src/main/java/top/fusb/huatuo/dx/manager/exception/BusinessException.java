package top.fusb.huatuo.dx.manager.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String code;
    private final String subCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.code = errorCode.code();
        this.subCode = errorCode.subCode();
    }

    public BusinessException(String code, String subCode, String message) {
        super(message);
        this.errorCode = null;
        this.code = code;
        this.subCode = subCode;
    }

    public String getCode() {
        return code;
    }

    public String getSubCode() {
        return subCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
