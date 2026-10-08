import Foundation
import SwiftUI
import SolarPulseKit

/// In-app localization that follows the language chosen in Settings (not the device language).
///
/// Strings live in `Localizable.xcstrings` (keys are the web dictionary keys, e.g. `nav.overview`).
/// Xcode compiles the catalog into one `<lang>.lproj/Localizable.strings` per language; we look the
/// key up in the bundle of the selected language and fall back to English, then to the key itself.
struct L10n: Sendable, Equatable {
    let lang: AppLanguage

    init(lang: AppLanguage) {
        self.lang = lang
    }

    func t(_ key: String) -> String {
        if let value = LocalizationBundles.shared.lookup(key, lang: lang.rawValue) { return value }
        if lang != .en, let value = LocalizationBundles.shared.lookup(key, lang: AppLanguage.en.rawValue) { return value }
        return key
    }

    /// `t("bill.generated", ["n": "16"])` — replaces `{n}` placeholders like the web.
    func t(_ key: String, _ vars: [String: String]) -> String {
        var s = t(key)
        for (k, v) in vars { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }

    var isRTL: Bool { lang.isRTL }
    var layoutDirection: LayoutDirection { lang.isRTL ? .rightToLeft : .leftToRight }
    var locale: Locale { Locale(identifier: lang.localeIdentifier) }

    // Convenience lookups for enum values (keys shared with the web dictionaries).
    func status(_ raw: String) -> String { t("status.\(raw)") }
    func siteType(_ v: SiteType) -> String { t("siteType.\(v.rawValue)") }
    func deviceType(_ v: DeviceType) -> String { t("devType.\(v.rawValue)") }
    func priority(_ v: TicketPriority) -> String { t("prio.\(v.rawValue)") }
    func reportKind(_ v: ReportKind) -> String { t("rep.\(v.rawValue)") }
    func metric(_ v: AlertMetric) -> String { t("alerts.m.\(v.rawValue)") }
    func metricHelp(_ v: AlertMetric) -> String { t("alerts.d.\(v.rawValue)") }
    func severity(_ v: AlertSeverity) -> String { t("alerts.sev.\(v.rawValue)") }
    func vendor(_ v: IntegrationVendor) -> String { t("int.v.\(v.rawValue)") }
    func integrationStatus(_ v: IntegrationStatus) -> String { t("int.status.\(v.rawValue)") }
    func weather(_ v: WeatherKind) -> String { t("wx.\(v.rawValue)") }

    /// Localised unit for an alert metric's threshold/value (web `metricUnit`).
    func metricUnit(_ v: AlertMetric) -> String {
        switch v.unit {
        case "min": return t("alerts.unit.min")
        case "d": return t("alerts.unit.days")
        default: return v.unit
        }
    }

    func greeting(hour: Int) -> String {
        hour < 12 ? t("greet.morning") : hour < 18 ? t("greet.afternoon") : t("greet.evening")
    }
}

/// Caches the per-language `.lproj` bundles. Thread-safe.
final class LocalizationBundles: @unchecked Sendable {
    static let shared = LocalizationBundles()

    private let lock = NSLock()
    private var bundles: [String: Bundle] = [:]
    private var missing: Set<String> = []

    private func bundle(for lang: String) -> Bundle? {
        lock.lock()
        defer { lock.unlock() }
        if let b = bundles[lang] { return b }
        if missing.contains(lang) { return nil }
        if let path = Bundle.main.path(forResource: lang, ofType: "lproj"), let b = Bundle(path: path) {
            bundles[lang] = b
            return b
        }
        missing.insert(lang)
        return nil
    }

    func lookup(_ key: String, lang: String) -> String? {
        guard let b = bundle(for: lang) else { return nil }
        let sentinel = "\u{1}__missing__"
        let value = b.localizedString(forKey: key, value: sentinel, table: "Localizable")
        return value == sentinel ? nil : value
    }
}

extension EnvironmentValues {
    /// The app-level localizer (follows Settings → Language).
    @Entry var l10n: L10n = L10n(lang: .en)
    /// Number/date formatter for the current language and currency.
    @Entry var fmt: Fmt = Fmt(lang: .en, currency: .USD)
}
