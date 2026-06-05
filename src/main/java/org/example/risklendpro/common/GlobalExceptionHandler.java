package org.example.risklendpro.common;

import org.example.risklendpro.common.DuplicateApplyException;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateApplyException.class)
    public CommonResponse<Void> handleDuplicateApplyException(DuplicateApplyException e) {
        log.warn("DuplicateApplyException: {}", e.getMessage());
        return CommonResponse.fail(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public CommonResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);
        return CommonResponse.fail(400, formatErrorMessage(e));
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return CommonResponse.fail(500, "服务器内部错误: " + formatErrorMessage(e));
    }

    private static String formatErrorMessage(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            message = message + " (cause: " + cause.getMessage() + ")";
        }
        return message;
    }
}