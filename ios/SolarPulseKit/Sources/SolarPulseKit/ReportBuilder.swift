import Foundation

/// The five report kinds of the Reports page (port of `build()` in src/pages/Reports.tsx).
public struct ReportBuilder: Sendable {
    public let sim: Simulator

    public init(sim: Simulator) {
        self.sim = sim
    }

    public struct SummaryItem: Sendable, Identifiable, Equatable {
        public let label: String
        public let value: String
        public var id: String { label }
    }

    public struct ChartPoint: Sendable, Identifiable, Equatable {
        public let date: Date
        public let value: Double
        public var id: Date { date }
    }

    public struct Report: Sendable, Equatable {
        public let columns: [String]
        /// Raw values for CSV.
        public let rows: [[String]]
        /// Localised cell text for preview / PDF.
        public let display: [[String]]
        public let summary: [SummaryItem]
        public let chart: [ChartPoint]
        /// Chart values are CO₂ tons instead of kWh.
        public let chartIsCO2: Bool
    }

    /// - Parameter t: translation lookup (web dictionary keys, e.g. `common.site`).
    public func build(kind: ReportKind, from: Date, to: Date, sites: [Site], db: DB, fmt: Fmt,
                      t: (String) -> String) -> Report {
        let days = sim.days
        let ids = Set(sites.map(\.id))
        let siteName: (String) -> String = { id in db.sites.first { $0.id == id }?.name ?? "—" }
        let fromK = days.dayKey(from)
        let toK = days.dayKey(to)
        func r2(_ v: Double) -> String { String(format: "%.2f", v) }

        switch kind {
        case .energy:
            let nDays = max(1, Int(jsRound(to.timeIntervalSince(from) / 86_400)) + 1)
            let data = sites.map { s -> (Site, Double, Double, Double) in
                let kwh = sim.totalKwh([s], from: from, to: to)
                let yld = s.capacityKw > 0 ? kwh / Double(nDays) / s.capacityKw : 0
                return (s, kwh, yld, kwh * s.pricePerKwh)
            }
            let total = data.reduce(0.0) { $0 + $1.1 }
            return Report(
                columns: [t("common.site"), t("sites.capacity"), t("an.production.short"), t("sites.yield"), t("ov.totalRevenue")],
                rows: data.map { [$0.0.name, fmt.num($0.0.capacityKw, 1), String(Int(jsRound($0.1))), r2($0.2), r2($0.3)] },
                display: data.map { [$0.0.name, fmt.power($0.0.capacityKw), fmt.energy($0.1), "\(fmt.num($0.2, 2)) kWh/kWp", fmt.money($0.3)] },
                summary: [
                    SummaryItem(label: t("an.production"), value: fmt.energy(total)),
                    SummaryItem(label: t("ov.totalRevenue"), value: fmt.money(data.reduce(0.0) { $0 + $1.3 })),
                    SummaryItem(label: t("rep.sites"), value: String(sites.count)),
                ],
                chart: sim.dailySeries(sites, from: from, to: to).map { ChartPoint(date: $0.date, value: $0.kwh) },
                chartIsCO2: false
            )
        case .financial:
            let inv = db.invoices.filter { ids.contains($0.siteId) && $0.issuedAt >= fromK && $0.issuedAt <= toK }
            let by = sites.compactMap { s -> (Site, Int, Double, Double)? in
                let l = inv.filter { $0.siteId == s.id }
                guard !l.isEmpty else { return nil }
                let billed = l.reduce(0.0) { $0 + $1.amount }
                let paid = l.filter { $0.status == .paid }.reduce(0.0) { $0 + $1.amount }
                return (s, l.count, billed, paid)
            }
            let billed = by.reduce(0.0) { $0 + $1.2 }
            let paid = by.reduce(0.0) { $0 + $1.3 }
            return Report(
                columns: [t("common.site"), t("bill.customer"), t("bill.invoice"), t("bill.totalBilled"), t("bill.paid"), t("bill.pending")],
                rows: by.map { [$0.0.name, $0.0.customer, String($0.1), r2($0.2), r2($0.3), r2($0.2 - $0.3)] },
                display: by.map { [$0.0.name, $0.0.customer, String($0.1), fmt.money2($0.2), fmt.money2($0.3), fmt.money2($0.2 - $0.3)] },
                summary: [
                    SummaryItem(label: t("bill.totalBilled"), value: fmt.money(billed)),
                    SummaryItem(label: t("bill.paid"), value: fmt.money(paid)),
                    SummaryItem(label: t("bill.pending"), value: fmt.money(billed - paid)),
                ],
                chart: [],
                chartIsCO2: false
            )
        case .devices:
            let devs = db.devices.filter { ids.contains($0.siteId) }
            let avg = devs.isEmpty ? 0 : devs.reduce(0.0) { $0 + $1.health } / Double(devs.count)
            return Report(
                columns: [t("common.name"), t("common.site"), t("common.type"), t("dev.model"), t("common.status"), t("dev.health"), t("dev.efficiency"), t("dev.firmware")],
                rows: devs.map { [$0.name, siteName($0.siteId), $0.type.rawValue, $0.model, $0.status.rawValue, fmt.num($0.health, 1), fmt.num($0.efficiency, 1), $0.firmware] },
                display: devs.map { [$0.name, siteName($0.siteId), t("devType.\($0.type.rawValue)"), $0.model, t("status.\($0.status.rawValue)"), "\(fmt.num($0.health))%", fmt.pct($0.efficiency), $0.firmware] },
                summary: [
                    SummaryItem(label: t("dev.title"), value: String(devs.count)),
                    SummaryItem(label: t("status.online"), value: String(devs.filter { $0.status == .online }.count)),
                    SummaryItem(label: t("status.offline"), value: String(devs.filter { $0.status == .offline }.count)),
                    SummaryItem(label: t("dev.health"), value: fmt.pct(avg)),
                ],
                chart: [],
                chartIsCO2: false
            )
        case .maintenance:
            let tk = db.tickets.filter { x in
                let created = String(x.createdAt.prefix(10))
                return ids.contains(x.siteId) && ((created >= fromK && created <= toK) || (x.dueDate >= fromK && x.dueDate <= toK))
            }
            return Report(
                columns: [t("mt.ticketTitle"), t("common.site"), t("mt.priority"), t("common.status"), t("mt.assignee"), t("mt.due")],
                rows: tk.map { [$0.title, siteName($0.siteId), $0.priority.rawValue, $0.status.rawValue, $0.assignee, $0.dueDate] },
                display: tk.map { [$0.title, siteName($0.siteId), t("prio.\($0.priority.rawValue)"), t("status.\($0.status.rawValue)"), $0.assignee.isEmpty ? "—" : $0.assignee, fmt.date($0.dueDate)] },
                summary: [
                    SummaryItem(label: t("common.total"), value: String(tk.count)),
                    SummaryItem(label: t("status.resolved"), value: String(tk.filter { $0.status == .resolved }.count)),
                    SummaryItem(label: t("status.open"), value: String(tk.filter { $0.status != .resolved }.count)),
                ],
                chart: [],
                chartIsCO2: false
            )
        case .environment:
            let s = db.settings
            let data = sites.map { x -> (Site, Double, Double) in
                let kwh = sim.totalKwh([x], from: from, to: to)
                return (x, kwh, Simulator.co2Tons(kwh, factor: s.co2KgPerKwh))
            }
            let co2 = data.reduce(0.0) { $0 + $1.2 }
            let trees: (Double) -> Double = { c in s.treeKgPerYear > 0 ? c * 1000 / s.treeKgPerYear : 0 }
            let cars: (Double) -> Double = { c in s.carTonsPerYear > 0 ? c / s.carTonsPerYear : 0 }
            return Report(
                columns: [t("common.site"), t("an.production.short") + " (kWh)", "CO₂ (t)", t("ov.trees"), t("ov.cars")],
                rows: data.map { [$0.0.name, String(Int(jsRound($0.1))), r2($0.2), String(Int(jsRound(trees($0.2)))), String(format: "%.1f", cars($0.2))] },
                display: data.map { [$0.0.name, fmt.energy($0.1), fmt.num($0.2, 2), fmt.num(trees($0.2)), fmt.num(cars($0.2), 1)] },
                summary: [
                    SummaryItem(label: t("ov.co2Offset"), value: "\(fmt.num(co2, 1)) \(t("common.tons"))"),
                    SummaryItem(label: t("ov.trees"), value: fmt.num(trees(co2))),
                    SummaryItem(label: t("ov.cars"), value: fmt.num(cars(co2), 1)),
                ],
                chart: sim.dailySeries(sites, from: from, to: to).map { ChartPoint(date: $0.date, value: Simulator.co2Tons($0.kwh, factor: s.co2KgPerKwh)) },
                chartIsCO2: true
            )
        }
    }

    /// CSV with a BOM so Excel opens UTF-8 (Uyghur/Arabic) text correctly.
    public static func csv(_ rows: [[String]]) -> String {
        func esc(_ v: String) -> String {
            v.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" }) ? "\"" + v.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : v
        }
        return "\u{FEFF}" + rows.map { $0.map(esc).joined(separator: ",") }.joined(separator: "\n")
    }
}
