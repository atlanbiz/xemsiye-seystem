import SwiftUI
import Charts
import SolarPulseKit

/// Dashboard — mirrors src/pages/Overview.tsx.
struct OverviewView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @Environment(\.horizontalSizeClass) private var sizeClass

    @State var kpis: OverviewKPIs?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HeroCard()
                if let kpis {
                    KPIGrid(kpis: kpis)
                } else {
                    KPIGrid(kpis: .placeholder).shimmering()
                }
                if sizeClass == .regular {
                    HStack(alignment: .top, spacing: 16) {
                        GenerationCard().frame(maxWidth: .infinity)
                        VStack(spacing: 16) {
                            WeatherCard()
                            PerformanceCard()
                        }
                        .frame(maxWidth: 360)
                    }
                    HStack(alignment: .top, spacing: 16) {
                        EnergyFlowCard().frame(maxWidth: .infinity)
                        VStack(spacing: 16) {
                            ImpactCard()
                            SitePerformanceCard()
                        }
                        .frame(maxWidth: 420)
                    }
                } else {
                    WeatherCard()
                    GenerationCard()
                    EnergyFlowCard()
                    PerformanceCard()
                    ImpactCard()
                    SitePerformanceCard()
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .navigationTitle(l10n.t("nav.overview"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: AppRoute.notifications) {
                    Image(systemName: store.unreadCount > 0 ? "bell.badge.fill" : "bell")
                        .symbolRenderingMode(.hierarchical)
                        .symbolEffect(.bounce, value: store.unreadCount)
                }
                .accessibilityLabel(Text(l10n.t("ntf.title")))
            }
        }
        .refreshable { await store.refresh() }
        .task(id: OverviewKPIs.Key(minute: Int(store.now.timeIntervalSince1970 / 60), sites: store.db.sites, co2: store.settings.co2KgPerKwh)) {
            let next = OverviewKPIs.compute(sites: store.db.sites, sim: store.sim, co2Factor: store.settings.co2KgPerKwh)
            withAnimation(.easeOut) { kpis = next }
        }
    }
}

// MARK: - KPI model

struct OverviewKPIs: Equatable {
    struct Key: Hashable {
        let minute: Int
        let sites: [Site]
        let co2: Double
    }

    var mtd: Double
    var prev: Double
    var revM: Double
    var revP: Double
    var todayKwh: Double
    var todayChange: Double
    var co2: Double
    var co2Change: Double
    var spark: [Double]
    var revSpark: [Double]
    var liveSpark: [Double]

    static let placeholder = OverviewKPIs(mtd: 0, prev: 0, revM: 0, revP: 0, todayKwh: 0, todayChange: 0, co2: 0,
                                          co2Change: 0, spark: [], revSpark: [], liveSpark: [])

    static func pctChange(_ cur: Double, _ prev: Double) -> Double { prev == 0 ? 0 : (cur - prev) / prev * 100 }

    /// Month-to-date vs the same span last month, today vs yesterday at the same hour (web Overview `kpis`).
    static func compute(sites: [Site], sim: Simulator, co2Factor: Double) -> OverviewKPIs {
        let days = sim.days
        let today = sim.now
        let c = days.components(today)
        let mStart = days.makeDate(year: c.year, monthIndex0: c.month - 1, day: 1)
        let pStart = days.makeDate(year: c.year, monthIndex0: c.month - 2, day: 1)
        let prevMonthLength = days.components(days.makeDate(year: c.year, monthIndex0: c.month - 1, day: 0)).day
        let pEnd = days.makeDate(year: c.year, monthIndex0: c.month - 2, day: min(c.day, prevMonthLength))
        let mtd = sim.totalKwh(sites, from: mStart, to: today)
        let prev = sim.totalKwh(sites, from: pStart, to: pEnd)
        func revenue(_ from: Date, _ to: Date) -> Double {
            sites.reduce(0.0) { $0 + sim.totalKwh([$1], from: from, to: to) * $1.pricePerKwh }
        }
        let revM = revenue(mStart, today)
        let revP = revenue(pStart, pEnd)
        let todayKwh = sim.totalKwh(sites, from: today, to: today)
        let yd = days.dayKey(days.addDays(today, -1))
        let hourNow = days.hourOfDay(today)
        let ydSame = sites.reduce(0.0) { acc, s in
            var sum = 0.0
            var h = 0.0
            while h < hourNow {
                sum += sim.siteKw(s, day: yd, hour: h + 0.125) * 0.25
                h += 0.25
            }
            return acc + sum
        }
        let last14 = sim.dailySeries(sites, from: days.addDays(today, -13), to: today).map(\.kwh)
        let live = sim.hourlySeries(sites, day: days.dayKey(today), stepMin: 60).compactMap(\.kw).suffix(10)
        return OverviewKPIs(
            mtd: mtd, prev: prev, revM: revM, revP: revP, todayKwh: todayKwh,
            todayChange: pctChange(todayKwh, ydSame),
            co2: Simulator.co2Tons(mtd, factor: co2Factor),
            co2Change: pctChange(mtd, prev),
            spark: last14,
            revSpark: last14.enumerated().map { $0.element * (0.95 + Double($0.offset % 3) * 0.03) },
            liveSpark: Array(live)
        )
    }
}

