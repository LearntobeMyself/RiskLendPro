package org.example.risklendpro.common;

import org.example.risklendpro.common.DuplicateApplyException;
import org.example.risklendpro.common.api.CommonResponse;
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
        // 业务异常信息直接返回给用户（可控、非敏感），仅取最外层 message
        return CommonResponse.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        // 未预期异常：仅记录日志，对外返回固定文案，不泄漏内部细节
        return CommonResponse.fail(500, "服务器内部错误");
    }
}