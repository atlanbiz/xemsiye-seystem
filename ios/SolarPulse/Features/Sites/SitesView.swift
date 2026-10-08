import SwiftUI
import SolarPulseKit

/// Sites list — search, status/type filters, add / edit / delete (src/pages/Sites.tsx).
struct SitesView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var query = ""
    @State var status: SiteStatus?
    @State var type: SiteType?
    @State var editing: Site?
    @State var showForm = false
    @State var deleting: Site?

    private var rows: [Site] {
        let q = query.lowercased()
        return store.db.sites.filter { s in
            (status == nil || s.status == status) && (type == nil || s.type == type) &&
                (q.isEmpty || [s.name, s.location, s.customer].contains { $0.lowercased().contains(q) })
        }
    }

    var body: some View {
        let list = rows
        ScrollView {
            VStack(spacing: 12) {
                filters
                HStack {
                    Text("\(l10n.t("common.showing")) \(list.count) \(l10n.t("common.of")) \(store.db.sites.count)")
                        .font(.caption).foregroundStyle(.secondary)
                    Spacer()
                }
                if list.isEmpty {
                    ContentUnavailableView(l10n.t("common.noData"), systemImage: "building.2", description: Text(l10n.t("sites.subtitle")))
                        .padding(.top, 40)
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 12)], spacing: 12) {
                        ForEach(list) { site in
                            NavigationLink(value: AppRoute.site(site.id)) {
                                SiteCard(site: site)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button { editing = site; showForm = true } label: { Label(l10n.t("common.edit"), systemImage: "pencil") }
                                Button(role: .destructive) { deleting = site } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .animation(.snappy, value: list.map(\.id))
        }
        .screenBackground()
        .navigationTitle(l10n.t("sites.title"))
        .searchable(text: $query, prompt: Text(l10n.t("common.search")))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = nil; showForm = true } label: { Label(l10n.t("sites.add"), systemImage: "plus") }
            }
        }
        .sheet(isPresented: $showForm) {
            SiteFormView(initial: editing)
        }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                if let s = deleting { Task { await store.deleteSite(s.id); store.show(l10n.t("common.deleted"), tone: .info) } }
            }
            Button(l10n.t("common.cancel"), role: .cancel) {}
        } message: {
            Text(l10n.t("sites.deleteWarn"))
        }
    }

    private var statusOptions: [(SiteStatus?, String)] {
        var out: [(SiteStatus?, String)] = [(nil, "\(l10n.t("common.status")): \(l10n.t("common.all"))")]
        for s in SiteStatus.allCases { out.append((s, l10n.status(s.rawValue))) }
        return out
    }

    private var typeOptions: [(SiteType?, String)] {
        var out: [(SiteType?, String)] = [(nil, "\(l10n.t("common.type")): \(l10n.t("common.all"))")]
        for t in SiteType.allCases { out.append((t, l10n.siteType(t))) }
        return out
    }

    private var filters: some View {
        VStack(spacing: 8) {
            ChipPicker(options: statusOptions, selection: $status)
            ChipPicker(options: typeOptions, selection: $type)
        }
    }
}

/// Site summary card with today's sparkline.
struct SiteCard: View {
    let site: Site
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let sim = store.sim
        let day = sim.days.dayKey(store.now)
        let spark = sim.hourlySeries([site], day: day, stepMin: 60).filter { $0.hour >= 6 }.compactMap(\.kw)
        let devices = store.db.devices.filter { $0.siteId == site.id }.count
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(site.name).font(.headline).lineLimit(1)
                    Label("\(site.location) · \(l10n.siteType(site.type))", systemImage: "mappin.and.ellipse")
                        .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 4) {
                    StatusBadge(text: l10n.status(site.status.rawValue), color: site.status.color)
                    if store.isLive(site) { LiveBadge(text: l10n.t("live.badge")) }
                }
            }
            Sparkline(values: spark, color: site.status == .active ? Brand.b500 : Brand.slate)
                .frame(height: 46)
            HStack {
                metric(l10n.t("sites.currentPower"), fmt.power(store.currentKw(site)))
                metric(l10n.t("sites.energyToday"), fmt.energy(sim.siteDayKwhCached(site, day: day)))
                metric(l10n.t("sites.capacity"), fmt.power(site.capacityKw))
            }
            Divider()
            HStack(spacing: 14) {
                Label("\(devices)", systemImage: "cpu").accessibilityLabel(Text("\(l10n.t("sites.devices")): \(devices)"))
                if site.batteryKwh > 0 {
                    Label("\(fmt.num(site.batteryKwh)) kWh", systemImage: "battery.75percent")
                }
                Spacer()
                Image(systemName: "chevron.forward").foregroundStyle(.tertiary)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .card()
        .contentShape(Rectangle())
    }

    private func metric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
            Text(value).font(.subheadline.weight(.semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
