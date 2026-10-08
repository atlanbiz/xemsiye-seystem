# SolarPulse platform contract

SolarPulse ships as three clients over one backend:

| Client | Path | Stack |
|---|---|---|
| Web | `src/` | React 18 + TypeScript + Vite + Tailwind 4 |
| iOS / iPadOS | `ios/` | Swift 6, SwiftUI, Swift Charts, MapKit (iOS 18+) |
| Android | `android/` | Kotlin 2, Jetpack Compose (Material 3), minSdk 26 |

All three read and write the same Supabase project (`supabase/schema.sql`) and,
without Supabase credentials, run a **demo mode** on a local deterministic
simulator so every screen has data. This document is the shared contract:
when one client changes a field, a metric or a formula, the others follow.

## 1. Data model

Column names in Supabase are the snake_case form of the fields below.
Ids are text (`site-01`, `dev-…`, `rule-…`); dates are `YYYY-MM-DD`; timestamps are ISO-8601 UTC.

| Table | Fields |
|---|---|
| `sites` | id, name, location, type (`residential`/`commercial`/`industrial`/`utility`), status (`active`/`idle`/`offline`/`maintenance`), capacityKw, batteryKwh, pricePerKwh, customer, installDate, lat, lng, **systemCost, annualOpex, degradationPct, tariffEscalationPct** |
| `devices` | id, siteId, name, type (`inverter`/`battery`/`panel`/`meter`/`sensor`), model, serial, status (`online`/`warning`/`offline`), health 0–100, efficiency 0–100, firmware, installedAt, lastSeen |
| `tickets` | id, siteId, deviceId?, title, description, priority (`low`/`medium`/`high`/`critical`), status (`open`/`in_progress`/`resolved`), assignee, dueDate, createdAt |
| `invoices` | id, number, siteId, customer, period `YYYY-MM`, energyKwh, rate, amount, status (`paid`/`pending`/`overdue`), issuedAt, dueAt, paidAt? |
| `notifications` | id, title, body, kind (`info`/`success`/`warning`/`danger`), link?, read, createdAt |
| `reports` | id, kind (`energy`/`financial`/`devices`/`maintenance`/`environment`), title, from, to, siteIds[], createdAt |
| `settings` (single row id=`settings`) | userName, email, role (`admin`/`operator`/`viewer`), company, language (`ug`/`en`/`ar`/`tr`), theme (`light`/`dark`/`system`), currency (`USD`/`CNY`/`EUR`), city, lat, lng, co2KgPerKwh, treeKgPerYear, carTonsPerYear, notify*, **discountRatePct** |
| `readings` | siteId, ts, powerKw, energyKwh? — real telemetry written by integrations |
| **`alert_rules`** | id, name, metric, threshold, siteId? (null = all sites), severity (`warning`/`danger`), enabled, lastTriggeredAt?, createdAt |
| **`integrations`** | id, vendor (`solaredge`/`fusionsolar`/`webhook`), name, siteId, externalId, config (json, non-secret), ingestToken, status (`pending`/`ok`/`error`), lastSyncAt?, lastError?, createdAt |
| `integration_secrets` | write-only from clients via `rpc('set_integration_secret', {p_integration_id, p_secret})` |

Defaults for the new site fields when absent: systemCost = `capacityKw × 900`,
annualOpex = `systemCost × 1.5 %`, degradationPct = 0.5, tariffEscalationPct = 2.
Default discountRatePct = 6.

## 2. Simulator (demo mode and fallback)

Port `src/lib/sim.ts` exactly (same FNV-1a `hash`, same `rand`, `solarCurve`,
`seasonFactor`, `weatherFactor`, performance ratio 0.82, status factors) and the
seed in `src/lib/seed.ts` (16 sites, same names/coordinates). Same inputs must
give the same kWh on every platform, so a site shows identical numbers on web,
iPhone and Android.

When a site has `readings` within the last 15 minutes, clients show the real
`powerKw` as current power (badge "Live") instead of the simulated value.

