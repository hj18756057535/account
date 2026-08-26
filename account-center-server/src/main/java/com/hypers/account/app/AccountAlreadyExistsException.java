package com.hypers.account.app;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException() {
        super("account already exists");
    }
}
