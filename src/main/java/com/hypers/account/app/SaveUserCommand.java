package com.hypers.account.app;

public class SaveUserCommand {

    private final String account;
    private final String email;
    private final String name;
    private final String phone;

    public SaveUserCommand(String account, String email, String name, String phone) {
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
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