## 3. Features (all clients)

1. **Site map** — every site as a pin coloured by status (active green `#22c55e`, idle amber `#f59e0b`,
   offline red `#ef4444`, maintenance violet `#8b5cf6`), pin size by capacity, tap → site card → site detail.
   Web: Leaflet + OpenStreetMap tiles. iOS: MapKit. Android: osmdroid (OSM tiles, no API key).
2. **PDF export** — invoice PDF and report PDF (the 5 report kinds), localized, RTL-correct for ug/ar.
   Web renders the printable DOM to PDF; iOS uses `ImageRenderer`/`UIGraphicsPDFRenderer`;
   Android uses `android.graphics.pdf.PdfDocument` with `StaticLayout` (handles Arabic shaping and bidi).
3. **Alert rules** — CRUD screen. Metrics:

   | metric | fires when | threshold unit |
   |---|---|---|
   | `site_yield_below` | site's today kWh ÷ capacityKw < threshold, checked after 14:00 local | kWh/kWp |
   | `site_offline` | site.status = `offline` | — |
   | `device_efficiency_below` | device.efficiency < threshold | % |
   | `device_health_below` | device.health < threshold | % |
   | `device_offline_minutes` | device.status = `offline` and now − lastSeen > threshold | minutes |
   | `invoice_overdue_days` | invoice.status = `overdue` and today − dueAt > threshold | days |

   A rule fires at most once per 6 hours (`lastTriggeredAt`); firing inserts a notification
   (kind = severity, title = rule name, body names the site/device/invoice, link to it).
   Demo mode evaluates on the client; with Supabase the `evaluate-alerts` Edge Function does it on a cron.
4. **Vendor integrations** — settings screen to connect a site to SolarEdge (API key + site id),
   Huawei FusionSolar (username + system code + base URL) or a generic webhook / MQTT bridge
   (shows `POST {SUPABASE_URL}/functions/v1/ingest` with header `x-ingest-token`). Status, last sync,
   last error. Secrets go only through `set_integration_secret`.
5. **Financial analysis (ROI)** — per site and portfolio, 25-year horizon:
   - year-1 energy `E1` = simulated kWh over the last 365 days (or `capacityKw × 365 × avg daily yield`)
   - `E_y = E1 × (1 − degradationPct/100)^(y−1)`
   - `price_y = pricePerKwh × (1 + tariffEscalationPct/100)^(y−1)`
   - cash flow `CF_y = E_y × price_y − annualOpex`, `CF_0 = −systemCost`
   - **payback years** (fractional, first year cumulative CF ≥ 0), **ROI %** = (Σ CF_1..25 − systemCost) / systemCost × 100,
     **NPV** at discountRatePct, **IRR** (bisection), **LCOE** = (systemCost + Σ opex discounted) / Σ E_y discounted,
     annual savings, cumulative cash-flow chart.

## 4. Localization

Four languages: `ug` Uyghur (RTL), `en` English, `ar` Arabic (RTL), `tr` Turkish.
The web dictionaries in `src/i18n/*.ts` are the source of truth for wording; mobile apps reuse the same
translations (iOS String Catalog, Android `values-*/strings.xml`). Numbers use Latin digits in every language.
Language is switchable in-app and the layout mirrors for RTL.

## 5. Design language

Premium, calm, data-dense. Brand blue scale `#eff5ff #dbe8fe #bfd5fe #93b8fd #6094fa #3b74f6 #2557eb #1d44d8`
(primary `#2557eb`), energy green `#22c55e`, solar amber `#f59e0b`, danger `#ef4444`.
Light: soft sky-to-white gradient background, white cards with 20–24 pt radius and subtle shadow.
Dark: slate-900 background, slate-800 cards. Full dark mode, Dynamic Type / font scaling, haptics on key actions,
skeleton loading, pull-to-refresh, empty states. App icon: lightning bolt on brand-blue rounded square.
