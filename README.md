# SolarPulse — قۇياش ئېنېرگىيىسى نازارەت سىستېمىسى

قۇياش ئېنېرگىيە ئىستانسىلىرىنى نازارەت قىلىش، ئانالىز قىلىش ۋە باشقۇرۇش ئۈچۈن تولۇق ئىقتىدارلىق تور سىستېمىسى.
ئۇيغۇرچە (RTL)، ئىنگلىزچە، ئەرەبچە (RTL) ۋە تۈركچە، يورۇق/قاراڭغۇ ئۇسلۇب، كومپيۇتېر ۋە يانفون ئېكرانىنى قوللايدۇ.

Languages: Uyghur, English, Arabic, Turkish (`src/i18n/`).

A full solar-fleet monitoring web app (React + TypeScript + Vite + Tailwind), built from the SolarPulse dashboard design.

## ئىجرا قىلىش / Run

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # production build → dist/
```

سىناق ھالىتىدە خالىغان ئېلخەت/پارول بىلەن كىرەلەيسىز. سانلىق مەلۇمات توركۆرگۈچتە ساقلىنىدۇ.
In demo mode any email/password signs in and data is kept in browser localStorage.

## بەتلەر / Pages

| بەت | ئىقتىدارى |
|---|---|
| ئومۇمىي كۆرۈنۈش / Overview | جانلىق قۇۋۋەت، بۈگۈنكى ئېنېرگىيە، CO₂، كىرىم، ھاسىلات گرافىكى (كۈن/ھەپتە/ئاي)، مۇھىت تەسىرى، ئىقتىدار ۋە سالامەتلىك، ئىستانسا جەدۋىلى، جانلىق ئېنېرگىيە ئېقىمى، ھاۋا رايى |
| ئىستانسىلار / Sites | ئىزدەش، سۈزۈش، كاتەكچە/تىزىملىك، قوشۇش/تەھرىرلەش/ئۆچۈرۈش، تەپسىلات بېتى (گرافىك، ئۈسكۈنە، ۋەزىپە، تالون، مالىيە كارتىسى، كىچىك خەرىتە، «جانلىق» قۇۋۋەت) |
| خەرىتە / Map | Leaflet + OpenStreetMap؛ بەلگە رەڭگى = ھالەت، چوڭلۇقى = سىغىم، ھالەت سۈزگۈچى، ئىستانسا كارتىسى → تەپسىلات بېتى. Status-coloured, capacity-sized pins, filter chips, site list, popup card |
| مالىيە / Finance | 25 يىللىق ROI: قايتۇرۇش مۇددىتى، ROI، NPV، IRR، LCOE، جۇغلانما نەق پۇل ئېقىمى، ئىستانسىلار جەدۋىلى. Portfolio and per-site ROI (`src/lib/finance.ts`) |
| ئاگاھلاندۇرۇش / Alerts | قائىدە قوشۇش/تەھرىرلەش (كۆرسەتكۈچ، چەك، ئىستانسا، دەرىجە)، يېقىنقى ئاگاھلاندۇرۇشلار؛ 6 سائەتلىك جىملىق. Rule CRUD, recent alerts, 6-hour cooldown (`src/lib/alerts.ts`) |
| ئانالىز / Analytics | 7/30/90 كۈن، 12 ئاي؛ يۈزلىنىش، ئىستانسا ۋە تۈر بويىچە، ھاسىلات-ئىستېمال، ئىسسىقلىق خەرىتىسى، مۇھىت تەسىرى، CSV |
| ئۈسكۈنىلەر / Devices | سالامەتلىك ۋە ئۈنۈم، قايتا قوزغىتىش، يۇمشاق دېتال يېڭىلاش، ۋەزىپە قۇرۇش، CRUD، CSV |
| دوكلاتلار / Reports | 5 خىل دوكلات، ۋاقىت ۋە ئىستانسا تاللاش، ئالدىن كۆرۈش، CSV، PDF، بېسىش، ساقلاش |
| ئاسراش / Maintenance | سۆرەپ-تاشلاش تاختىسى ۋە تىزىملىك، مۇھىملىق، مۆھلەت، مەسئۇل |
| ھېسابات / Billing | تالون ھاسىل قىلىش، تۆلەندى قىلىش، بېسىلىدىغان تالون، PDF تالون، ئايلىق كىرىم، ۋاقتى ئۆتكەنلەرنى ئاپتوماتىك بەلگىلەش |
| تەڭشەكلەر / Settings | ئارخىپ، تىل، ئۇسلۇب، پۇل بىرلىكى، دىسكونت نىسبىتى، ھاۋا رايى شەھىرى، ئۇقتۇرۇش، CO₂ كوئېففىتسېنتى، ئۇلىنىشلار (SolarEdge، FusionSolar، Webhook)، JSON چىقىرىش/كىرگۈزۈش |

PDF: تالون ۋە دوكلات بەتنىڭ ئۆزىدىن رەسىمگە ئايلاندۇرۇلۇپ A4 بەتلەرگە بۆلۈنىدۇ، شۇڭا ئۇيغۇرچە/ئەرەبچە RTL توغرا چىقىدۇ.
Invoice and report PDFs render the printable DOM (html-to-image → jsPDF, loaded on demand), so Uyghur/Arabic shaping and RTL match the screen.

ئۈستى بالداق: ئومۇمىي ئىزدەش (Ctrl+K)، ئۇقتۇرۇشلار، كالېندار (ۋەزىپە ۋە تالون مۆھلەتلىرى)، تىل ۋە ئۇسلۇب ئالماشتۇرۇش.

## Supabase نى ئۇلاش / Connecting Supabase

1. Supabase دا يېڭى تۈر (project) قۇرۇڭ.
2. SQL Editor دا `supabase/schema.sql` نى ئىجرا قىلىڭ.
3. `.env.example` نى `.env` قىلىپ كۆچۈرۈپ، `VITE_SUPABASE_URL` ۋە `VITE_SUPABASE_ANON_KEY` نى تولدۇرۇڭ.
4. `npm run dev` — سىستېما ئاپتوماتىك Supabase غا ئالمىشىدۇ؛ كىرىش Supabase Auth ئارقىلىق بولىدۇ. سانداندا ئىستانسا بولمىسا سىناق سانلىق مەلۇماتى بىلەن تولدۇرۇلىدۇ.

No code changes are needed: `src/lib/repo.ts` picks Supabase when the env vars exist.
Older browser data (demo mode) is migrated automatically: ROI fields, alert rules and integrations get their defaults.

## Edge Functions — ھەقىقىي سانلىق مەلۇمات ۋە ئاگاھلاندۇرۇش / real telemetry and alerts

`supabase/functions/` دا ئۈچ Deno فۇنكسىيىسى بار / three Deno functions:

| Function | ئىقتىدارى / What it does |
|---|---|
| `ingest` | `POST {SUPABASE_URL}/functions/v1/ingest`، بېشى `x-ingest-token`. Body `{ ts?, powerKw, energyKwh?, devices?: [{ serial, status, efficiency?, health? }] }` → writes `readings`, updates devices of that site by serial, sets the integration status. `energyKwh` = energy produced so far today. |
| `sync-vendors` | SolarEdge (`/site/{id}/overview`) ۋە FusionSolar (`/thirdData/login` → `getStationRealKpi`، `getDevList` + `getDevRealKpi`) دىن سانلىق مەلۇمات تارتىدۇ. Reads secrets from `integration_secrets` with the service role, writes `readings`, sets status / last error. |
| `evaluate-alerts` | `alert_rules` نى مۇلازىمېتىردا تەكشۈرىدۇ (ئوخشاش قائىدە، 6 سائەتلىك جىملىق). `site_yield_below` uses today's `readings.energy_kwh` (no simulator on the server); sites without readings are skipped. |

ئورنىتىش / Deploy (Supabase CLI):

```bash
supabase link --project-ref <project-ref>
supabase functions deploy ingest --no-verify-jwt   # devices authenticate with x-ingest-token
supabase functions deploy sync-vendors
supabase functions deploy evaluate-alerts
# optional: local time zone for "after 14:00" / "today" (default UTC), and a shared cron secret
supabase secrets set ALERTS_TIMEZONE=Asia/Urumqi CRON_SECRET=<random-string>
```

`SUPABASE_URL` ۋە `SUPABASE_SERVICE_ROLE_KEY` ئاپتوماتىك بېرىلىدۇ / are provided automatically.

ۋاقىت جەدۋىلى / Scheduling with pg_cron (Database → Extensions: enable `pg_cron` and `pg_net`), then in the SQL editor:

```sql
select vault.create_secret('<SERVICE_ROLE_KEY>', 'service_role_key');
select vault.create_secret('<CRON_SECRET>', 'cron_secret');   -- only if you set CRON_SECRET

