package com.todo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static AppException forbidden() {
        return new AppException("无权限执行此操作", HttpStatus.FORBIDDEN);
    }

    public static AppException notFound(String resource) {
        return new AppException(resource + "不存在", HttpStatus.NOT_FOUND);
    }

    public static AppException badRequest(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST);
    }
}
