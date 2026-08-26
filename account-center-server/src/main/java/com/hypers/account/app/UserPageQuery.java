package com.hypers.account.app;

import lombok.Value;

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
