import SwiftUI
import UIKit
import SolarPulseKit

extension Color {
    /// `Color(hex: 0x2557EB)`
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: opacity)
    }

    /// A colour that resolves differently in light and dark mode.
    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {
    convenience init(rgb: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255,
                  green: CGFloat((rgb >> 8) & 0xFF) / 255,
                  blue: CGFloat(rgb & 0xFF) / 255,
                  alpha: alpha)
    }
}

/// Design tokens — PLATFORM.md §5.
enum Brand {
    static let b50 = Color(hex: 0xEFF5FF)
    static let b100 = Color(hex: 0xDBE8FE)
    static let b200 = Color(hex: 0xBFD5FE)
    static let b300 = Color(hex: 0x93B8FD)
    static let b400 = Color(hex: 0x6094FA)
    static let b500 = Color(hex: 0x3B74F6)
    static let b600 = Color(hex: 0x2557EB)
    static let b700 = Color(hex: 0x1D44D8)

    static let primary = b600
    static let chart = b500
    static let green = Color(hex: 0x22C55E)
    static let amber = Color(hex: 0xF59E0B)
    static let danger = Color(hex: 0xEF4444)
    static let violet = Color(hex: 0x8B5CF6)
    static let sky = Color(hex: 0x0EA5E9)
    static let slate = Color(hex: 0x94A3B8)

    static let palette: [Color] = [b500, green, amber, violet, sky, danger]

    static var gradient: LinearGradient { LinearGradient(colors: [b500, b700], startPoint: .topLeading, endPoint: .bottomTrailing) }
    static var heroGradient: LinearGradient { LinearGradient(colors: [Color(hex: 0x3B74F6), Color(hex: 0x1D44D8), Color(hex: 0x172554)],
                                             startPoint: .topLeading, endPoint: .bottomTrailing) }
}

enum Theme {
    /// White cards in light mode, slate-800 in dark mode.
    static let card = Color.adaptive(light: 0xFFFFFF, dark: 0x1E293B)
    static let cardStroke = Color.adaptive(light: 0xE2E8F0, dark: 0x334155)
    static let subtleFill = Color.adaptive(light: 0xF1F5F9, dark: 0x0F172A)
    static let backgroundTop = Color.adaptive(light: 0xE6EFFE, dark: 0x0F172A)
    static let backgroundBottom = Color.adaptive(light: 0xFFFFFF, dark: 0x0B1120)
    static let cardRadius: CGFloat = 22
}

/// Soft sky-to-white gradient in light mode, slate-900 in dark mode.
struct AppBackground: View {
    var body: some View {
        LinearGradient(colors: [Theme.backgroundTop, Theme.backgroundBottom], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
    }
}

struct CardModifier: ViewModifier {
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .fill(Theme.card)
                    .shadow(color: .black.opacity(0.06), radius: 14, x: 0, y: 6)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .strokeBorder(Theme.cardStroke.opacity(0.6), lineWidth: 0.5)
            )
    }
}

extension View {
    func card(padding: CGFloat = 16) -> some View { modifier(CardModifier(padding: padding)) }

    /// Charts read left-to-right on every language (same as the web's `dir="ltr"` charts).
    func ltrChart() -> some View { environment(\.layoutDirection, .leftToRight) }

    /// Standard screen chrome: gradient background behind a scroll view.
    func screenBackground() -> some View {
        background(AppBackground())
            .scrollContentBackground(.hidden)
    }
}

// MARK: - Semantic colours

extension SiteStatus {
    var color: Color {
        switch self {
        case .active: return Brand.green
        case .idle: return Brand.amber
        case .offline: return Brand.danger
        case .maintenance: return Brand.violet
        }
    }
}

extension DeviceStatus {
    var color: Color {
        switch self {
        case .online: return Brand.green
        case .warning: return Brand.amber
        case .offline: return Brand.danger
        }
    }

    var icon: String {
        switch self {
        case .online: return "checkmark.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .offline: return "xmark.circle.fill"
        }
    }
}

extension DeviceType {
    var icon: String {
        switch self {
        case .inverter: return "cpu"
        case .battery: return "battery.100percent.bolt"
        case .panel: return "solarpanel"
        case .meter: return "gauge.with.dots.needle.67percent"
        case .sensor: return "sensor"
        }
    }
}

extension TicketStatus {
    var color: Color {
        switch self {
        case .open: return Brand.b500
        case .inProgress: return Brand.violet
        case .resolved: return Brand.green
        }
    }

    var icon: String {
        switch self {
        case .open: return "circle.dotted"
        case .inProgress: return "arrow.triangle.2.circlepath"
        case .resolved: return "checkmark.circle.fill"
        }
    }
}

extension TicketPriority {
    var color: Color {
        switch self {
        case .critical: return Brand.danger
        case .high: return Brand.amber
        case .medium: return Brand.b500
        case .low: return Brand.slate
        }
    }
}

extension InvoiceStatus {
    var color: Color {
        switch self {
        case .paid: return Brand.green
        case .pending: return Brand.amber
        case .overdue: return Brand.danger
        }
    }
}

extension NotificationKind {
    var color: Color {
        switch self {
        case .info: return Brand.b500
        case .success: return Brand.green
        case .warning: return Brand.amber
        case .danger: return Brand.danger
        }
    }

    var icon: String {
        switch self {
        case .info: return "info.circle.fill"
        case .success: return "checkmark.seal.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .danger: return "bolt.trianglebadge.exclamationmark.fill"
        }
    }
}

extension IntegrationStatus {
    var color: Color {
        switch self {
        case .pending: return Brand.amber
        case .ok: return Brand.green
        case .error: return Brand.danger
        }
    }
}

extension IntegrationVendor {
    var icon: String {
        switch self {
        case .solaredge: return "sun.max.fill"
        case .fusionsolar: return "cloud.sun.fill"
        case .webhook: return "point.3.connected.trianglepath.dotted"
        }
    }
}

extension ReportKind {
    var icon: String {
        switch self {
        case .energy: return "bolt.fill"
        case .financial: return "dollarsign.circle.fill"
        case .devices: return "cpu"
        case .maintenance: return "wrench.and.screwdriver.fill"
        case .environment: return "leaf.fill"
        }
    }
}

extension WeatherKind {
    var icon: String {
        switch self {
        case .sunny: return "sun.max.fill"
        case .partly: return "cloud.sun.fill"
        case .cloudy: return "cloud.fill"
        case .fog: return "cloud.fog.fill"
        case .rain: return "cloud.rain.fill"
        case .snow: return "cloud.snow.fill"
        case .storm: return "cloud.bolt.rain.fill"
        }
    }
}

/// Health / efficiency colour thresholds used by the web (≥90 green, ≥75 amber, else red).
func healthColor(_ v: Double) -> Color {
    v >= 90 ? Brand.green : v >= 75 ? Brand.amber : Brand.danger
}