// MARK: - Hero

private struct HeroCard: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let sites = store.db.sites
        let stats = Simulator.deviceStats(store.db.devices)
        let health = stats.availability >= 95 ? "excellent" : stats.availability >= 85 ? "good" : "attention"
        let capacity = sites.reduce(0.0) { $0 + $1.capacityKw }
        let live = store.fleetKw()
        let active = sites.filter { $0.status == .active }.count
        let idle = sites.filter { $0.status == .idle }.count
        let firstName = store.settings.userName.split(separator: " ").first.map(String.init) ?? ""

        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("\(l10n.greeting(hour: store.sim.days.hour(store.now))), \(firstName) 👋")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                (Text("\(l10n.t("hero.title1")) \(l10n.t("hero.title2")) ")
                    + Text(l10n.t("hero.\(health)")).foregroundColor(health == "attention" ? Brand.amber : Color(hex: 0xBFD5FE)))
                    .font(.system(.title2, design: .rounded).weight(.bold))
                    .foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
            NavigationLink(value: AppRoute.section(.sites)) {
                HStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(l10n.t("ov.totalSites")).font(.caption).foregroundStyle(.white.opacity(0.8))
                        Text("\(sites.count)").font(.system(size: 36, weight: .bold, design: .rounded)).foregroundStyle(.white)
                        HStack(spacing: 12) {
                            legend(Brand.green, l10n.t("ov.activeSites"), active)
                            legend(Brand.amber, l10n.t("ov.idleSites"), idle)
                        }
                    }
                    Spacer()
                    Ring(value: capacity > 0 ? live / capacity * 100 : 0, color: .white, lineWidth: 7, size: 92) {
                        VStack(spacing: 0) {
                            Text(l10n.t("ov.totalCapacity")).font(.system(size: 8)).foregroundStyle(.white.opacity(0.8)).lineLimit(1).minimumScaleFactor(0.6)
                            Text(fmt.num(capacity / 1000, 2)).font(.subheadline.bold()).foregroundStyle(.white)
                            Text(verbatim: "MWp").font(.system(size: 8)).foregroundStyle(.white.opacity(0.8))
                        }
                        .padding(10)
                    }
                    .accessibilityElement(children: .combine)
                }
                .padding(14)
                .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            ZStack {
                Brand.heroGradient
                Image(systemName: "sun.max.fill")
                    .font(.system(size: 180))
                    .foregroundStyle(.white.opacity(0.08))
                    .offset(x: 120, y: -60)
                    .accessibilityHidden(true)
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: Brand.b600.opacity(0.35), radius: 20, y: 10)
    }

    private func legend(_ color: Color, _ title: String, _ n: Int) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(title).font(.caption2).foregroundStyle(.white.opacity(0.85))
            Text("\(n)").font(.caption2.bold()).foregroundStyle(.white)
        }
    }
}

// MARK: - KPI grid

