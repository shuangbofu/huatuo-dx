package top.fusb.huatuo.dx.agent.controller;

import top.fusb.huatuo.dx.agent.dto.Result;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException exception) {
        log.warn(
                "Business exception code={}, subCode={}, message={}",
                exception.getCode(),
                exception.getSubCode(),
                exception.getMessage(),
                exception
        );
        return Result.failure(exception.getCode(), exception.getSubCode(), exception.getMessage());
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
    public Result<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.failure(ErrorCode.BAD_REQUEST.code(), "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.failure(ErrorCode.BAD_REQUEST.code(), "BIND_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception exception) {
        log.error("Unhandled agent exception", exception);
        return Result.failure(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.subCode(), exception.getMessage());
    }
}
