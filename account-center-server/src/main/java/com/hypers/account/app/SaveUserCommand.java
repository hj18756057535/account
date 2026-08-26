package com.hypers.account.app;

import lombok.Value;

@Value
public class SaveUserCommand {

    String account;
    String email;
    String name;
    String phone;
}
