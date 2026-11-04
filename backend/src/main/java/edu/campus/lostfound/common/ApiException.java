package edu.campus.lostfound.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public ApiException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(40001, HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(40100, HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(40300, HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(40400, HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(40900, HttpStatus.CONFLICT, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(42900, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
