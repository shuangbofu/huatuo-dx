package top.fusb.huatuo.dx.manager.controller;

import top.fusb.huatuo.dx.manager.dto.Result;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        log.warn(
                "Business exception code={}, subCode={}, message={}",
                exception.getCode(),
                exception.getSubCode(),
                exception.getMessage(),
                exception
        );
        return Result.failure(exception.getCode(), exception.getSubCode(), exception.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public Result<Void> handleNotFound(EntityNotFoundException exception) {
        return Result.failure(ErrorCode.ENTITY_NOT_FOUND.code(), ErrorCode.ENTITY_NOT_FOUND.subCode(), exception.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Void> handleBadRequest(Exception exception) {
        return Result.failure(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.subCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.failure(ErrorCode.VALIDATION_ERROR.code(), ErrorCode.VALIDATION_ERROR.subCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.failure(ErrorCode.BIND_ERROR.code(), ErrorCode.BIND_ERROR.subCode(), message);
    }

    @ExceptionHandler(RestClientResponseException.class)
    public Result<Void> handleRemoteResponseException(RestClientResponseException exception) {
        log.warn(
                "Remote service returned error status={}, responseBody={}",
                exception.getStatusCode().value(),
                exception.getResponseBodyAsString(),
                exception
        );
        String message = "远端服务调用失败: HTTP " + exception.getStatusCode().value();
        if (exception.getStatusCode().value() == 403) {
            message = "远端服务拒绝访问，请检查节点密钥或节点配置";
        }
        return Result.failure(ErrorCode.REMOTE_CALL_FAILED.code(), ErrorCode.REMOTE_CALL_FAILED.subCode(), message);
    }

    @ExceptionHandler(RestClientException.class)
    public Result<Void> handleRemoteException(RestClientException exception) {
        log.warn("Remote service invocation failed", exception);
        return Result.failure(ErrorCode.REMOTE_CALL_FAILED.code(), ErrorCode.REMOTE_CALL_FAILED.subCode(), "远端服务调用失败");
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            DataAccessException.class
    })
    public Result<Void> handleDatabaseException(Exception exception) {
        log.error("Database operation failed", exception);
        return Result.failure(
                ErrorCode.DATABASE_ERROR.code(),
                ErrorCode.DATABASE_ERROR.subCode(),
                "数据库操作失败，请稍后重试"
        );
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception exception) {
        log.error("Unhandled exception", exception);
        return Result.failure(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.subCode(), "系统异常，请稍后重试");
    }
}
