import SwiftUI
import Charts
import SolarPulseKit

/// Analytics — ranges 7/30/90 d / 12 m, trend, by site, by type, production vs consumption (src/pages/Analytics.tsx).
struct AnalyticsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var range: Simulator.AnalyticsRange = .d30
    @State var siteId: String?
    @State var model: AnalyticsModel?

    private struct Key: Hashable {
        let range: Simulator.AnalyticsRange
        let siteId: String?
        let sites: [Site]
        let co2: Double
        let day: String
    }

    var body: some View {
        let sites = siteId == nil ? store.db.sites : store.db.sites.filter { $0.id == siteId }
        ScrollView {
            VStack(spacing: 16) {
                controls
                if let m = model {
                    content(m, capacity: sites.reduce(0) { $0 + $1.capacityKw })
                        .transition(.opacity)
                } else {
                    VStack(spacing: 16) {
                        RoundedRectangle(cornerRadius: 18).fill(Theme.card).frame(height: 160)
                        RoundedRectangle(cornerRadius: 22).fill(Theme.card).frame(height: 260)
                        RoundedRectangle(cornerRadius: 22).fill(Theme.card).frame(height: 260)
                    }
                    .modifier(Shimmer())
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .navigationTitle(l10n.t("an.title"))
        .refreshable { await store.refresh() }
        .task(id: Key(range: range, siteId: siteId, sites: sites, co2: store.settings.co2KgPerKwh, day: store.sim.todayKey)) {
            let sim = store.sim
            let r = range
            let co2 = store.settings.co2KgPerKwh
            let f = fmt
            let result = await Task.detached(priority: .userInitiated) {
                AnalyticsModel.compute(sites: sites, sim: sim, range: r, co2Factor: co2, fmt: f)
            }.value
            withAnimation(.easeOut) { model = result }
        }
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker("", selection: $range) {
                Text(verbatim: "7D").tag(Simulator.AnalyticsRange.d7)
                Text(verbatim: "30D").tag(Simulator.AnalyticsRange.d30)
                Text(verbatim: "90D").tag(Simulator.AnalyticsRange.d90)
                Text(verbatim: "12M").tag(Simulator.AnalyticsRange.m12)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            Menu {
                Picker("", selection: $siteId) {
                    Text(l10n.t("an.allSites")).tag(String?.none)
                    ForEach(store.db.sites) { s in Text(s.name).tag(String?.some(s.id)) }
                }
            } label: {
                Label(siteId.flatMap { store.site($0)?.name } ?? l10n.t("an.allSites"), systemImage: "building.2")
                    .font(.footnote.weight(.medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Theme.card, in: Capsule())
            }
        }
        .sensoryFeedback(.selection, trigger: range)
    }

    @ViewBuilder
    private func content(_ m: AnalyticsModel, capacity: Double) -> some View {
        let change = m.prevTotal == 0 ? 0 : (m.total - m.prevTotal) / m.prevTotal * 100
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 10)], spacing: 10) {
            StatTile(icon: "sun.max.fill", tint: Brand.b500, title: l10n.t("an.production"), value: fmt.energy(m.total), sub: fmt.signedPct(change))
            StatTile(icon: "chart.line.uptrend.xyaxis", tint: Brand.green, title: l10n.t("an.avgDaily"), value: fmt.energy(m.avg))
            StatTile(icon: "trophy.fill", tint: Brand.amber, title: l10n.t("an.peakDay"), value: fmt.energy(m.bestKwh), sub: m.bestDate.map { fmt.date($0) })
            StatTile(icon: "gauge.with.dots.needle.50percent", tint: Brand.violet, title: l10n.t("an.perfRatio"),
                     value: "\(fmt.num(capacity > 0 ? m.avg / capacity : 0, 2)) kWh/kWp", sub: l10n.t("an.avgDaily"))
        }

        Card(l10n.t("an.trend")) {
            Chart(m.trend) { p in
                AreaMark(x: .value("x", p.index), y: .value("kWh", p.value))
                    .interpolationMethod(.monotone)
                    .foregroundStyle(LinearGradient(colors: [Brand.b500.opacity(0.4), Brand.b500.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                LineMark(x: .value("x", p.index), y: .value("kWh", p.value))
                    .interpolationMethod(.monotone)
                    .foregroundStyle(Brand.b500)
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 6)) { v in
                    AxisValueLabel {
                        if let i = v.as(Int.self), i >= 0, i < m.trend.count { Text(m.trend[i].label) }
                    }
                }
            }
            .chartYAxis { energyAxis }
            .frame(height: 220)
            .ltrChart()
        }

        Card(l10n.t("an.bySite")) {
            Chart(m.bySite) { row in
                BarMark(x: .value("kWh", row.value), y: .value("site", row.name))
                    .foregroundStyle(Brand.b500.gradient)
                    .cornerRadius(5)
                    .annotation(position: .trailing, alignment: .leading) {
                        Text(Fmt.axisEnergy(row.value)).font(.caption2).foregroundStyle(.secondary)
                    }
            }
            .chartXAxis(.hidden)
            .chartYAxis {
                AxisMarks { v in
                    AxisValueLabel { if let n = v.as(String.self) { Text(n).lineLimit(1) } }
                }
            }
            .frame(height: max(200, CGFloat(m.bySite.count) * 30))
        }

        Card(l10n.t("an.byType")) {
            Chart(m.byType) { row in
                SectorMark(angle: .value("kWh", row.value), innerRadius: .ratio(0.6), angularInset: 2)
                    .foregroundStyle(by: .value("type", l10n.siteType(row.type)))
                    .cornerRadius(4)
            }
            .chartForegroundStyleScale(range: Brand.palette)
            .chartLegend(position: .bottom, alignment: .center)
            .frame(height: 240)
        }

        Card(l10n.t("an.prodVsCons")) {
            Chart {
                ForEach(m.pvc) { p in
                    if let prod = p.production {
                        LineMark(x: .value("h", p.hour), y: .value("kW", prod), series: .value("s", "prod"))
                            .foregroundStyle(by: .value("s", l10n.t("an.production.short")))
                    }
                    if let cons = p.consumption {
                        LineMark(x: .value("h", p.hour), y: .value("kW", cons), series: .value("s", "cons"))
                            .foregroundStyle(by: .value("s", l10n.t("an.consumption.short")))
                            .lineStyle(StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    }
                }
            }
            .chartForegroundStyleScale([l10n.t("an.production.short"): Brand.b500, l10n.t("an.consumption.short"): Brand.amber])
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
            .chartLegend(position: .top, alignment: .leading)
            .frame(height: 220)
            .ltrChart()
        }

        Card(l10n.t("an.heatmap")) {
            Chart(m.heat) { cell in
                RectangleMark(xStart: .value("h", cell.hour), xEnd: .value("h", cell.hour + 1),
                              yStart: .value("d", cell.row), yEnd: .value("d", cell.row + 1))
                    .foregroundStyle(cell.kw < 0.01 ? Brand.slate.opacity(0.12) : Brand.b500.opacity(0.12 + 0.88 * cell.kw / max(m.heatMax, 1)))
                    .cornerRadius(2)
            }
            .chartXScale(domain: 0...24)
            .chartYScale(domain: 0...max(m.heatRows.count, 1))
            .chartXAxis {
                AxisMarks(values: [0, 3, 6, 9, 12, 15, 18, 21]) { v in
                    AxisValueLabel { if let h = v.as(Int.self) { Text("\(h)") } }
                }
            }
            .chartYAxis {
                AxisMarks(values: Array(stride(from: 0, to: m.heatRows.count, by: 2))) { v in
                    AxisValueLabel {
                        if let r = v.as(Int.self), r >= 0, r < m.heatRows.count { Text(m.heatRows[r]).font(.system(size: 8)) }
                    }
                }
            }
            .frame(height: 260)
            .ltrChart()
        }

        Card(l10n.t("ov.envImpact")) {
            let s = store.settings
            HStack(spacing: 12) {
                impact("leaf.fill", Brand.sky, l10n.t("ov.co2Offset"), "\(fmt.num(m.co2, 1)) \(l10n.t("common.tons"))")
                impact("tree.fill", Brand.green, l10n.t("ov.trees"), fmt.num(s.treeKgPerYear > 0 ? m.co2 * 1000 / s.treeKgPerYear : 0))
                impact("car.fill", Brand.amber, l10n.t("ov.cars"), fmt.num(s.carTonsPerYear > 0 ? m.co2 / s.carTonsPerYear : 0, 1))
            }
            Chart(m.monthlyCO2) { p in
                BarMark(x: .value("m", p.label), y: .value("t", p.value))
                    .foregroundStyle(Brand.green.gradient)
                    .cornerRadius(4)
            }
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                    AxisValueLabel { if let x = v.as(Double.self) { Text("\(fmt.num(x, 1)) t") } }
                }
            }
            .frame(height: 180)
            .ltrChart()
        }
    }

    private var energyAxis: some AxisContent {
        AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
            AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
            AxisValueLabel { if let x = v.as(Double.self) { Text(Fmt.axisEnergy(x)) } }
        }
    }

    private func impact(_ icon: String, _ tint: Color, _ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            IconTile(systemName: icon, tint: tint, size: 34)
            Text(title).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
            Text(value).font(.subheadline.weight(.semibold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Everything the Analytics page shows, computed off the main thread.
struct AnalyticsModel: Sendable {
    struct Point: Sendable, Identifiable {
        let index: Int
        let label: String
        let value: Double
        var id: Int { index }
    }

    struct Named: Sendable, Identifiable {
        let name: String
        let value: Double
        var id: String { name }
    }

    struct TypeShare: Sendable, Identifiable {
        let type: SiteType
        let value: Double
        var id: String { type.rawValue }
    }

    struct PVC: Sendable, Identifiable {
        let hour: Double
        let production: Double?
        let consumption: Double?
        var id: Double { hour }
    }

    struct HeatCell: Sendable, Identifiable {
        let row: Int
        let hour: Int
        let kw: Double
        var id: Int { row * 24 + hour }
    }

    var total: Double
    var prevTotal: Double
    var avg: Double
    var bestKwh: Double
    var bestDate: Date?
    var trend: [Point]
    var bySite: [Named]
    var byType: [TypeShare]
    var pvc: [PVC]
    var heat: [HeatCell]
    var heatRows: [String]
    var heatMax: Double
    var co2: Double
    var monthlyCO2: [Point]

    static func compute(sites: [Site], sim: Simulator, range: Simulator.AnalyticsRange, co2Factor: Double, fmt: Fmt) -> AnalyticsModel {
        let days = sim.days
        let (from, to) = sim.rangeOf(range)
        let series = sim.dailySeries(sites, from: from, to: to)
        let total = series.reduce(0) { $0 + $1.kwh }
        let n = max(series.count, 1)
        let prevFrom = days.addDays(from, -series.count)
        let prevTotal = sim.totalKwh(sites, from: prevFrom, to: days.addDays(from, -1))
        let best = series.max { $0.kwh < $1.kwh }

        var trend: [Point] = []
        if range == .m12 {
            var order: [String] = []
            var sums: [String: Double] = [:]
            var labels: [String: String] = [:]
            for p in series {
                let k = days.monthKey(p.date)
                if sums[k] == nil { order.append(k); labels[k] = fmt.monthShort(p.date) }
                sums[k, default: 0] += p.kwh
            }
            trend = order.enumerated().map { Point(index: $0.offset, label: labels[$0.element] ?? $0.element, value: sums[$0.element] ?? 0) }
        } else {
            trend = series.enumerated().map { Point(index: $0.offset, label: fmt.dateShort($0.element.date), value: $0.element.kwh) }
        }

        let bySite = sites.map { Named(name: $0.name, value: sim.totalKwh([$0], from: from, to: to)) }.sorted { $0.value > $1.value }
        var typeTotals: [SiteType: Double] = [:]
        for s in sites { typeTotals[s.type, default: 0] += bySite.first { $0.name == s.name }?.value ?? 0 }
        let byType = SiteType.allCases.compactMap { t in typeTotals[t].map { TypeShare(type: t, value: $0) } }

        let day = days.dayKey(sim.now)
        let pvc = sim.hourlySeries(sites, day: day, stepMin: 60).map { p in
            PVC(hour: p.hour, production: p.kw, consumption: p.kw == nil ? nil : sim.consumptionKw(sites, hour: p.hour, day: day))
        }

        var heat: [HeatCell] = []
        var heatRows: [String] = []
        for i in 0..<14 {
            let d = days.addDays(sim.now, -13 + i)
            let k = days.dayKey(d)
            heatRows.append(fmt.dateShort(d))
            for h in 0..<24 {
                heat.append(HeatCell(row: i, hour: h, kw: sites.reduce(0.0) { $0 + sim.siteKw($1, day: k, hour: Double(h) + 0.5) }))
            }
        }
        let heatMax = max(1, heat.map(\.kw).max() ?? 1)

        var monthly: [Point] = []
        let now = sim.now
        for k in stride(from: 11, through: 0, by: -1) {
            let start = days.addMonths(now, -k)
            let end = days.endOfMonth(start)
            let kwh = sim.totalKwh(sites, from: start, to: end > now ? now : end)
            monthly.append(Point(index: 11 - k, label: fmt.monthShort(start), value: Simulator.co2Tons(kwh, factor: co2Factor)))
        }

        return AnalyticsModel(
            total: total, prevTotal: prevTotal, avg: total / Double(n), bestKwh: best?.kwh ?? 0, bestDate: best?.date,
            trend: trend, bySite: bySite, byType: byType, pvc: pvc, heat: heat, heatRows: heatRows, heatMax: heatMax,
            co2: Simulator.co2Tons(total, factor: co2Factor), monthlyCO2: monthly
        )
    }
}
