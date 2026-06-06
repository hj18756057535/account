package com.hypers.account.app;

public class AccountUser {

    private final String id;
    private final String account;
    private final String email;
    private final String name;
    private final String phone;

    public AccountUser(String id, String account, String email, String name, String phone) {
        this.id = id;
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
    }

    public String getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
