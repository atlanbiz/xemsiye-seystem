import Foundation
import Observation
import SwiftUI
import UserNotifications
import SolarPulseKit

/// App-wide state: the dataset, the live clock, CRUD helpers and alert evaluation.
@MainActor
@Observable
final class AppStore {
    private(set) var db = DB()
    private(set) var isLoaded = false
    private(set) var loadError: String?
    var isRefreshing = false
    var toast: ToastMessage?
    /// Live clock (5 s) driving current power and the energy flow.
    private(set) var now = Date()
    /// Latest real telemetry per site (Supabase `readings`, last 15 min).
    private(set) var liveReadings: [String: Reading] = [:]
    private(set) var weather: WeatherNow
    private(set) var notificationStatus: UNAuthorizationStatus = .notDetermined
    /// Bumps whenever a mutation succeeds (drives success haptics).
    private(set) var savedCounter = 0

    let repo: any Repository
    let sim: Simulator

    @ObservationIgnored private var clockTask: Task<Void, Never>?
    @ObservationIgnored private var toastTask: Task<Void, Never>?
    @ObservationIgnored private var lastWeatherFetch: Date?
    @ObservationIgnored private var lastEvaluation: Date?

    init(repo: any Repository, sim: Simulator = .live) {
        self.repo = repo
        self.sim = sim
        self.weather = WeatherNow(sim: sim.simWeather())
        // Before any data is loaded (login screen) use the remembered / device language.
        let language = AppStore.preferredLanguage()
        db.settings.language = language
        UserDefaults.standard.set(language.rawValue, forKey: AppStore.languageKey)
        if let theme = UserDefaults.standard.string(forKey: AppStore.themeKey).flatMap(AppTheme.init(rawValue:)) {
            db.settings.theme = theme
        }
    }

    // MARK: - Per-device UI preferences

    private static let languageKey = "solarpulse.ui.language"
    private static let themeKey = "solarpulse.ui.theme"

    /// Last language chosen on this device, else the device language when supported, else English.
    static func preferredLanguage() -> AppLanguage {
        if let saved = UserDefaults.standard.string(forKey: languageKey).flatMap(AppLanguage.init(rawValue:)) { return saved }
        for id in Locale.preferredLanguages {
            if let lang = AppLanguage(rawValue: String(id.prefix(2)).lowercased()) { return lang }
        }
        return .en
    }

    /// The UI language/theme follow what was last picked on this device (also on the login screen).
    private func applyDevicePreferences(to settings: inout AppSettings) {
        if let saved = UserDefaults.standard.string(forKey: AppStore.languageKey).flatMap(AppLanguage.init(rawValue:)) {
            settings.language = saved
        }
        if let saved = UserDefaults.standard.string(forKey: AppStore.themeKey).flatMap(AppTheme.init(rawValue:)) {
            settings.theme = saved
        }
    }

    var isDemo: Bool { !repo.isRemote }
    var settings: AppSettings { db.settings }
    var l10n: L10n { L10n(lang: db.settings.language) }
    var fmt: Fmt { Fmt(lang: db.settings.language, currency: db.settings.currency, days: sim.days) }
    var unreadCount: Int { db.notifications.filter { !$0.read }.count }

    // MARK: - Loading

    func start() async {
        guard !isLoaded else { return }
        await load()
        startClock()
        await refreshNotificationStatus()
        await refreshWeather(force: true)
        await refreshLive()
        await evaluateAlertsIfDemo()
    }

    func load() async {
        do {
            var loaded = try await repo.load()
            let changed = loaded.refreshOverdue(todayKey: sim.todayKey)
            if !changed.isEmpty { try? await repo.upsertMany(changed) }
            applyDevicePreferences(to: &loaded.settings)
            db = loaded
            loadError = nil
        } catch {
            loadError = error.localizedDescription
            if !isLoaded { db = SeedBuilder(sim: sim).build() }
        }
        withAnimation(.easeOut(duration: 0.35)) { isLoaded = true }
    }

    /// Pull-to-refresh / foreground: reload (Supabase), refresh telemetry and weather, evaluate rules (demo).
    func refresh() async {
        isRefreshing = true
        defer { isRefreshing = false }
        if !isDemo { await load() } else {
            let changed = db.refreshOverdue(todayKey: sim.todayKey)
            if !changed.isEmpty { try? await repo.upsertMany(changed) }
        }
        now = Date()
        await refreshLive()
        await refreshWeather(force: false)
        await evaluateAlertsIfDemo(force: true)
    }

