package com.hypers.account.app;

public class ResourceVersionConflictException extends RuntimeException {

    public ResourceVersionConflictException() {
        super("resource version conflict");
    }
}
