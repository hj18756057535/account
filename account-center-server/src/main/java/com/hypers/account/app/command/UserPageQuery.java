package com.hypers.account.app.command;

import lombok.Value;

/** 用户分页查询条件。 */
@Value
public class UserPageQuery {

    int page;
    int size;
    String keyword;
    String status;
    String sortField;
    String sortDirection;

    public int getOffset() {
        return (page - 1) * size;
    }
}
