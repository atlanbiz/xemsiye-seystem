import SwiftUI
import UserNotifications
import SolarPulseKit

@main
struct SolarPulseApp: App {
    @State var store: AppStore
    @State var auth = AuthStore()

    init() {
        _store = State(initialValue: AppStore(repo: Backend.shared.makeRepository()))
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
    }

    var body: some Scene {
        WindowGroup {
            LocalizedRoot()
                .environment(store)
                .environment(auth)
        }
    }
}

/// Applies the in-app language (strings, locale, RTL mirroring) and theme to the whole hierarchy.
struct LocalizedRoot: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        let l10n = store.l10n
        RootView()
            .environment(\.l10n, l10n)
            .environment(\.fmt, store.fmt)
            .environment(\.locale, l10n.locale)
            .environment(\.layoutDirection, l10n.layoutDirection)
            .preferredColorScheme(colorScheme(store.settings.theme))
            .tint(Brand.primary)
            .animation(.easeInOut(duration: 0.25), value: store.settings.language)
    }

    private func colorScheme(_ theme: AppTheme) -> ColorScheme? {
        switch theme {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}
