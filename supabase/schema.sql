-- SolarPulse — Supabase schema
-- Supabase SQL Editor دا بۇ ھۆججەتنى ئىجرا قىلىڭ. / Run this in the Supabase SQL editor.
-- Column names are snake_case versions of the TypeScript fields in src/lib/types.ts.

create table if not exists sites (
  id text primary key,
  name text not null,
  location text not null,
  type text not null check (type in ('residential','commercial','industrial','utility')),
  status text not null check (status in ('active','idle','offline','maintenance')),
  capacity_kw numeric not null,
  battery_kwh numeric not null default 0,
  price_per_kwh numeric not null default 0.1,
  customer text not null,
  install_date date not null,
  lat double precision not null default 0,
  lng double precision not null default 0
);

create table if not exists devices (
  id text primary key,
  site_id text not null references sites(id) on delete cascade,
  name text not null,
  type text not null check (type in ('inverter','battery','panel','meter','sensor')),
  model text not null,
  serial text not null default '',
  status text not null check (status in ('online','warning','offline')),
  health numeric not null default 100,
  efficiency numeric not null default 100,
  firmware text not null default '',
  installed_at date not null,
  last_seen timestamptz not null default now()
);

create table if not exists tickets (
  id text primary key,
  site_id text not null references sites(id) on delete cascade,
  device_id text references devices(id) on delete set null,
  title text not null,
  description text not null default '',
  priority text not null check (priority in ('low','medium','high','critical')),
  status text not null check (status in ('open','in_progress','resolved')),
  assignee text not null default '',
  due_date date not null,
  created_at timestamptz not null default now()
);

create table if not exists invoices (
  id text primary key,
  number text not null,
  site_id text not null references sites(id) on delete cascade,
  customer text not null,
  period text not null,            -- YYYY-MM
  energy_kwh numeric not null,
  rate numeric not null,
  amount numeric not null,
  status text not null check (status in ('paid','pending','overdue')),
  issued_at date not null,
  due_at date not null,
  paid_at date
);

create table if not exists notifications (
  id text primary key,
  title text not null,
  body text not null,
  kind text not null check (kind in ('info','success','warning','danger')),
  link text,
  read boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists reports (
  id text primary key,
  kind text not null,
  title text not null,
  "from" date not null,
  "to" date not null,
  site_ids text[] not null default '{}',
  created_at timestamptz not null default now()
);

create table if not exists settings (
  id text primary key default 'settings',
  user_name text, email text, role text, company text,
  language text, theme text, currency text,
  city text, lat double precision, lng double precision,
  co2_kg_per_kwh numeric, tree_kg_per_year numeric, car_tons_per_year numeric,
  notify_email boolean, notify_push boolean, notify_device_alerts boolean,
  notify_billing boolean, notify_maintenance boolean
);

-- Optional: real telemetry. When populated, it can replace the simulator in src/lib/sim.ts.
create table if not exists readings (
  site_id text not null references sites(id) on delete cascade,
  ts timestamptz not null,
  power_kw numeric not null,
  energy_kwh numeric,
  primary key (site_id, ts)
);

-- ── v2: financial (ROI) fields on sites ─────────────────────────────────────
alter table sites add column if not exists system_cost numeric not null default 0;          -- CAPEX, in settings.currency
alter table sites add column if not exists annual_opex numeric not null default 0;          -- O&M per year
alter table sites add column if not exists degradation_pct numeric not null default 0.5;    -- output loss per year, %
alter table sites add column if not exists tariff_escalation_pct numeric not null default 2; -- tariff growth per year, %
alter table settings add column if not exists discount_rate_pct numeric;                     -- NPV discount rate, % (default 6)

-- ── v2: user-defined alert rules ────────────────────────────────────────────
-- metric values: site_yield_below (today kWh/kWp), site_offline, device_efficiency_below,
-- device_health_below, device_offline_minutes, invoice_overdue_days
create table if not exists alert_rules (
  id text primary key,
  name text not null,
  metric text not null check (metric in ('site_yield_below','site_offline','device_efficiency_below','device_health_below','device_offline_minutes','invoice_overdue_days')),
  threshold numeric not null default 0,
  site_id text references sites(id) on delete cascade,  -- null = all sites
  severity text not null default 'warning' check (severity in ('warning','danger')),
  enabled boolean not null default true,
  last_triggered_at timestamptz,
  created_at timestamptz not null default now()
);

-- ── v2: vendor integrations (real telemetry) ────────────────────────────────
create table if not exists integrations (
  id text primary key,
  vendor text not null check (vendor in ('solaredge','fusionsolar','webhook')),
  name text not null,
  site_id text not null references sites(id) on delete cascade,
  external_id text not null default '',          -- vendor site id / station code
  config jsonb not null default '{}'::jsonb,      -- non-secret settings (base_url, username, ...)
  ingest_token text not null default replace(gen_random_uuid()::text, '-', ''), -- for webhook/MQTT-bridge POSTs
  status text not null default 'pending' check (status in ('pending','ok','error')),
  last_sync_at timestamptz,
  last_error text,
  created_at timestamptz not null default now()
);

-- Secrets (API keys, passwords) are write-only for the app: no select policy,
-- only Edge Functions (service role) can read them.
create table if not exists integration_secrets (
  integration_id text primary key references integrations(id) on delete cascade,
  secret jsonb not null default '{}'::jsonb
);

create index if not exists devices_site_idx on devices(site_id);
create index if not exists tickets_site_idx on tickets(site_id);
create index if not exists invoices_site_idx on invoices(site_id);
create index if not exists invoices_period_idx on invoices(period);
create index if not exists readings_ts_idx on readings(site_id, ts desc);
create index if not exists integrations_site_idx on integrations(site_id);

-- Row Level Security: only signed-in users can read/write (single-organisation setup).
do $$
declare t text;
begin
  foreach t in array array['sites','devices','tickets','invoices','notifications','reports','settings','readings','alert_rules','integrations'] loop
    execute format('alter table %I enable row level security', t);
    execute format('drop policy if exists "auth full access" on %I', t);
    execute format('create policy "auth full access" on %I for all to authenticated using (true) with check (true)', t);
  end loop;
end $$;

-- integration_secrets has RLS on and no policies: clients can't read or write it directly.
-- They store a secret through this function; only Edge Functions (service role) read it.
alter table integration_secrets enable row level security;
create or replace function set_integration_secret(p_integration_id text, p_secret jsonb)
returns void language sql security definer set search_path = public as $$
  insert into integration_secrets (integration_id, secret) values (p_integration_id, p_secret)
  on conflict (integration_id) do update set secret = excluded.secret;
$$;
revoke all on function set_integration_secret(text, jsonb) from public, anon;
grant execute on function set_integration_secret(text, jsonb) to authenticated;
