create table if not exists users (
  id bigserial primary key,
  name varchar(255) not null,
  login varchar(100) not null unique,
  password varchar(255) not null,
  email varchar(255) not null unique,
  active boolean not null default true,
  company_id bigint
);

create index if not exists idx_users_company_id on users(company_id);
create index if not exists idx_users_active on users(active);