select cron.schedule('solarpulse-sync-vendors', '*/10 * * * *', $$
  select net.http_post(
    url := 'https://<project-ref>.supabase.co/functions/v1/sync-vendors',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key'),
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')),
    body := '{}'::jsonb)
$$);

select cron.schedule('solarpulse-evaluate-alerts', '*/5 * * * *', $$
  select net.http_post(
    url := 'https://<project-ref>.supabase.co/functions/v1/evaluate-alerts',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key'),
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')),
    body := '{}'::jsonb)
$$);
```

سىناق ھالىتىدە ئاگاھلاندۇرۇش قائىدىلىرى توركۆرگۈچتە تەكشۈرۈلىدۇ، ئۇلىنىش مەخپىي ئۇچۇرلىرى ساقلانمايدۇ.
In demo mode alert rules run in the browser (on load and every 3 minutes) and integration secrets are never stored.
The vendor calls follow SolarEdge's and Huawei's published APIs but have not been tested against live accounts.

## قۇرۇلما / Structure

```
src/
  lib/sim.ts        ھاسىلات سىمۇلياتورى (ھەممە سان مۇشۇنىڭدىن ھېسابلىنىدۇ)
  lib/repo.ts       localStorage ↔ Supabase ساقلاش قەۋىتى
  lib/seed.ts       سىناق سانلىق مەلۇماتى (16 ئىستانسا، ئاگاھلاندۇرۇش قائىدىلىرى)
  lib/finance.ts    ROI: قايتۇرۇش، NPV، IRR، LCOE، نەق پۇل ئېقىمى
  lib/alerts.ts     ئاگاھلاندۇرۇش قائىدىسى باھالىغۇچ / alert rule evaluator
  lib/pdf.ts        DOM → A4 PDF
  context/          data, auth, i18n, toast, alerts
  i18n/             ug.ts, en.ts, ar.ts, tr.ts
  components/       Layout (sidebar/topbar), ui, SiteForm, SiteMap (Leaflet), Integrations
  pages/            ھەربىر بەت
supabase/schema.sql
supabase/functions/ ingest, sync-vendors, evaluate-alerts (Deno)
docs/PLATFORM.md    web / iOS / Android ئورتاق كېلىشىم / shared contract
```

ئېلېكتر ھاسىلاتى ھازىر سىمۇلياتوردىن كېلىدۇ (ئىستانسا سىغىمى، ۋاقىت، پەسىل ۋە ھاۋا رايىغا ئاساسەن).
ھەقىقىي ئىنۋېرتور سانلىق مەلۇماتى بولسا `readings` جەدۋىلىگە يېزىپ `sim.ts` دىكى فۇنكسىيىلەرنى ئالماشتۇرسىڭىز بولىدۇ.
