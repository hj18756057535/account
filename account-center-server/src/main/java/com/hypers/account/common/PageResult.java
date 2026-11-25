package com.hypers.account.common;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/** 分页结果；没有数据时 items 为空集合，不返回 null。 */
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
