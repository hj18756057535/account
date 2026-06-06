create table if not exists account_users (
    id varchar(64) primary key,
    account varchar(128) not null,
    email varchar(255) not null,
    name varchar(128) not null,
    phone varchar(64) not null,
    status varchar(32) not null default 'enabled',
    created_by varchar(64),
    updated_by varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp,
    constraint uk_account_users_account unique (account)
);

create table if not exists account_applications (
    app_code varchar(64) primary key,
    name varchar(128) not null,
    entry_url varchar(512) not null,
    sso_callback_url varchar(512) not null,
    permission_iframe_url varchar(512) not null,
    notify_base_url varchar(512) not null,
    secret varchar(255) not null,
    default_tenant_code varchar(64) not null default 'default',
    status varchar(32) not null default 'enabled',
    secret_version integer not null default 1,
    created_by varchar(64),
    updated_by varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp
);

create table if not exists account_user_applications (
    user_id varchar(64) not null,
    app_code varchar(64) not null,
    status varchar(32) not null default 'enabled',
    authorized_by varchar(64),
    created_at timestamp not null default current_timestamp,
    deauthorized_at timestamp,
    primary key (user_id, app_code),
    constraint fk_ua_user foreign key (user_id) references account_users (id),
    constraint fk_ua_app foreign key (app_code) references account_applications (app_code)
);

create table if not exists account_operation_logs (
    id varchar(64) primary key,
    operator_id varchar(64),
    operation_type varchar(64) not null,
    target_type varchar(64) not null,
    target_id varchar(128) not null,
    detail text,
    created_at timestamp not null default current_timestamp
);

create table if not exists account_nonce_records (
    nonce varchar(128) primary key,
    app_code varchar(64) not null,
    purpose varchar(64) not null,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists account_admin_tickets (
    code varchar(128) primary key,
    app_code varchar(64) not null,
    user_id varchar(64) not null,
    purpose varchar(64) not null,
    expires_at timestamp not null,
    used_at timestamp,
    created_at timestamp not null default current_timestamp
);

create table if not exists account_admin_roles (
    user_id varchar(64) not null,
    role_code varchar(64) not null,
    created_at timestamp not null default current_timestamp,
    primary key (user_id, role_code)
);
