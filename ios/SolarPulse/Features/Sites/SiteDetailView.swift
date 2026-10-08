import SwiftUI
import Charts
import MapKit
import SolarPulseKit

/// Site detail (src/pages/SiteDetail.tsx) + mini map and ROI card.
struct SiteDetailView: View {
    let siteId: String

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @Environment(\.dismiss) private var dismiss

    @State var showEdit = false
    @State var confirmDelete = false
    @State var finance: Finance.Result?

    var body: some View {
        if let site = store.site(siteId) {
            content(site)
        } else {
            ContentUnavailableView(l10n.t("sites.notFound"), systemImage: "building.2.crop.circle")
        }
    }

    private func content(_ site: Site) -> some View {
        let sim = store.sim
        let days = sim.days
        let today = store.now
        let day = days.dayKey(today)
        let kw = store.currentKw(site)
        let todayKwh = sim.totalKwh([site], from: today, to: today)
        let monthKwh = sim.totalKwh([site], from: days.startOfMonth(today), to: today)
        let last30 = sim.dailySeries([site], from: days.addDays(today, -29), to: today)
        let last30Total = last30.reduce(0) { $0 + $1.kwh }
        let hourly = sim.hourlySeries([site], day: day, stepMin: 30)
        let devices = store.db.devices.filter { $0.siteId == site.id }
        let tickets = store.db.tickets.filter { $0.siteId == site.id }
        let invoices = store.db.invoices.filter { $0.siteId == site.id }.sorted { $0.period > $1.period }

        return ScrollView {
            VStack(spacing: 16) {
                header(site)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 12)], spacing: 12) {
                    StatTile(icon: "bolt.fill", tint: Brand.b500, title: l10n.t("sites.currentPower"), value: fmt.power(kw),
                             sub: "\(fmt.pct(site.capacityKw > 0 ? kw / site.capacityKw * 100 : 0)) / \(fmt.power(site.capacityKw))")
                    StatTile(icon: "sun.max.fill", tint: Brand.amber, title: l10n.t("sites.energyToday"), value: fmt.energy(todayKwh),
                             sub: fmt.money2(todayKwh * site.pricePerKwh))
                    StatTile(icon: "calendar", tint: Brand.green, title: l10n.t("sites.thisMonth"), value: fmt.energy(monthKwh),
                             sub: fmt.money(monthKwh * site.pricePerKwh))
                    StatTile(icon: "gauge.with.dots.needle.50percent", tint: Brand.violet, title: l10n.t("sites.yield"),
                             value: "\(fmt.num(site.capacityKw > 0 ? last30Total / 30 / site.capacityKw : 0, 2)) kWh/kWp",
                             sub: l10n.t("an.avgDaily"))
                }