    private func startClock() {
        clockTask?.cancel()
        clockTask = Task { [weak self] in
            var ticks = 0
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                guard let self else { return }
                self.now = Date()
                ticks += 1
                if ticks % 12 == 0 { await self.refreshLive() }          // every minute
                if ticks % 36 == 0 { await self.evaluateAlertsIfDemo() }  // every 3 minutes, like the web
                if ticks % 180 == 0 { await self.refreshWeather(force: true) } // every 15 minutes
            }
        }
    }

    func refreshWeather(force: Bool) async {
        if !force, let last = lastWeatherFetch, Date().timeIntervalSince(last) < 15 * 60 { return }
        lastWeatherFetch = Date()
        if let live = await WeatherService.fetch(lat: db.settings.lat, lng: db.settings.lng) {
            weather = live
        } else {
            weather = WeatherNow(sim: sim.simWeather())
        }
    }

    func refreshLive() async {
        guard !isDemo else { return }
        if let readings = try? await repo.latestReadings(since: Date().addingTimeInterval(-15 * 60)) {
            liveReadings = readings
        }
    }

    // MARK: - Simulator helpers (with live telemetry override)

    /// Real power when a reading from the last 15 minutes exists, else simulated.
    func currentKw(_ site: Site) -> Double {
        if let r = liveReadings[site.id], let ts = ISODate.parse(r.ts), now.timeIntervalSince(ts) < 15 * 60 {
            return r.powerKw
        }
        return sim.siteKw(site, day: sim.days.dayKey(now), hour: sim.days.hourOfDay(now))
    }

    func isLive(_ site: Site) -> Bool {
        guard let r = liveReadings[site.id], let ts = ISODate.parse(r.ts) else { return false }
        return now.timeIntervalSince(ts) < 15 * 60
    }

    /// Fleet output now (simulated with jitter, real readings substituted per site).
    func fleetKw() -> Double {
        let simulated = sim.liveKw(db.sites.filter { !isLive($0) }, at: now)
        let real = db.sites.filter { isLive($0) }.reduce(0.0) { $0 + (liveReadings[$1.id]?.powerKw ?? 0) }
        return simulated + real
    }

    func site(_ id: String) -> Site? { db.sites.first { $0.id == id } }
    func siteName(_ id: String) -> String { site(id)?.name ?? "—" }

    // MARK: - Toasts

    func show(_ text: String, tone: ToastMessage.Tone = .success) {
        toastTask?.cancel()
        withAnimation(.spring(duration: 0.35)) { toast = ToastMessage(text: text, tone: tone) }
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2.4))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut) { self?.toast = nil }
        }
    }

    private func report(_ error: Error) {
        show("\(l10n.t("common.error")): \(error.localizedDescription)", tone: .error)
    }

    // MARK: - CRUD (optimistic, like the web data context)

    private func applyLocal<T: TableRow>(_ row: T) {
        var list = db[keyPath: T.dbKeyPath]
        if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.insert(row, at: 0) }
        db[keyPath: T.dbKeyPath] = list
    }

    /// - Parameter haptic: play the success haptic (off for background bookkeeping like "mark read").
    func save<T: TableRow>(_ row: T, haptic: Bool = true) async {
        withAnimation(.snappy) { applyLocal(row) }
        do {
            try await repo.upsert(row)
            if haptic { savedCounter += 1 }
        } catch { report(error) }
    }

    func saveMany<T: TableRow>(_ rows: [T], haptic: Bool = true) async {
        withAnimation(.snappy) { for r in rows { applyLocal(r) } }
        do {
            try await repo.upsertMany(rows)
            if haptic { savedCounter += 1 }
        } catch { report(error) }
    }

    func delete<T: TableRow>(_ type: T.Type, id: String) async {
        withAnimation(.snappy) { db[keyPath: T.dbKeyPath].removeAll { $0.id == id } }
        do { try await repo.remove(type, id: id) } catch { report(error) }
    }

    /// Deleting a site also deletes its devices, tickets, invoices, rules and integrations.
    func deleteSite(_ id: String) async {
        for d in db.devices where d.siteId == id { await delete(Device.self, id: d.id) }
        for t in db.tickets where t.siteId == id { await delete(Ticket.self, id: t.id) }
        for i in db.invoices where i.siteId == id { await delete(Invoice.self, id: i.id) }
        for r in db.alertRules where r.siteId == id { await delete(AlertRule.self, id: r.id) }
        for x in db.integrations where x.siteId == id { await delete(Integration.self, id: x.id) }
        await delete(Site.self, id: id)
    }

    func updateSettings(_ mutate: (inout AppSettings) -> Void) async {
        var s = db.settings
        mutate(&s)
        guard s != db.settings else { return }
        if s.language != db.settings.language { UserDefaults.standard.set(s.language.rawValue, forKey: AppStore.languageKey) }
        if s.theme != db.settings.theme { UserDefaults.standard.set(s.theme.rawValue, forKey: AppStore.themeKey) }
        db.settings = s
        guard isLoaded else { return } // login screen: remembered locally, applied after load
        do { try await repo.saveSettings(s) } catch { report(error) }
    }

    /// Inserts an in-app notification (respects "In-app notifications").
    func notify(title: String, body: String, kind: NotificationKind, link: String?) async {
        guard db.settings.notifyPush else { return }
        let n = AppNotification(id: makeId("ntf-"), title: title, body: body, kind: kind, link: link, read: false,
                                createdAt: ISODate.string(Date()))
        await save(n, haptic: false)
    }

    func markAllRead() async {
        let unread = db.notifications.filter { !$0.read }.map { n -> AppNotification in
            var x = n
            x.read = true
            return x
        }
        guard !unread.isEmpty else { return }
        await saveMany(unread, haptic: false)
    }

    func clearNotifications() async {
        for n in db.notifications { await delete(AppNotification.self, id: n.id) }
    }

    func resetDemo() async {
        var seed = SeedBuilder(sim: sim).build()
        let s = db.settings
        seed.settings.language = s.language
        seed.settings.theme = s.theme
        seed.settings.userName = s.userName
        seed.settings.email = s.email
        sim.clearCache()
        db = seed
        do { try await repo.replaceAll(seed) } catch { report(error) }
        lastEvaluation = nil
    }

    // MARK: - Alerts

    func refreshNotificationStatus() async {
        notificationStatus = await LocalNotifier.authorizationStatus()
    }

    func requestNotificationPermission() async -> Bool {
        let granted = await LocalNotifier.requestAuthorization()
        await refreshNotificationStatus()
        return granted
    }

    /// Demo mode only: evaluates the rules on this device (Supabase runs the Edge Function on a cron).
    @discardableResult
    func evaluateAlertsIfDemo(force: Bool = false) async -> Int {
        guard isDemo, isLoaded else { return 0 }
        if !force, let last = lastEvaluation, Date().timeIntervalSince(last) < 60 { return 0 }
        lastEvaluation = Date()
        let evalNow = Date()
        let evaluator = AlertEvaluator(sim: sim)
        let firings = evaluator.evaluate(rules: db.alertRules, sites: db.sites, devices: db.devices,
                                         invoices: db.invoices, now: evalNow)
        let t = l10n
        let f = fmt
        for firing in firings {
            let unit = t.metricUnit(firing.rule.metric)
            let decimals = firing.rule.metric == .siteYieldBelow ? 2 : 0
            let body = AlertEvaluator.describe(firing.matches) { m in
                "\(f.num(m.value ?? 0, decimals))\(unit == "%" ? "" : " ")\(unit)"
            }
            var rule = firing.rule
            rule.lastTriggeredAt = ISODate.string(evalNow)
            await save(rule, haptic: false)
            let n = AlertEvaluator.notification(for: firing, body: body, id: makeId("ntf-"), now: evalNow)
            if db.settings.notifyPush { await save(n, haptic: false) }
            if db.settings.notifyDeviceAlerts && notificationStatus == .authorized {
                LocalNotifier.post(id: n.id, title: n.title, body: body)
            }
        }
        return firings.count
    }
}

/// Deep links stored in notifications use the web's paths (`/sites/site-11`, `/billing?open=…`).
enum DeepLink {
    static func route(for link: String?) -> AppRoute? {
        guard let link, let comps = URLComponents(string: link) else { return nil }
        let parts = comps.path.split(separator: "/").map(String.init)
        let query = Dictionary((comps.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { a, _ in a })
        guard let first = parts.first else { return nil }
        switch first {
        case "sites": return parts.count > 1 ? .site(parts[1]) : .section(.sites)
        case "devices": return .section(.devices)
        case "billing": return query["open"].map { AppRoute.invoice($0) } ?? .section(.billing)
        case "maintenance": return query["open"].map { AppRoute.ticket($0) } ?? .section(.maintenance)
        case "reports": return .section(.reports)
        case "analytics": return .section(.analytics)
        case "finance": return .section(.finance)
        case "alerts": return .section(.alerts)
        case "map": return .section(.map)
        default: return nil
        }
    }
}
