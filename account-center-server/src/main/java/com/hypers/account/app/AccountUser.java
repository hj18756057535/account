package com.hypers.account.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Account Center 统一用户实体。
 * 不使用 ORM 注解，由 MyBatis resultMap 映射数据库字段。
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountUser {

    private String id;
    private String account;
    private String email;
    private String name;
    private String phone;
    private String status;
    private long version;
    @JsonIgnore
    private String password;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountUser(String id, String account, String email, String name, String phone) {
        this(id, account, email, name, phone, "enabled", 1L, null, null, null, null);
    }

    public AccountUser(String id, String account, String email, String name, String phone,
                        String status, long version, String createdBy, String updatedBy,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.status = status;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}
