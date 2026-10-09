# SolarPulse for iPhone & iPad

Native SwiftUI client for SolarPulse — the same solar-fleet monitoring product as the web app in `src/`,
following the shared contract in [`docs/PLATFORM.md`](../docs/PLATFORM.md).

- Swift 6 (strict concurrency), SwiftUI + Observation, Swift Charts, MapKit for SwiftUI
- iOS / iPadOS 18.0+ — `TabView` on iPhone, `NavigationSplitView` on iPad / regular width
- Supabase (auth + PostgREST via [`supabase-swift`](https://github.com/supabase/supabase-swift)) **or** a fully offline
  demo mode on the deterministic simulator (identical numbers to the web)
- Four in-app languages — Uyghur, English, Arabic, Turkish — with RTL mirroring for ug/ar and Latin digits everywhere

## Requirements

- macOS 15 with **Xcode 16** or newer (Swift 6 toolchain, iOS 18 SDK)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) — the Xcode project is generated, not committed

## Build & run

```sh
brew install xcodegen
cd ios
xcodegen
open SolarPulse.xcodeproj
```

Pick the **SolarPulse** scheme, an iPhone or iPad simulator, and run. Without Supabase credentials the app
starts in **demo mode**: sign in with the pre-filled credentials (any email + a 6+ character password works).

Swift Package dependencies (supabase-swift, the local `SolarPulseKit` package) resolve automatically on first open.

## Project layout

```
ios/
├── project.yml                 XcodeGen spec (app + SolarPulseTests, SPM deps)
├── Config.xcconfig             SUPABASE_URL / SUPABASE_ANON_KEY build settings (empty = demo mode)
├── Secrets.example.xcconfig    copy to Secrets.xcconfig (git-ignored) with your keys
├── SolarPulseKit/              platform-independent logic (Swift package, macOS + iOS)
│   ├── Sources/SolarPulseKit/  Models, DayMath, Simulator, Seed, Finance (ROI), AlertEvaluator,
│   │                           Formatters, ReportBuilder
│   └── Tests/                  simulator/seed parity with the web, ROI, IRR, alert semantics, formatters
├── SolarPulse/
│   ├── App/                    @main app, adaptive shell (tabs / split view), routes
│   ├── Core/                   localization (L10n), theme tokens, shared components, PDF exporter
│   ├── Data/                   AppStore (state + CRUD + alerts), repositories (JSON file / Supabase),
│   │                           auth, weather (Open-Meteo), local notifications
│   ├── Features/               Login, Overview, Sites (+detail/form), Map, Devices, Maintenance,
│   │                           Billing (+invoice PDF), Analytics, Reports (+PDF/CSV), Finance, Alerts,
│   │                           Notifications, Settings, Integrations
│   └── Resources/              Assets (AppIcon, AccentColor), Localizable.xcstrings, *.lproj/InfoPlist.strings
└── SolarPulseTests/            app-level tests (localization lookup, deep links, demo persistence)
```

## Configuring Supabase

1. Run `supabase/schema.sql` in your Supabase project's SQL editor (it is shared with the web and Android apps).
2. `cp Secrets.example.xcconfig Secrets.xcconfig` and fill in the project URL and anon key
   (Supabase → Project Settings → API). `//` starts a comment in `.xcconfig` files, so keep the `https:/$()/…`
   form shown in the example.
3. Rebuild. `Config.xcconfig` includes `Secrets.xcconfig`; the values reach the app through the Info.plist keys
   `SUPABASE_URL` and `SUPABASE_ANON_KEY`. Empty values → demo mode.

With Supabase:

- Sign-in / sign-up use Supabase Auth (email + password; sign-up may require email confirmation).
- Rows are read/written through PostgREST with the snake_case columns of the schema. An empty project is seeded
  with the demo dataset on first load, exactly like the web.
- Alert rules are evaluated server-side by the `evaluate-alerts` Edge Function; the app shows the resulting
  `notifications` rows.
- Integration secrets are only ever sent through `rpc('set_integration_secret', …)` and never read back.
- Sites with a `readings` row from the last 15 minutes show the real `powerKw` with a **Live** badge.

## Demo mode

- The dataset is a 1:1 port of `src/lib/seed.ts` + `src/lib/sim.ts` (FNV-1a hash with 32-bit wrapping multiply,
  the same `rand`, solar curve, season and weather factors, performance ratio 0.82, status factors), so every site
  shows the same kWh as on the web for the same date and time zone.
- Data is stored as JSON in `Application Support/SolarPulse/solarpulse-db-v1.json`. *Settings → Reset demo data*
  restores the seed.
- Alert rules are evaluated on the device on launch, when the app returns to the foreground, every 3 minutes while
  open, on pull-to-refresh and with **Check now**. Each rule fires at most once per 6 hours, inserts a notification
  and — if the user allowed it — posts a local notification. Permission is requested only from the explicit
  "Turn on notifications" card / Settings button, never at launch.

## Localization

`SolarPulse/Resources/Localizable.xcstrings` uses the web dictionary keys (`nav.overview`, `bill.markPaid`, …).
Wording comes from `src/i18n/{en,ug,ar,tr}.ts`; iOS-only strings were added in all four languages. The language
is chosen in-app (Settings → Language, or the globe menu on the login screen): `L10n` looks keys up in the
selected language's `.lproj` bundle, and the root view sets `layoutDirection` and `locale` so the whole UI —
including the PDFs — mirrors for Uyghur and Arabic. Charts keep a left-to-right time axis, like the web.

After changing the web dictionaries, regenerate the catalog from the repository root with
`ios/scripts/sync-strings.sh` (needs `npm install` for esbuild). iOS-only strings live in
`ios/scripts/ios_strings.json` with all four languages.

To refresh the catalog after the web dictionaries change, regenerate it from `src/i18n/*.ts`
(keys are `extractionState: manual`; `SWIFT_EMIT_LOC_STRINGS` is off so Xcode does not add extracted keys).

## Tests

- **SolarPulseKit** (pure logic, also runs on macOS/Linux with a Swift 6 toolchain):

  ```sh
  cd ios/SolarPulseKit
  swift test
  ```

  Covers simulator parity (pinned values computed by running the web's `sim.ts` / `seed.ts` / `finance.ts` /
  `alerts.ts` with Node under `TZ=UTC` and a frozen clock — e.g. `siteDayKwh(site-01, 2026-06-21) = 4545.852236393882`),
  the seeded devices/invoices, ROI formulas (hand-computed case), IRR, payback, LCOE, portfolio aggregation, every
  alert metric, the 6-hour cooldown, and number/date formatting.
- **App tests**: in Xcode, Product → Test (⌘U) on the SolarPulse scheme, or

  ```sh
  xcodebuild test -project SolarPulse.xcodeproj -scheme SolarPulse -destination 'platform=iOS Simulator,name=iPhone 16'
  ```

## App Store notes

- Bundle identifier: `com.solarpulse.app` (placeholder — change `PRODUCT_BUNDLE_IDENTIFIER` in `project.yml`).
- Signing: set `DEVELOPMENT_TEAM` in `project.yml` (or in Xcode → Signing & Capabilities), then re-run `xcodegen`.
- Version: `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `project.yml`.
- `ITSAppUsesNonExemptEncryption = NO` is set (HTTPS only).
- The app uses no location, camera or tracking APIs; local notifications need no Info.plist usage string.
- App icon: single 1024×1024 universal icon (`Assets.xcassets/AppIcon.appiconset`), generated from a script —
  lightning bolt on the brand-blue gradient.
- Archive with Product → Archive (Release configuration) and upload through the Organizer.
