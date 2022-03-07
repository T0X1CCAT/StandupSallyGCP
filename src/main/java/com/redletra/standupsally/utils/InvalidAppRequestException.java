package com.redletra.standupsally.utils;

public class InvalidAppRequestException extends RuntimeException{
    public InvalidAppRequestException() {
    }

    public InvalidAppRequestException(String message) {
        super(message);
    }

    public InvalidAppRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidAppRequestException(Throwable cause) {
        super(cause);
    }

    public InvalidAppRequestException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
