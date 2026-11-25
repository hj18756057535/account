package com.hypers.account.app.command;

import lombok.Value;

/** 新建或更新用户的入参。 */
@Value
public class SaveUserCommand {

    String account;
    String email;
    String name;
    String phone;
}
