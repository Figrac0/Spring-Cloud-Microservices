alter table users
    add column if not exists roles varchar(255) not null default 'USER';

update users
set roles = 'USER'
where roles is null or trim(roles) = '';