private struct KPIGrid: View {
    let kpis: OverviewKPIs
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 12) {
            KPITile(icon: "bolt.fill", tint: Brand.b500, title: l10n.t("ov.currentPower"), value: fmt.power(store.fleetKw()),
                    footnote: AnyView(HStack(spacing: 4) {
                        LiveDot()
                        Text(l10n.t("ov.liveOutput")).font(.caption2).foregroundStyle(.secondary)
                    }), spark: kpis.liveSpark)
            KPITile(icon: "sun.max.fill", tint: Brand.amber, title: l10n.t("ov.energyToday"), value: fmt.energy(kpis.todayKwh),
                    change: kpis.todayChange, changeLabel: l10n.t("common.vsYesterday"), spark: kpis.spark)
            KPITile(icon: "leaf.fill", tint: Brand.sky, title: l10n.t("ov.co2Offset"), value: "\(fmt.num(kpis.co2, 1)) \(l10n.t("common.tons"))",
                    change: kpis.co2Change, changeLabel: l10n.t("common.vsLastMonth"), spark: kpis.spark)
            KPITile(icon: "dollarsign.circle.fill", tint: Brand.green, title: l10n.t("ov.totalRevenue"), value: fmt.money(kpis.revM),
                    change: OverviewKPIs.pctChange(kpis.revM, kpis.revP), changeLabel: l10n.t("common.vsLastMonth"), spark: kpis.revSpark)
        }
    }
}

private struct LiveDot: View {
    @State var on = false
    var body: some View {
        Circle()
            .fill(Brand.green)
            .frame(width: 7, height: 7)
            .overlay(Circle().stroke(Brand.green.opacity(0.5), lineWidth: 2).scaleEffect(on ? 2.2 : 1).opacity(on ? 0 : 1))
            .onAppear { withAnimation(.easeOut(duration: 1.4).repeatForever(autoreverses: false)) { on = true } }
            .accessibilityHidden(true)
    }
}

// MARK: - Weather

private struct WeatherCard: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let w = store.weather
        let kind = WeatherKind(code: w.code)
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(fmt.dateLong(store.now)).font(.caption.weight(.medium)).foregroundStyle(.secondary)
                    Text(WeatherService.cityLabel(store.settings.city, lang: store.settings.language))
                        .font(.caption2).foregroundStyle(.tertiary)
                }
                Spacer()
                if w.live { LiveBadge(text: l10n.t("live.badge")) }
            }
            HStack(spacing: 14) {
                Image(systemName: kind.icon)
                    .font(.system(size: 40))
                    .symbolRenderingMode(.multicolor)
                    .symbolEffect(.pulse, options: .repeating.speed(0.3), isActive: kind == .sunny)
                    .accessibilityHidden(true)
                VStack(alignment: .leading) {
                    Text("\(w.temp)°C").font(.title.weight(.semibold)).monospacedDigit()
                        .environment(\.layoutDirection, .leftToRight)
                    Text(l10n.weather(kind)).font(.caption).foregroundStyle(.secondary)
                }
            }
            Divider()
            InfoRow(label: l10n.t("wx.irradiance"), value: "\(w.irradiance) W/m²", ltrValue: true)
            InfoRow(label: l10n.t("wx.wind"), value: "\(w.wind) km/h", ltrValue: true)
            InfoRow(label: l10n.t("wx.humidity"), value: "\(w.humidity)%", ltrValue: true)
        }
        .card()
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Generation chart

private struct GenerationCard: View {
    enum Period: String, CaseIterable, Identifiable { case daily, weekly, monthly; var id: String { rawValue } }

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @State var period: Period = .daily
    @State var selectedX: Double?

    struct Point: Identifiable {
        let x: Double // hour, or day index
        let date: Date?
        let value: Double?
        var id: Double { x }
    }

    private struct Model {
        let points: [Point]
        let total: Double
        let prevTotal: Double
        let peak: Point?
        let hourly: Bool
    }

