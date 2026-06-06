-- ============================================================
-- V2__add_password_field.sql
-- 为用户表新增密码字段，支持 Account Center 本地登录
-- ============================================================

ALTER TABLE account_users ADD COLUMN password varchar(255);
