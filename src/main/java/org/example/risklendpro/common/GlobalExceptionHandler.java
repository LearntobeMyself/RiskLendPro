package org.example.risklendpro.common;

import org.example.risklendpro.pojo.response.CommonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(RuntimeException.class)
    public CommonResponse<Void> handleRuntimeException(RuntimeException e) {
        return CommonResponse.fail(400, e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception e) {
        return CommonResponse.fail(500, "服务器内部错误: " + e.getMessage());
    }
}