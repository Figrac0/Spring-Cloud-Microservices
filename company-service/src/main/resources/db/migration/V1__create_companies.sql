create table if not exists companies (
  id bigserial primary key,
  name varchar(255) not null,
  ogrn varchar(20) not null unique,
  activity_description text,
  director_id bigint not null
);

create index if not exists idx_companies_director_id on companies(director_id);