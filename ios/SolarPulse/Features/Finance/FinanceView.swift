import SwiftUI
import Charts
import SolarPulseKit

/// Financial analysis (ROI) — portfolio + per-site KPIs and cumulative cash flow (PLATFORM.md §3.5, web src/pages/Finance.tsx).
struct FinanceView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var analysis: FinanceAnalysis?
    @State var selectedSite: String?

    private struct Key: Hashable {
        let sites: [Site]
        let rate: Double
        let day: String
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let a = analysis {
                    content(a)
                } else {
                    VStack(spacing: 12) {
                        ProgressView()
                        Text(l10n.t("fin.loading")).font(.footnote).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .navigationTitle(l10n.t("fin.title"))
        .task(id: Key(sites: store.db.sites, rate: store.settings.discountRatePct, day: store.sim.todayKey)) {
            let sites = store.db.sites
            let sim = store.sim
            let rate = store.settings.discountRatePct
            let result = await Task.detached(priority: .userInitiated) {
                FinanceAnalysis.compute(sites: sites, sim: sim, discountRatePct: rate)
            }.value
            withAnimation(.easeOut) { analysis = result }
        }
    }

    @ViewBuilder
    private func content(_ a: FinanceAnalysis) -> some View {
        let focus = selectedSite.flatMap { id in a.perSite.first { $0.site.id == id } }
        let result = focus?.result ?? a.portfolio
        Text(l10n.t("fin.subtitle")).font(.subheadline).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)

        Menu {
            Picker("", selection: $selectedSite) {
                Text(l10n.t("fin.portfolio")).tag(String?.none)
                ForEach(a.perSite, id: \.site.id) { row in Text(row.site.name).tag(String?.some(row.site.id)) }
            }
        } label: {
            Label(focus?.site.name ?? l10n.t("fin.portfolio"), systemImage: "building.2")
                .font(.footnote.weight(.medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Theme.card, in: Capsule())
        }
        .frame(maxWidth: .infinity, alignment: .leading)

        if let result {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 10)], spacing: 10) {
                StatTile(icon: "banknote.fill", tint: Brand.b500, title: l10n.t("fin.investment"), value: fmt.money(result.systemCost))
                StatTile(icon: "hourglass", tint: Brand.amber, title: l10n.t("fin.payback"),
                         value: result.paybackYears.map { l10n.t("fin.years", ["n": fmt.num($0, 1)]) } ?? l10n.t("fin.never"))
                StatTile(icon: "percent", tint: Brand.green, title: l10n.t("fin.roi"), value: fmt.pct(result.roiPct, 0))
                StatTile(icon: "chart.bar.xaxis", tint: Brand.violet, title: l10n.t("fin.npv"), value: fmt.money(result.npv),
                         sub: "\(l10n.t("fin.discountRate")) \(fmt.pct(store.settings.discountRatePct, 1))")
                StatTile(icon: "arrow.up.right.circle.fill", tint: Brand.sky, title: l10n.t("fin.irr"), value: result.irr.map { fmt.pct($0 * 100) } ?? "—")
                StatTile(icon: "bolt.badge.clock.fill", tint: Brand.b600, title: l10n.t("fin.lcoe"),
                         value: result.lcoe.map { "\(fmt.money2($0))\(l10n.t("fin.perKwh"))" } ?? "—")
                StatTile(icon: "dollarsign.circle.fill", tint: Brand.green, title: l10n.t("fin.savings"), value: fmt.money(result.annualSavings),
                         sub: l10n.t("fin.yr1"))
                StatTile(icon: "sun.max.fill", tint: Brand.amber, title: l10n.t("fin.e1"), value: fmt.energy(result.energy.first ?? 0))
            }

            Card(l10n.t("fin.cashFlow")) {
                cashFlowChart(result)
            }
        }

        Card(l10n.t("fin.perSite")) {
            ForEach(a.perSite.sorted { ($0.result.paybackYears ?? 99) < ($1.result.paybackYears ?? 99) }, id: \.site.id) { row in
                Button {
                    withAnimation(.snappy) { selectedSite = row.site.id }
                } label: {
                    HStack(spacing: 10) {
                        Circle().fill(row.site.status.color).frame(width: 8, height: 8)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.site.name).font(.subheadline.weight(.medium)).lineLimit(1)
                            Text("\(l10n.t("fin.payback")): \(row.result.paybackYears.map { l10n.t("fin.years", ["n": fmt.num($0, 1)]) } ?? l10n.t("fin.never"))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(fmt.pct(row.result.roiPct, 0)).font(.subheadline.weight(.semibold)).monospacedDigit()
                                .foregroundStyle(row.result.roiPct >= 0 ? Brand.green : Brand.danger)
                            Text(row.result.irr.map { "\(l10n.t("fin.irr")) \(fmt.pct($0 * 100))" } ?? "—")
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selectedSite == row.site.id ? .isSelected : [])
                Divider()
            }
        }

        Card(l10n.t("fin.assumptions")) {
            Text(l10n.t("fin.model")).font(.footnote).foregroundStyle(.secondary)
            InfoRow(label: l10n.t("fin.discountRate"), value: fmt.pct(store.settings.discountRatePct, 1), ltrValue: true)
            InfoRow(label: l10n.t("fin.horizon"), value: l10n.t("fin.years", ["n": "\(Finance.horizonYears)"]))
            NavigationLink(value: AppRoute.section(.settings)) {
                Label(l10n.t("fin.editRate"), systemImage: "slider.horizontal.3").font(.footnote)
            }
        }
    }

    private struct YearPoint: Identifiable {
        let year: Int
        let cashFlow: Double
        let cumulative: Double
        var id: Int { year }
    }

    private func cashFlowChart(_ r: Finance.Result) -> some View {
        let points = r.cashFlows.indices.map { YearPoint(year: $0, cashFlow: r.cashFlows[$0], cumulative: r.cumulative[$0]) }
        let breakEven = r.paybackYears.map { Int($0.rounded(.up)) }
        let annualLabel = l10n.t("fin.annualCf")
        let cumulativeLabel = l10n.t("fin.cumulative")
        return VStack(alignment: .leading, spacing: 8) {
            if let breakEven {
                Label(l10n.t("fin.breakEven", ["n": "\(breakEven)"]), systemImage: "flag.checkered")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Brand.green)
            }
            Chart {
                ForEach(points) { p in
                    BarMark(x: .value("y", Double(p.year)), y: .value("cf", p.cashFlow))
                        .foregroundStyle(by: .value("s", annualLabel))
                        .opacity(0.55)
                    LineMark(x: .value("y", Double(p.year)), y: .value("cum", p.cumulative))
                        .foregroundStyle(by: .value("s", cumulativeLabel))
                        .interpolationMethod(.monotone)
                        .lineStyle(StrokeStyle(lineWidth: 2.5))
                }
                RuleMark(y: .value("zero", 0))
                    .foregroundStyle(Brand.slate.opacity(0.6))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))
                if let pb = r.paybackYears {
                    RuleMark(x: .value("payback", pb))
                        .foregroundStyle(Brand.green.opacity(0.7))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 3]))
                }
            }
            .chartForegroundStyleScale([annualLabel: Brand.b400, cumulativeLabel: Brand.green])
            .chartXAxis {
                AxisMarks(values: [0.0, 5.0, 10.0, 15.0, 20.0, 25.0]) { v in
                    AxisGridLine().foregroundStyle(.clear)
                    AxisValueLabel { if let y = v.as(Double.self) { Text("\(Int(y))") } }
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 5)) { v in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                    AxisValueLabel { if let x = v.as(Double.self) { Text(compactMoney(x)) } }
                }
            }
            .chartLegend(position: .top, alignment: .leading)
            .frame(height: 240)
            .ltrChart()
        }
    }

    private func compactMoney(_ v: Double) -> String {
        let a = abs(v)
        if a >= 1e6 { return "\(fmt.num(v / 1e6, 1))M" }
        if a >= 1e3 { return "\(fmt.num(v / 1e3, 0))k" }
        return fmt.num(v)
    }
}

/// Per-site + portfolio results (web `analyzePortfolio`).
struct FinanceAnalysis: Sendable {
    struct Row: Sendable {
        let site: Site
        let result: Finance.Result
    }

    let perSite: [Row]
    let portfolio: Finance.Result?

    static func compute(sites: [Site], sim: Simulator, discountRatePct: Double) -> FinanceAnalysis {
        let rows = sites.map { site in
            Row(site: site, result: Finance.analyze(Finance.inputs(for: site, e1: Finance.yearOneEnergy(site, sim: sim)),
                                                    discountRatePct: discountRatePct))
        }
        return FinanceAnalysis(perSite: rows, portfolio: Finance.portfolio(rows.map(\.result), discountRatePct: discountRatePct))
    }
}
