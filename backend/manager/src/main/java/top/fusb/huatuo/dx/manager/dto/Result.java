package top.fusb.huatuo.dx.manager.dto;

public record Result<T>(
        String code,
        String subCode,
        String message,
        T data
) {
    public static <T> Result<T> success(T data) {
        return new Result<>("0", "SUCCESS", "OK", data);
    }

    public static Result<Void> success() {
        return new Result<>("0", "SUCCESS", "OK", null);
    }

    public static <T> Result<T> failure(String code, String subCode, String message) {
        return new Result<>(code, subCode, message, null);
    }
}
