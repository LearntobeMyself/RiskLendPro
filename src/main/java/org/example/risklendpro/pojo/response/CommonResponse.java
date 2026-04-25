package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用响应体")
public class CommonResponse<T> {
    @Schema(description = "状态码，200表示成功，其他表示失败")
    private int code;
    @Schema(description = "响应消息")
    private String message;
    @Schema(description = "是否成功")
    private boolean success;
    @Schema(description = "响应数据，成功时返回业务数据，失败时返回null")
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(200);
        response.setMessage("操作成功");
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> CommonResponse<T> success(String message, T data) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> CommonResponse<T> fail(int code, String message) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setSuccess(false);
        response.setData(null);
        return response;
    }
}