    private func model() -> Model {
        let sim = store.sim
        let days = sim.days
        let sites = store.db.sites
        let today = store.now
        switch period {
        case .daily:
            let series = sim.hourlySeries(sites, day: days.dayKey(today), stepMin: 30)
            let pts = series.map { Point(x: $0.hour, date: nil, value: $0.kw) }
            let total = sim.totalKwh(sites, from: today, to: today)
            let yd = days.dayKey(days.addDays(today, -1))
            let h = days.hourOfDay(today)
            let prev = sites.reduce(0.0) { $0 + sim.siteDayKwh($1, day: yd, untilHour: h) }
            let peak = pts.filter { $0.value != nil }.max { ($0.value ?? 0) < ($1.value ?? 0) }
            return Model(points: pts, total: total, prevTotal: prev, peak: peak, hourly: true)
        case .weekly, .monthly:
            let n = period == .weekly ? 7 : 30
            let series = sim.dailySeries(sites, from: days.addDays(today, -(n - 1)), to: today)
            let prev = sim.dailySeries(sites, from: days.addDays(today, -(2 * n - 1)), to: days.addDays(today, -n))
            let pts = series.enumerated().map { Point(x: Double($0.offset), date: $0.element.date, value: $0.element.kwh) }
            let peak = pts.max { ($0.value ?? 0) < ($1.value ?? 0) }
            return Model(points: pts, total: series.reduce(0) { $0 + $1.kwh }, prevTotal: prev.reduce(0) { $0 + $1.kwh }, peak: peak, hourly: false)
        }
    }

    private func xDomain(_ m: Model) -> ClosedRange<Double> {
        if m.hourly { return 0.0...24.0 }
        let upper = Double(max(m.points.count, 1)) - 0.5
        return -0.5...upper
    }

    private func label(for p: Point, hourly: Bool) -> String {
        if hourly {
            let m = Int((p.x * 60).rounded())
            return "\(DayMath.pad2((m / 60) % 24)):\(DayMath.pad2(m % 60))"
        }
        return p.date.map { fmt.dateShort($0) } ?? ""
    }

    var body: some View {
        let m = model()
        let change = OverviewKPIs.pctChange(m.total, m.prevTotal)
        let valueText: (Double) -> String = { m.hourly ? fmt.power($0) : fmt.energy($0) }
        let selected = selectedX.flatMap { x in m.points.min { abs($0.x - x) < abs($1.x - x) } }

        Card(l10n.t("ov.energyGeneration")) {
            Picker("", selection: $period) {
                Text(l10n.t("common.daily")).tag(Period.daily)
                Text(l10n.t("common.weekly")).tag(Period.weekly)
                Text(l10n.t("common.monthly")).tag(Period.monthly)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(maxWidth: 260)
        } content: {
            VStack(alignment: .leading, spacing: 2) {
                Text(fmt.energy(m.total))
                    .font(.system(.title, design: .rounded).weight(.semibold))
                    .contentTransition(.numericText())
                Text(l10n.t("ov.totalEnergy")).font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Text(fmt.signedPct(change)).foregroundStyle(change >= 0 ? Brand.green : Brand.danger)
                        .environment(\.layoutDirection, .leftToRight)
                    Text(l10n.t(period == .daily ? "common.vsYesterday" : "common.vsPrevPeriod")).foregroundStyle(.secondary)
                }
                .font(.caption)
            }
            Chart {
                ForEach(m.points) { p in
                    if let v = p.value {
                        AreaMark(x: .value("x", p.x), y: .value("v", v))
                            .interpolationMethod(.monotone)
                            .foregroundStyle(LinearGradient(colors: [Brand.b500.opacity(0.45), Brand.b500.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                        LineMark(x: .value("x", p.x), y: .value("v", v))
                            .interpolationMethod(.monotone)
                            .foregroundStyle(Brand.b500)
                            .lineStyle(StrokeStyle(lineWidth: 2))
                    }
                }
                if let peak = m.peak, let v = peak.value, v > 0 {
                    PointMark(x: .value("x", peak.x), y: .value("v", v))
                        .symbolSize(70)
                        .foregroundStyle(Brand.b500)
                        .annotation(position: .top, spacing: 4) {
                            Text(valueText(v))
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 6).padding(.vertical, 3)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 6))
                                .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(Theme.cardStroke))
                        }
                }
                if let s = selected, let v = s.value {
                    RuleMark(x: .value("x", s.x))
                        .foregroundStyle(Brand.slate.opacity(0.5))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        .annotation(position: .top, overflowResolution: .init(x: .fit(to: .chart), y: .disabled)) {
                            VStack(spacing: 1) {
                                Text(label(for: s, hourly: m.hourly)).font(.caption2).foregroundStyle(.secondary)
                                Text(valueText(v)).font(.caption.bold())
                            }
                            .padding(6)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                        }
                }
            }
            .chartXSelection(value: $selectedX)
            .chartXScale(domain: xDomain(m))
            .chartXAxis {
                if m.hourly {
                    AxisMarks(values: [0.0, 6.0, 12.0, 18.0, 24.0]) { value in
                        AxisGridLine().foregroundStyle(.clear)
                        AxisValueLabel {
                            if let h = value.as(Double.self) { Text("\(DayMath.pad2(Int(h) % 24)):00") }
                        }
                    }
                } else {
                    AxisMarks(values: .automatic(desiredCount: 5)) { value in
                        AxisValueLabel {
                            if let x = value.as(Double.self) {
                                let i = Int(x.rounded())
                                if i >= 0, i < m.points.count, let d = m.points[i].date { Text(fmt.dateShort(d)) }
                            }
                        }
                    }
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                    AxisValueLabel {
                        if let v = value.as(Double.self) { Text(m.hourly ? Fmt.axisPower(v) : Fmt.axisEnergy(v)) }
                    }
                }
            }
            .frame(height: 210)
            .ltrChart()
            .animation(.easeInOut, value: period)
            .accessibilityLabel(Text("\(l10n.t("ov.energyGeneration")): \(fmt.energy(m.total))"))
        }
    }
}

