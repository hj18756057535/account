package com.hypers.account.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * Account Center 统一用户实体。
 * 不使用 ORM 注解，由 MyBatis resultMap 映射数据库字段。
 */
public class AccountUser {

    private String id;
    private String account;
    private String email;
    private String name;
    private String phone;
    private String status;
    @JsonIgnore
    private String password;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountUser() {
    }

    public AccountUser(String id, String account, String email, String name, String phone) {
        this(id, account, email, name, phone, "enabled", null, null, null, null);
    }

    public AccountUser(String id, String account, String email, String name, String phone,
                        String status, String createdBy, String updatedBy,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
