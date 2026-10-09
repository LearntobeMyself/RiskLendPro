package org.example.risklendpro.common;

import org.example.risklendpro.common.api.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR = "服务器内部错误";

    @ExceptionHandler(CatalogBusinessException.class)
    public CommonResponse<Object> handleCatalogBusinessException(CatalogBusinessException e) {
        log.warn("CatalogBusinessException {}: {}", e.getCode(), e.getMessage());
        return CommonResponse.fail(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(DuplicateApplyException.class)
    public CommonResponse<Void> handleDuplicateApplyException(DuplicateApplyException e) {
        log.warn("DuplicateApplyException: {}", e.getMessage());
        return CommonResponse.fail(400, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(CommonResponse.fail(403, "无权限访问该接口"));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public CommonResponse<Void> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return CommonResponse.fail(400, badRequestMessage(e));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public CommonResponse<Void> handleNotFound(Exception e) {
        log.warn("Not found: {}", e.getMessage());
        return CommonResponse.fail(404, "接口不存在");
    }

    @ExceptionHandler(DataAccessException.class)
    public CommonResponse<Void> handleDataAccess(DataAccessException e) {
        log.error("DataAccessException", e);
        if (isConcurrentConflict(e)) {
            return CommonResponse.fail(400, "操作冲突，请稍后重试");
        }
        return CommonResponse.fail(500, INTERNAL_ERROR);
    }

    @ExceptionHandler(NullPointerException.class)
    public CommonResponse<Void> handleNullPointer(NullPointerException e) {
        log.error("NullPointerException", e);
        return CommonResponse.fail(500, INTERNAL_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public CommonResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);
        if (clientSafe(e.getMessage())) {
            return CommonResponse.fail(400, e.getMessage());
        }
        return CommonResponse.fail(500, INTERNAL_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return CommonResponse.fail(500, INTERNAL_ERROR);
    }

    private static boolean isConcurrentConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql) {
                int code = sql.getErrorCode();
                if (code == 1020 || code == 1205 || code == 1213) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("record has changed")
                        || lower.contains("deadlock")
                        || lower.contains("lock wait timeout")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean clientSafe(String message) {
        if (message == null || message.isBlank() || message.length() > 200) {
            return false;
        }
        if (message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
            return false;
        }
        String lower = message.toLowerCase();
        return !lower.contains("exception")
                && !lower.contains("java.")
                && !lower.contains("org.")
                && !lower.contains("com.")
                && !lower.contains("sql")
                && !lower.contains("mapper")
                && !lower.contains("select ")
                && !lower.contains("insert ")
                && !lower.contains("update ")
                && !lower.contains("delete ");
    }

    private static String badRequestMessage(Exception e) {
        if (e instanceof MethodArgumentNotValidException invalid) {
            return fieldMessage(invalid.getBindingResult().getFieldError());
        }
        if (e instanceof BindException bind) {
            return fieldMessage(bind.getFieldError());
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体不能为空或格式不正确";
        }
        if (e instanceof MissingServletRequestParameterException missing) {
            return "缺少参数：" + missing.getParameterName();
        }
        return "请求参数不合法";
    }

    private static String fieldMessage(FieldError fieldError) {
        if (fieldError != null && fieldError.getDefaultMessage() != null && clientSafe(fieldError.getDefaultMessage())) {
            return fieldError.getDefaultMessage();
        }
        return "请求参数不合法";
    }
}
