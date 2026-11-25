BEGIN;
# 密码  Acc@2026#Kx9mT2
INSERT INTO account_users (
    id,
    account,
    email,
    name,
    phone,
    status,
    version,
    password,
    created_by,
    updated_by
) VALUES (
             '-1',
             'admin',
             'admin@hongjie.com',
             '系统管理员',
             '18756057535',
             'enabled',
             1,
             '$2a$10$X0TV696sxm3JDafJDSlFGuqpkRQG8rRhJxZczuePAm9dE4RtFf2jW',
             'bootstrap',
             'bootstrap'
         );

INSERT INTO account_admin_roles (
    user_id,
    role_code
) VALUES (
             '-1',
             'ACCOUNT_ADMIN'
         );

COMMIT;