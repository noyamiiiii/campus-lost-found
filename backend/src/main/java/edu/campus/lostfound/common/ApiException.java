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

    public static ApiException notFound(String message) {
        return new ApiException(40400, HttpStatus.NOT_FOUND, message);
    }
}