// MARK: - Energy flow

private struct EnergyFlowCard: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let f = store.sim.energyFlow(store.db.sites, at: store.now)
        let gridIn = f.grid >= 0
        let charging = f.battery >= 0
        Card(l10n.t("ov.energyFlow")) {
            ZStack {
                FlowLines(solar: f.solar > 0.5, grid: abs(f.grid) > 0.5, gridReverse: !gridIn,
                          battery: abs(f.battery) > 0.5, batteryReverse: charging)
                VStack {
                    HStack {
                        FlowNode(icon: "sun.max.fill", tint: Brand.b500, title: l10n.t("ov.solarGen"), value: fmt.power(f.solar))
                        Spacer()
                        FlowNode(icon: "powerplug.fill", tint: Brand.violet, title: l10n.t(gridIn ? "ov.gridImport" : "ov.gridExport"), value: fmt.power(abs(f.grid)))
                    }
                    Spacer()
                    HStack {
                        FlowNode(icon: charging ? "battery.100percent.bolt" : "battery.50percent", tint: Brand.green, title: l10n.t("ov.batteryStorage"),
                                 value: "\(charging ? "+" : "−")\(fmt.power(abs(f.battery))) · \(Int(f.batterySoc.rounded()))%")
                        Spacer()
                        FlowNode(icon: "house.fill", tint: Brand.amber, title: l10n.t("ov.consumption"), value: fmt.power(f.consumption))
                    }
                }
                HouseBadge()
            }
            .frame(height: 250)
            .environment(\.layoutDirection, .leftToRight)
            HStack(spacing: 16) {
                Label(l10n.t(charging ? "ov.charging" : "ov.discharging"),
                      systemImage: charging ? "arrow.up.right" : "arrow.down.right")
                    .foregroundStyle(charging ? Brand.green : Brand.amber)
                Text("\(l10n.t("ov.batteryStorage")): \(fmt.energy(f.batteryCapacity * f.batterySoc / 100)) / \(fmt.energy(f.batteryCapacity))")
                    .foregroundStyle(.secondary)
            }
            .font(.caption)
            .frame(maxWidth: .infinity)
        }
    }
}

