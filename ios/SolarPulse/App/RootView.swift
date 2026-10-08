import SwiftUI
import SolarPulseKit

/// Top-level sections (sidebar on iPad, tabs + "More" on iPhone).
enum AppSection: String, CaseIterable, Identifiable, Hashable {
    case overview, sites, map, devices, maintenance, billing, analytics, reports, finance, alerts, settings

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .overview: return "nav.overview"
        case .sites: return "nav.sites"
        case .map: return "nav.map"
        case .devices: return "nav.devices"
        case .maintenance: return "nav.maintenance"
        case .billing: return "nav.billing"
        case .analytics: return "nav.analytics"
        case .reports: return "nav.reports"
        case .finance: return "nav.finance"
        case .alerts: return "nav.alerts"
        case .settings: return "nav.settings"
        }
    }

    var icon: String {
        switch self {
        case .overview: return "square.grid.2x2.fill"
        case .sites: return "building.2.fill"
        case .map: return "map.fill"
        case .devices: return "cpu.fill"
        case .maintenance: return "wrench.and.screwdriver.fill"
        case .billing: return "doc.text.fill"
        case .analytics: return "chart.xyaxis.line"
        case .reports: return "doc.richtext.fill"
        case .finance: return "chart.line.uptrend.xyaxis"
        case .alerts: return "bell.badge.fill"
        case .settings: return "gearshape.fill"
        }
    }

    /// Tabs shown directly on compact width; the rest live under "More".
    static let primaryTabs: [AppSection] = [.overview, .sites, .map, .devices]
    static let moreSections: [AppSection] = [.maintenance, .billing, .analytics, .reports, .finance, .alerts, .settings]
}

/// Values pushed on a NavigationStack.
enum AppRoute: Hashable {
    case site(String)
    case invoice(String)
    case ticket(String)
    case section(AppSection)
    case integrations
    case notifications
}

struct RootView: View {
    @Environment(AppStore.self) private var store
    @Environment(AuthStore.self) private var auth
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.l10n) private var l10n

    var body: some View {
        ZStack(alignment: .top) {
            Group {
                if auth.isRestoring {
                    SplashView()
                } else if !auth.isSignedIn {
                    LoginView()
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                } else if !store.isLoaded {
                    SkeletonDashboard()
                } else {
                    AdaptiveShell()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.35), value: auth.isSignedIn)
            .animation(.easeInOut(duration: 0.35), value: store.isLoaded)

            if let toast = store.toast {
                ToastView(toast: toast)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(10)
            }
        }
        .task { await auth.restore() }
        .task(id: auth.isSignedIn) {
            if auth.isSignedIn { await store.start() }
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, auth.isSignedIn, store.isLoaded else { return }
            Task {
                await store.refreshNotificationStatus()
                await store.refresh()
            }
        }
        .sensoryFeedback(.success, trigger: store.savedCounter)
    }
}

struct SplashView: View {
    var body: some View {
        ZStack {
            AppBackground()
            BrandMark(size: 56)
        }
    }
}

/// TabView on compact width, NavigationSplitView on regular width (iPad / large iPhone landscape).
struct AdaptiveShell: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        if sizeClass == .regular {
            SplitShell()
        } else {
            TabShell()
        }
    }
}

struct TabShell: View {
    @Environment(\.l10n) private var l10n
    @Environment(AppStore.self) private var store
    @State var selection: AppSection = .overview

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppSection.primaryTabs) { section in
                NavigationStack {
                    SectionRootView(section: section)
                        .withAppRoutes()
                }
                .tabItem { Label(l10n.t(section.titleKey), systemImage: section.icon) }
                .tag(section)
            }
            NavigationStack {
                MoreView()
                    .withAppRoutes()
            }
            .tabItem { Label(l10n.t("nav.more"), systemImage: "ellipsis.circle.fill") }
            .badge(store.unreadCount)
            .tag(AppSection.settings)
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}

struct MoreView: View {
    @Environment(\.l10n) private var l10n
    @Environment(AppStore.self) private var store

    var body: some View {
        List {
            Section {
                NavigationLink(value: AppRoute.notifications) {
                    HStack {
                        Label(l10n.t("ntf.title"), systemImage: "bell.fill")
                        Spacer()
                        if store.unreadCount > 0 {
                            Text("\(store.unreadCount)")
                                .font(.caption.bold())
                                .foregroundStyle(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Brand.danger, in: Capsule())
                        }
                    }
                }
            }
            Section {
                ForEach(AppSection.moreSections) { section in
                    NavigationLink(value: AppRoute.section(section)) {
                        Label {
                            Text(l10n.t(section.titleKey))
                        } icon: {
                            IconTile(systemName: section.icon, tint: Brand.primary, size: 30)
                        }
                    }
                }
            }
        }
        .navigationTitle(l10n.t("nav.more"))
        .screenBackground()
    }
}

struct SplitShell: View {
    @Environment(\.l10n) private var l10n
    @Environment(AppStore.self) private var store
    @State var selection: AppSection? = .overview
    @State var visibility: NavigationSplitViewVisibility = .all

    var body: some View {
        NavigationSplitView(columnVisibility: $visibility) {
            List(selection: $selection) {
                Section {
                    ForEach(AppSection.allCases) { section in
                        Label(l10n.t(section.titleKey), systemImage: section.icon)
                            .badge(section == .alerts ? store.unreadCount : 0)
                            .tag(section)
                    }
                } header: {
                    BrandMark(size: 28)
                        .padding(.vertical, 8)
                        .textCase(nil)
                }
            }
            .navigationSplitViewColumnWidth(min: 220, ideal: 250)
            .listStyle(.sidebar)
        } detail: {
            NavigationStack {
                if let selection {
                    SectionRootView(section: selection)
                        .withAppRoutes()
                } else {
                    ContentUnavailableView(l10n.t("common.selectSection"), systemImage: "sidebar.left")
                }
            }
            .id(selection)
        }
        .navigationSplitViewStyle(.balanced)
        .sensoryFeedback(.selection, trigger: selection)
    }
}

/// The root screen of a section.
struct SectionRootView: View {
    let section: AppSection

    var body: some View {
        switch section {
        case .overview: OverviewView()
        case .sites: SitesView()
        case .map: SiteMapView()
        case .devices: DevicesView()
        case .maintenance: MaintenanceView()
        case .billing: BillingView()
        case .analytics: AnalyticsView()
        case .reports: ReportsView()
        case .finance: FinanceView()
        case .alerts: AlertsView()
        case .settings: SettingsView()
        }
    }
}

struct AppRouteView: View {
    let route: AppRoute
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n

    var body: some View {
        switch route {
        case .site(let id): SiteDetailView(siteId: id)
        case .invoice(let id):
            if let inv = store.db.invoices.first(where: { $0.id == id }) {
                InvoiceDetailView(invoiceId: inv.id)
            } else {
                ContentUnavailableView(l10n.t("common.noData"), systemImage: "doc.text.magnifyingglass")
            }
        case .ticket(let id): TicketEditorView(ticketId: id)
        case .section(let s): SectionRootView(section: s)
        case .integrations: IntegrationsView()
        case .notifications: NotificationsView()
        }
    }
}

extension View {
    /// Registers the app's navigation destinations on a NavigationStack root.
    func withAppRoutes() -> some View {
        navigationDestination(for: AppRoute.self) { route in
            AppRouteView(route: route)
        }
    }
}