                Card(l10n.t("sites.todayCurve")) {
                    Chart {
                        ForEach(hourly) { p in
                            if let v = p.kw {
                                AreaMark(x: .value("h", p.hour), y: .value("kW", v))
                                    .interpolationMethod(.monotone)
                                    .foregroundStyle(LinearGradient(colors: [Brand.b500.opacity(0.4), Brand.b500.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                                LineMark(x: .value("h", p.hour), y: .value("kW", v))
                                    .interpolationMethod(.monotone)
                                    .foregroundStyle(Brand.b500)
                            }
                        }
                    }
                    .chartXScale(domain: 0.0...24.0)
                    .chartXAxis {
                        AxisMarks(values: [0.0, 6.0, 12.0, 18.0, 24.0]) { v in
                            AxisValueLabel { if let h = v.as(Double.self) { Text("\(DayMath.pad2(Int(h) % 24)):00") } }
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                            AxisValueLabel { if let x = v.as(Double.self) { Text(Fmt.axisPower(x)) } }
                        }
                    }
                    .frame(height: 190)
                    .ltrChart()
                }

                Card(l10n.t("sites.last30")) {
                    Chart(last30) { p in
                        BarMark(x: .value("d", p.date, unit: .day), y: .value("kWh", p.kwh))
                            .foregroundStyle(Brand.b500.gradient)
                            .cornerRadius(3)
                    }
                    .chartXAxis {
                        AxisMarks(values: .stride(by: .day, count: 7)) { v in
                            AxisValueLabel { if let d = v.as(Date.self) { Text(fmt.dateShort(d)) } }
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                            AxisValueLabel { if let x = v.as(Double.self) { Text(Fmt.axisEnergy(x)) } }
                        }
                    }
                    .frame(height: 180)
                    .ltrChart()
                }

                financeCard(site)
                miniMap(site)

                Card("\(l10n.t("sites.devices")) (\(devices.count))", systemImage: "cpu") {
                    if devices.isEmpty {
                        Text(l10n.t("common.noData")).font(.footnote).foregroundStyle(.secondary)
                    } else {
                        ForEach(devices) { d in
                            HStack {
                                IconTile(systemName: d.type.icon, tint: Brand.b500, size: 30)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(d.name).font(.subheadline.weight(.medium)).environment(\.layoutDirection, .leftToRight)
                                    Text("\(l10n.deviceType(d.type)) · \(d.model)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                                Spacer()
                                StatusBadge(text: l10n.status(d.status.rawValue), color: d.status.color)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    }
                }

                Card("\(l10n.t("sites.tickets")) (\(tickets.count))", systemImage: "wrench.and.screwdriver") {
                    if tickets.isEmpty {
                        Text(l10n.t("common.noData")).font(.footnote).foregroundStyle(.secondary)
                    } else {
                        ForEach(tickets) { t in
                            NavigationLink(value: AppRoute.ticket(t.id)) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(t.title).font(.subheadline.weight(.medium)).lineLimit(1)
                                        Text("\(fmt.date(t.dueDate)) · \(t.assignee)").font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    StatusBadge(text: l10n.status(t.status.rawValue), color: t.status.color)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Card("\(l10n.t("sites.invoices")) (\(invoices.count))", systemImage: "doc.text") {
                    if invoices.isEmpty {
                        Text(l10n.t("common.noData")).font(.footnote).foregroundStyle(.secondary)
                    } else {
                        ForEach(invoices.prefix(6)) { inv in
                            NavigationLink(value: AppRoute.invoice(inv.id)) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(inv.number).font(.subheadline.weight(.medium)).environment(\.layoutDirection, .leftToRight)
                                        Text("\(fmt.energy(inv.energyKwh)) · \(fmt.money2(inv.amount))").font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    StatusBadge(text: l10n.status(inv.status.rawValue), color: inv.status.color)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .navigationTitle(site.name)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { showEdit = true } label: { Label(l10n.t("common.edit"), systemImage: "pencil") }
                    Menu {
                        ForEach(SiteStatus.allCases) { s in
                            Button {
                                Task { await setStatus(site, s) }
                            } label: {
                                if s == site.status { Label(l10n.status(s.rawValue), systemImage: "checkmark") } else { Text(l10n.status(s.rawValue)) }
                            }
                        }
                    } label: {
                        Label(l10n.t("common.status"), systemImage: "circle.dashed")
                    }
                    Divider()
                    Button(role: .destructive) { confirmDelete = true } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel(Text(l10n.t("common.actions")))
            }
        }
        .sheet(isPresented: $showEdit) { SiteFormView(initial: site) }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                Task {
                    await store.deleteSite(site.id)
                    store.show(l10n.t("common.deleted"), tone: .info)
                    dismiss()
                }
            }
        } message: {
            Text(l10n.t("sites.deleteWarn"))
        }
        .task(id: site) {
            let sim = store.sim
            let discount = store.settings.discountRatePct
            let s = site
            let result = await Task.detached(priority: .userInitiated) {
                Finance.analyze(Finance.inputs(for: s, e1: Finance.yearOneEnergy(s, sim: sim)), discountRatePct: discount)
            }.value
            withAnimation { finance = result }
        }
    }

    private func header(_ site: Site) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text(site.name).font(.title2.weight(.bold))
                Label("\(site.location) · \(l10n.siteType(site.type)) · \(site.customer)", systemImage: "mappin.and.ellipse")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                StatusBadge(text: l10n.status(site.status.rawValue), color: site.status.color)
                if store.isLive(site) { LiveBadge(text: l10n.t("live.badge")) }
            }
        }
        .padding(.top, 4)
    }

    private func financeCard(_ site: Site) -> some View {
        Card(l10n.t("fin.card"), systemImage: "chart.line.uptrend.xyaxis") {
            NavigationLink(value: AppRoute.section(.finance)) { Text(l10n.t("fin.details")).font(.caption) }
        } content: {
            if let r = finance {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading, spacing: 12) {
                    finStat(l10n.t("fin.payback"), r.paybackYears.map { l10n.t("fin.years", ["n": fmt.num($0, 1)]) } ?? l10n.t("fin.never"))
                    finStat(l10n.t("fin.roi"), fmt.pct(r.roiPct, 0))
                    finStat(l10n.t("fin.npv"), fmt.money(r.npv))
                    finStat(l10n.t("fin.irr"), r.irr.map { fmt.pct($0 * 100) } ?? "—")
                    finStat(l10n.t("fin.lcoe"), r.lcoe.map { "\(fmt.money2($0))\(l10n.t("fin.perKwh"))" } ?? "—")
                    finStat(l10n.t("fin.investment"), fmt.money(r.systemCost))
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 80)
            }
        }
    }

    private func finStat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.headline).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
        .accessibilityElement(children: .combine)
    }

    private func miniMap(_ site: Site) -> some View {
        let coord = CLLocationCoordinate2D(latitude: site.lat, longitude: site.lng)
        return Card(l10n.t("common.location"), systemImage: "map") {
            NavigationLink(value: AppRoute.section(.map)) { Text(l10n.t("map.viewOnMap")).font(.caption) }
        } content: {
            Map(initialPosition: .region(MKCoordinateRegion(center: coord, span: MKCoordinateSpan(latitudeDelta: 0.6, longitudeDelta: 0.6)))) {
                Annotation(site.name, coordinate: coord) {
                    SitePin(site: site, selected: true)
                }
            }
            .mapStyle(.standard(elevation: .realistic, pointsOfInterest: .excludingAll))
            .frame(height: 170)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .allowsHitTesting(false)
        }
    }

    private func setStatus(_ site: Site, _ status: SiteStatus) async {
        guard status != site.status else { return }
        var next = site
        next.status = status
        await store.save(next)
        if status == .offline {
            await store.notify(title: l10n.t("status.offline"), body: site.name, kind: .danger, link: "/sites/\(site.id)")
        }
        store.show(l10n.t("common.saved"))
    }
}
