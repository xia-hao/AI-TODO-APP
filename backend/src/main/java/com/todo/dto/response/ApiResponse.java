package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiResponse<T> {
    private boolean success;
    private int code = 200;
    private T data;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.code = 200;
        r.data = data;
        r.message = "操作成功";
        return r;
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.code = 200;
        r.data = data;
        r.message = message;
        return r;
    }

    public static ApiResponse<Void> error(String message, int code) {
        ApiResponse<Void> r = new ApiResponse<>();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }
}
