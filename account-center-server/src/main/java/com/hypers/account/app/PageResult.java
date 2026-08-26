package com.hypers.account.app;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    public PageResult(List<T> items, int page, int size, long total) {
        this.items = items == null ? Collections.emptyList() : items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

}
