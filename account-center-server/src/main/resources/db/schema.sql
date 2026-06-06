create table if not exists account_users (
    id varchar(64) primary key,
    account varchar(128) not null,
    email varchar(255) not null,
    name varchar(128) not null,
    phone varchar(64) not null,
    created_at timestamp not null default current_timestamp
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
    created_at timestamp not null default current_timestamp
);

create table if not exists account_user_applications (
    user_id varchar(64) not null,
    app_code varchar(64) not null,
    created_at timestamp not null default current_timestamp,
    primary key (user_id, app_code),
    constraint fk_account_user_applications_user foreign key (user_id) references account_users (id),
    constraint fk_account_user_applications_app foreign key (app_code) references account_applications (app_code)
);
