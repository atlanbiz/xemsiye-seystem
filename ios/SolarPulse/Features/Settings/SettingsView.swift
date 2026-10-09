import SwiftUI
import UIKit
import UserNotifications
import SolarPulseKit

/// Settings — profile, language, theme, currency, finance & impact factors, notifications, data, sign out.
struct SettingsView: View {
    @Environment(AppStore.self) private var store
    @Environment(AuthStore.self) private var auth
    @Environment(\.l10n) private var l10n
    @Environment(\.openURL) private var openURL

    @State var draft: AppSettings?
    @State var confirmReset = false
    @State var confirmSignOut = false

    private var hasChanges: Bool { draft != nil && draft != store.settings }

    var body: some View {
        Form {
            if let d = Binding($draft) {
                profileSection(d)
                preferencesSection
                factorsSection(d)
                notificationsSection
                dataSection
                Section(l10n.t("set.about")) {
                    LabeledContent(l10n.t("set.version"), value: AppConfig.appVersion)
                    LabeledContent(l10n.t("set.storage"), value: l10n.t(store.isDemo ? "set.storage.device" : "set.storage.supabase"))
                }
                Section {
                    Button(role: .destructive) { confirmSignOut = true } label: {
                        Label(l10n.t("user.logout"), systemImage: "rectangle.portrait.and.arrow.right")
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(AppBackground())
        .navigationTitle(l10n.t("set.title"))
        .toolbar {
            if hasChanges {
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("common.save")) { Task { await saveDraft() } }.fontWeight(.semibold)
                }
            }
        }
        .onAppear { if draft == nil { draft = store.settings } }
        .onChange(of: store.settings) { old, new in
            // keep the draft in sync with instantly-applied preferences
            guard var d = draft else { return }
            d.language = new.language
            d.theme = new.theme
            d.currency = new.currency
            d.city = new.city
            d.lat = new.lat
            d.lng = new.lng
            d.notifyEmail = new.notifyEmail
            d.notifyPush = new.notifyPush
            d.notifyDeviceAlerts = new.notifyDeviceAlerts
            d.notifyBilling = new.notifyBilling
            d.notifyMaintenance = new.notifyMaintenance
            if old.userName != new.userName || old.email != new.email { d = new }
            draft = d
        }
        .task { await store.refreshNotificationStatus() }
        .confirmationDialog(l10n.t("set.resetConfirm"), isPresented: $confirmReset, titleVisibility: .visible) {
            Button(l10n.t("set.reset"), role: .destructive) {
                Task {
                    await store.resetDemo()
                    draft = store.settings
                    store.show(l10n.t("set.resetDone"))
                }
            }
        }
        .confirmationDialog(l10n.t("user.logout"), isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button(l10n.t("user.logout"), role: .destructive) { Task { await auth.signOut() } }
        }
    }

    // MARK: sections

    private func profileSection(_ d: Binding<AppSettings>) -> some View {
        Section(l10n.t("set.profile")) {
            HStack(spacing: 14) {
                Text(initials(d.wrappedValue.userName))
                    .font(.title3.bold())
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(LinearGradient(colors: [Color(hex: 0xFCD34D), Color(hex: 0xF97316)], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(d.wrappedValue.userName).font(.headline)
                    Text(auth.email ?? d.wrappedValue.email).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
            TextField(l10n.t("set.fullName"), text: d.userName)
            TextField(l10n.t("set.email"), text: d.email)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            TextField(l10n.t("set.company"), text: d.company)
            Picker(l10n.t("set.role"), selection: d.role) {
                ForEach(["admin", "operator", "viewer"], id: \.self) { r in Text(l10n.t("role.\(r)")).tag(r) }
            }
        }
    }

    private var preferencesSection: some View {
        Section(l10n.t("set.preferences")) {
            Picker(selection: Binding(get: { store.settings.language }, set: { v in Task { await store.updateSettings { $0.language = v } } })) {
                ForEach(AppLanguage.allCases) { Text($0.nativeName).tag($0) }
            } label: {
                Label(l10n.t("set.language"), systemImage: "globe")
            }
            Picker(selection: Binding(get: { store.settings.theme }, set: { v in Task { await store.updateSettings { $0.theme = v } } })) {
                Text(l10n.t("set.theme.light")).tag(AppTheme.light)
                Text(l10n.t("set.theme.dark")).tag(AppTheme.dark)
                Text(l10n.t("set.theme.system")).tag(AppTheme.system)
            } label: {
                Label(l10n.t("set.theme"), systemImage: "circle.lefthalf.filled")
            }
            Picker(selection: Binding(get: { store.settings.currency }, set: { v in Task { await store.updateSettings { $0.currency = v } } })) {
                ForEach(CurrencyCode.allCases) { Text($0.rawValue).tag($0) }
            } label: {
                Label(l10n.t("set.currency"), systemImage: "dollarsign.circle")
            }
            Picker(selection: Binding(get: { store.settings.city }, set: { name in
                guard let c = WeatherService.cities.first(where: { $0.name == name }) else { return }
                Task {
                    await store.updateSettings { s in
                        s.city = c.name
                        s.lat = c.lat
                        s.lng = c.lng
                    }
                    await store.refreshWeather(force: true)
                }
            })) {
                if !WeatherService.cities.contains(where: { $0.name == store.settings.city }) {
                    Text(store.settings.city).tag(store.settings.city)
                }
                ForEach(WeatherService.cities) { c in Text(c.label(store.settings.language)).tag(c.name) }
            } label: {
                Label(l10n.t("set.city"), systemImage: "cloud.sun")
            }
        }
    }

    private func factorsSection(_ d: Binding<AppSettings>) -> some View {
        Section(l10n.t("set.environment")) {
            numberRow(l10n.t("set.discountRate"), d.discountRatePct)
            numberRow(l10n.t("set.co2Factor"), d.co2KgPerKwh)
            numberRow(l10n.t("set.treeFactor"), d.treeKgPerYear)
            numberRow(l10n.t("set.carFactor"), d.carTonsPerYear)
        }
    }

    private var notificationsSection: some View {
        Section(l10n.t("set.notifications")) {
            toggle("set.notifyEmail", store.settings.notifyEmail) { $0.notifyEmail = $1 }
            toggle("set.notifyPush", store.settings.notifyPush) { $0.notifyPush = $1 }
            toggle("set.notifyDevice", store.settings.notifyDeviceAlerts) { $0.notifyDeviceAlerts = $1 }
            toggle("set.notifyBilling", store.settings.notifyBilling) { $0.notifyBilling = $1 }
            toggle("set.notifyMaintenance", store.settings.notifyMaintenance) { $0.notifyMaintenance = $1 }
            HStack {
                Label(l10n.t("set.systemNotifications"), systemImage: "app.badge")
                Spacer()
                switch store.notificationStatus {
                case .authorized, .provisional, .ephemeral:
                    Text(l10n.t("set.permission.allowed")).foregroundStyle(Brand.green).font(.footnote)
                case .denied:
                    Button(l10n.t("set.openSystemSettings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                    .font(.footnote)
                case .notDetermined:
                    Button(l10n.t("set.permission.ask")) { Task { _ = await store.requestNotificationPermission() } }
                        .font(.footnote)
                @unknown default:
                    EmptyView()
                }
            }
        }
    }

    private var dataSection: some View {
        Section {
            NavigationLink(value: AppRoute.integrations) {
                Label(l10n.t("set.integrations"), systemImage: "point.3.connected.trianglepath.dotted")
            }
            if store.isDemo {
                Button(role: .destructive) { confirmReset = true } label: {
                    Label(l10n.t("set.reset"), systemImage: "arrow.counterclockwise")
                }
            }
        } header: {
            Text(l10n.t("set.data"))
        } footer: {
            Text(store.isDemo ? "\(l10n.t("set.resetHint")) \(l10n.t("set.supabaseHintIOS"))" : l10n.t("set.storage.supabase"))
        }
    }

    // MARK: helpers

    private func toggle(_ key: String, _ value: Bool, _ apply: @escaping @MainActor (inout AppSettings, Bool) -> Void) -> some View {
        Toggle(l10n.t(key), isOn: Binding(get: { value },
                                         set: { v in Task { await store.updateSettings { apply(&$0, v) } } }))
    }

    private func numberRow(_ title: String, _ value: Binding<Double>) -> some View {
        LabeledContent(title) {
            TextField("", value: value, format: .plainNumber)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 110)
                .environment(\.layoutDirection, .leftToRight)
        }
    }

    private func initials(_ name: String) -> String {
        let parts = name.split(whereSeparator: \.isWhitespace).prefix(2)
        let s = parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
        return s.isEmpty ? "U" : s
    }

    private func saveDraft() async {
        guard let d = draft else { return }
        await store.updateSettings { s in
            s.userName = d.userName
            s.email = d.email
            s.company = d.company
            s.role = d.role
            s.discountRatePct = max(0, d.discountRatePct)
            s.co2KgPerKwh = max(0, d.co2KgPerKwh)
            s.treeKgPerYear = max(0, d.treeKgPerYear)
            s.carTonsPerYear = max(0, d.carTonsPerYear)
        }
        store.show(l10n.t("common.saved"))
    }
}
