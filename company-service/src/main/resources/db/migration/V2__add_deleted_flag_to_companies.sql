alter table companies
    add column if not exists deleted boolean not null default false;

create index if not exists idx_companies_deleted on companies(deleted);
