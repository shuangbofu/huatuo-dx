package top.fusb.huatuo.dx.agent.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getCode() {
        return errorCode.code();
    }

    public String getSubCode() {
        return errorCode.subCode();
    }
}
