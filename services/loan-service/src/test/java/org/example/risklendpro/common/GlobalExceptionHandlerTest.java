package org.example.risklendpro.common;

import org.apache.ibatis.exceptions.PersistenceException;
import org.example.risklendpro.common.api.CommonResponse;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void nullPointerDoesNotExposeClassName() {
        CommonResponse<Void> response = handler.handleNullPointer(
                new NullPointerException("Cannot invoke \"String.equals(Object)\" because getPassword() is null"));
        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertFalse(response.getMessage().contains("getPassword"));
    }

    @Test
    void concurrentRecordChangeIsRetryable() {
        CommonResponse<Void> response = handler.handleDataAccess(new MyBatisSystemException(
                new PersistenceException("Record has changed since last read in table 'user_credit_limit'")));
        assertEquals(400, response.getCode());
        assertEquals("操作冲突，请稍后重试", response.getMessage());
        assertFalse(response.getMessage().contains("user_credit_limit"));
    }

    @Test
    void dataAccessDoesNotExposeSql() {
        CommonResponse<Void> response = handler.handleDataAccess(new MyBatisSystemException(
                new PersistenceException("Error querying database. Cause: java.sql.SQLSyntaxErrorException")));
        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertFalse(response.getMessage().contains("SQLSyntax"));
    }

    @Test
    void missingParameterIsBadRequest() throws Exception {
        CommonResponse<Void> response = handler.handleBadRequest(
                new MissingServletRequestParameterException("pageNum", "Integer"));
        assertEquals(400, response.getCode());
        assertEquals("缺少参数：pageNum", response.getMessage());
    }

    @Test
    void unknownPathIsNotFound() {
        CommonResponse<Void> response = handler.handleNotFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing"));
        assertEquals(404, response.getCode());
        assertEquals("接口不存在", response.getMessage());
    }

    @Test
    void businessRuntimeMessageStaysVisible() {
        CommonResponse<Void> response = handler.handleRuntimeException(new RuntimeException("手机号已被注册"));
        assertEquals(400, response.getCode());
        assertEquals("手机号已被注册", response.getMessage());
    }

    @Test
    void runtimeMessageWithSqlIsHidden() {
        CommonResponse<Void> response = handler.handleRuntimeException(
                new RuntimeException("Error querying database. Cause: java.sql.SQLException"));
        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
    }
}