private struct FlowNode: View {
    let icon: String
    let tint: Color
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 8) {
            IconTile(systemName: icon, tint: tint, size: 32)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
                Text(value).font(.footnote.weight(.semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
            }
        }
        .padding(8)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 6, y: 2)
        .frame(maxWidth: 170, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private struct HouseBadge: View {
    var body: some View {
        ZStack {
            Circle().fill(Brand.gradient).frame(width: 74, height: 74)
                .shadow(color: Brand.b600.opacity(0.4), radius: 14, y: 6)
            Image(systemName: "house.lodge.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(.white)
        }
        .accessibilityHidden(true)
    }
}

private struct FlowSegment {
    let from: CGPoint
    let to: CGPoint
    let color: Color
    let active: Bool
    let reverse: Bool
}

/// Animated dashed connectors between the four corners and the centre.
private struct FlowLines: View {
    let solar: Bool
    let grid: Bool
    let gridReverse: Bool
    let battery: Bool
    let batteryReverse: Bool

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30)) { context in
            let phase = CGFloat(context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1)) * 20
            Canvas { ctx, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let tl = CGPoint(x: size.width * 0.2, y: size.height * 0.18)
                let tr = CGPoint(x: size.width * 0.8, y: size.height * 0.18)
                let bl = CGPoint(x: size.width * 0.2, y: size.height * 0.82)
                let br = CGPoint(x: size.width * 0.8, y: size.height * 0.82)
                let lines: [FlowSegment] = [
                    FlowSegment(from: tl, to: c, color: Brand.b500, active: solar, reverse: false),
                    FlowSegment(from: tr, to: c, color: Brand.violet, active: grid, reverse: gridReverse),
                    FlowSegment(from: bl, to: c, color: Brand.green, active: battery, reverse: batteryReverse),
                    FlowSegment(from: c, to: br, color: Brand.amber, active: true, reverse: false),
                ]
                for seg in lines {
                    var path = Path()
                    path.move(to: seg.from)
                    path.addQuadCurve(to: seg.to, control: CGPoint(x: (seg.from.x + seg.to.x) / 2, y: seg.from.y))
                    let dashPhase: CGFloat = seg.active ? (seg.reverse ? phase : -phase) : 0
                    let style = StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: seg.active ? [6, 8] : [], dashPhase: dashPhase)
                    ctx.stroke(path, with: .color(seg.color.opacity(seg.active ? 0.9 : 0.2)), style: style)
                }
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - Environmental impact

private struct ImpactCard: View {
    enum Period: String, CaseIterable { case monthly, yearly, all }

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @State var period: Period = .monthly

    var body: some View {
        let s = store.settings
        let sim = store.sim
        let today = store.now
        let from: Date = {
            switch period {
            case .monthly: return sim.days.startOfMonth(today)
            case .yearly: return sim.days.makeDate(year: sim.days.components(today).year, monthIndex0: 0, day: 1)
            case .all:
                let earliest = store.db.sites.map(\.installDate).min() ?? sim.days.dayKey(today)
                return sim.days.parseDay(earliest)
            }
        }()
        let kwh = sim.totalKwh(store.db.sites, from: from, to: today)
        let co2 = Simulator.co2Tons(kwh, factor: s.co2KgPerKwh)
        let trees = s.treeKgPerYear > 0 ? co2 * 1000 / s.treeKgPerYear : 0
        let cars = s.carTonsPerYear > 0 ? co2 / s.carTonsPerYear : 0

        Card(l10n.t("ov.envImpact")) {
            Menu {
                Picker("", selection: $period) {
                    Text(l10n.t("common.monthly")).tag(Period.monthly)
                    Text(l10n.t("common.yearly")).tag(Period.yearly)
                    Text(l10n.t("common.all")).tag(Period.all)
                }
            } label: {
                HStack(spacing: 4) {
                    Text(l10n.t(period == .monthly ? "common.monthly" : period == .yearly ? "common.yearly" : "common.all"))
                    Image(systemName: "chevron.up.chevron.down").font(.caption2)
                }
                .font(.caption.weight(.medium))
            }
        } content: {
            VStack(spacing: 14) {
                impactRow("tree.fill", Brand.green, l10n.t("ov.trees"), fmt.num(trees), l10n.t("ov.trees.unit"))
                impactRow("leaf.fill", Brand.sky, l10n.t("ov.co2Offset"), fmt.num(co2, 1), l10n.t("common.tons"))
                impactRow("car.fill", Brand.amber, l10n.t("ov.cars"), fmt.num(cars, 1), l10n.t("ov.cars.unit"))
            }
        }
    }

    private func impactRow(_ icon: String, _ tint: Color, _ title: String, _ value: String, _ unit: String) -> some View {
        HStack(spacing: 12) {
            IconTile(systemName: icon, tint: tint, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(value).font(.title3.weight(.semibold)).monospacedDigit().contentTransition(.numericText())
                    Text(unit).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Performance & health

private struct PerformanceCard: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let stats = Simulator.deviceStats(store.db.devices)
        let items: [(String, Double)] = [
            (l10n.t("ov.availability"), stats.availability),
            (l10n.t("ov.inverterEff"), stats.inverterEfficiency),
            (l10n.t("ov.batteryHealth"), stats.batteryHealth),
        ]
        Card(l10n.t("ov.perfHealth")) {
            NavigationLink(value: AppRoute.section(.devices)) {
                Text(l10n.t("common.viewAll")).font(.caption)
            }
        } content: {
            VStack(spacing: 14) {
                ForEach(items, id: \.0) { item in
                    HStack(spacing: 12) {
                        Ring(value: item.1, color: ringColor(item.1), lineWidth: 5, size: 50) {
                            Text(fmt.pct(item.1)).font(.system(size: 10, weight: .semibold)).monospacedDigit()
                                .minimumScaleFactor(0.6).lineLimit(1).padding(4)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.0).font(.subheadline.weight(.medium))
                            Text(grade(item.1)).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .accessibilityElement(children: .combine)
                }
                Divider()
                HStack(spacing: 14) {
                    dot(Brand.green, l10n.t("status.online"), stats.online)
                    dot(Brand.amber, l10n.t("status.warning"), stats.warning)
                    dot(Brand.danger, l10n.t("status.offline"), stats.offline)
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private func grade(_ v: Double) -> String {
        v >= 97 ? l10n.t("ov.excellent") : v >= 90 ? l10n.t("ov.good") : v >= 75 ? l10n.t("ov.fair") : l10n.t("ov.poor")
    }

    private func ringColor(_ v: Double) -> Color { v >= 90 ? Brand.green : v >= 75 ? Brand.amber : Brand.danger }

    private func dot(_ c: Color, _ t: String, _ n: Int) -> some View {
        HStack(spacing: 4) {
            Circle().fill(c).frame(width: 7, height: 7)
            Text("\(t) \(n)")
        }
    }
}

// MARK: - Site performance

private struct SitePerformanceCard: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let sim = store.sim
        let day = sim.days.dayKey(store.now)
        let rows = store.db.sites
            .map { s in (site: s, kw: store.currentKw(s), kwh: sim.siteDayKwhCached(s, day: day)) }
            .sorted { $0.kwh > $1.kwh }
            .prefix(6)
        Card(l10n.t("ov.sitePerformance")) {
            NavigationLink(value: AppRoute.section(.sites)) {
                Text(l10n.t("common.viewAll")).font(.caption)
            }
        } content: {
            VStack(spacing: 0) {
                ForEach(Array(rows), id: \.site.id) { row in
                    NavigationLink(value: AppRoute.site(row.site.id)) {
                        HStack(spacing: 10) {
                            Circle().fill(row.site.status.color).frame(width: 8, height: 8)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.site.name).font(.subheadline.weight(.medium)).lineLimit(1)
                                Text(fmt.energy(row.kwh)).font(.caption).foregroundStyle(.secondary).monospacedDigit()
                            }
                            Spacer()
                            Sparkline(values: sim.hourlySeries([row.site], day: day, stepMin: 60).filter { $0.hour >= 6 }.compactMap(\.kw),
                                      color: row.site.status == .active ? Brand.b500 : Brand.slate, fill: false)
                                .frame(width: 56, height: 22)
                            Text(fmt.power(row.kw)).font(.footnote.weight(.semibold)).monospacedDigit()
                                .frame(minWidth: 70, alignment: .trailing)
                            Image(systemName: "chevron.forward").font(.caption2).foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 9)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if row.site.id != rows.last?.site.id { Divider() }
                }
            }
        }
    }
}
