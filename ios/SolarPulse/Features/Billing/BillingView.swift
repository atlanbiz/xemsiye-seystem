import SwiftUI
import Charts
import SolarPulseKit

/// Billing — invoices, mark paid, generate a month's invoices (src/pages/Billing.tsx).
struct BillingView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var query = ""
    @State var statusFilter: InvoiceStatus?
    @State var periodFilter: String?
    @State var siteFilter: String?
    @State var showGenerate = false
    @State var deleting: Invoice?
    @State var paidCounter = 0

    private var rows: [Invoice] {
        let q = query.lowercased()
        return store.db.invoices
            .filter { (statusFilter == nil || $0.status == statusFilter) && (periodFilter == nil || $0.period == periodFilter) && (siteFilter == nil || $0.siteId == siteFilter) }
            .filter { i in q.isEmpty || i.number.lowercased().contains(q) || i.customer.lowercased().contains(q) }
            .sorted { $0.period != $1.period ? $0.period > $1.period : $0.number < $1.number }
    }

    private var periods: [String] { Array(Set(store.db.invoices.map(\.period))).sorted(by: >) }

    struct ChartRow: Identifiable {
        let period: String
        let date: Date
        let series: String
        let amount: Double
        var id: String { period + series }
    }

    var body: some View {
        let invoices = store.db.invoices
        let sum: (InvoiceStatus?) -> Double = { s in invoices.filter { s == nil || $0.status == s }.reduce(0) { $0 + $1.amount } }
        let total = sum(nil)
        let paid = sum(.paid)
        let list = rows

        List {
            Section {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 10)], spacing: 10) {
                    StatTile(icon: "doc.text.fill", tint: Brand.b500, title: l10n.t("bill.totalBilled"), value: fmt.money(total), sub: "\(invoices.count) \(l10n.t("bill.invoice"))")
                    StatTile(icon: "checkmark.circle.fill", tint: Brand.green, title: l10n.t("bill.paid"), value: fmt.money(paid), sub: fmt.pct(total > 0 ? paid / total * 100 : 0))
                    StatTile(icon: "clock.fill", tint: Brand.amber, title: l10n.t("bill.pending"), value: fmt.money(sum(.pending)))
                    StatTile(icon: "exclamationmark.circle.fill", tint: Brand.danger, title: l10n.t("bill.overdue"), value: fmt.money(sum(.overdue)))
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)

                revenueChart
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)

                filters
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                    .listRowBackground(Color.clear)
            }

            Section {
                if list.isEmpty {
                    ContentUnavailableView(l10n.t("common.noData"), systemImage: "doc.text.magnifyingglass")
                } else {
                    ForEach(list) { inv in
                        NavigationLink(value: AppRoute.invoice(inv.id)) {
                            InvoiceRow(invoice: inv)
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            if inv.status != .paid {
                                Button { Task { await markPaid(inv) } } label: { Label(l10n.t("bill.markPaid"), systemImage: "checkmark.circle") }
                                    .tint(Brand.green)
                            }
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { deleting = inv } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                        }
                    }
                }
            } header: {
                Text("\(l10n.t("common.showing")) \(list.count) \(l10n.t("common.of")) \(invoices.count)")
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("bill.title"))
        .searchable(text: $query, prompt: Text(l10n.t("common.search")))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showGenerate = true } label: { Label(l10n.t("bill.generate"), systemImage: "doc.badge.plus") }
            }
        }
        .sheet(isPresented: $showGenerate) {
            GenerateInvoicesSheet { month in periodFilter = month }
                .presentationDetents([.medium])
        }
        .sensoryFeedback(.success, trigger: paidCounter)
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                if let inv = deleting {
                    Task {
                        await store.delete(Invoice.self, id: inv.id)
                        store.show(l10n.t("common.deleted"), tone: .info)
                    }
                }
            }
        }
    }

    private var revenueChart: some View {
        let ps = periods.reversed()
        var data: [ChartRow] = []
        for p in ps {
            let (y, m) = DayMath.parseMonth(p)
            let d = store.sim.days.makeDate(year: y, monthIndex0: m - 1, day: 1)
            let inPeriod = store.db.invoices.filter { $0.period == p }
            data.append(ChartRow(period: p, date: d, series: "paid", amount: inPeriod.filter { $0.status == .paid }.reduce(0) { $0 + $1.amount }))
            data.append(ChartRow(period: p, date: d, series: "open", amount: inPeriod.filter { $0.status != .paid }.reduce(0) { $0 + $1.amount }))
        }
        let paidLabel = l10n.status("paid")
        let openLabel = l10n.status("pending")
        return Card(l10n.t("bill.revenueTrend")) {
            Chart(data) { row in
                BarMark(x: .value("m", row.period), y: .value("amount", row.amount))
                    .foregroundStyle(by: .value("s", row.series == "paid" ? paidLabel : openLabel))
                    .cornerRadius(4)
            }
            .chartForegroundStyleScale([paidLabel: Brand.green, openLabel: Brand.amber])
            .chartXAxis {
                AxisMarks { v in
                    AxisValueLabel {
                        if let p = v.as(String.self) {
                            Text(monthLabel(p))
                        }
                    }
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                    AxisValueLabel { if let x = v.as(Double.self) { Text("\(Int((x / 1000).rounded()))k") } }
                }
            }
            .chartLegend(position: .top, alignment: .leading)
            .frame(height: 190)
            .ltrChart()
        }
    }

    private func monthLabel(_ period: String) -> String {
        let ym = DayMath.parseMonth(period)
        return fmt.monthShort(store.sim.days.makeDate(year: ym.year, monthIndex0: ym.month - 1, day: 1))
    }

    private var statusOptions: [(InvoiceStatus?, String)] {
        var out: [(InvoiceStatus?, String)] = [(nil, "\(l10n.t("common.status")): \(l10n.t("common.all"))")]
        for s in InvoiceStatus.allCases { out.append((s, l10n.status(s.rawValue))) }
        return out
    }

    private var siteOptions: [(String?, String)] {
        var out: [(String?, String)] = [(nil, l10n.t("an.allSites"))]
        for s in store.db.sites { out.append((s.id, s.name)) }
        return out
    }

    private var periodOptions: [(String?, String)] {
        var out: [(String?, String)] = [(nil, "\(l10n.t("bill.period")): \(l10n.t("common.all"))")]
        for p in periods { out.append((p, fmt.period(p))) }
        return out
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                menuFilter(icon: "building.2", options: siteOptions, selection: $siteFilter)
                menuFilter(icon: "calendar", options: periodOptions, selection: $periodFilter)
            }
            ChipPicker(options: statusOptions, selection: $statusFilter)
        }
    }

    private func menuFilter(icon: String, options: [(String?, String)], selection: Binding<String?>) -> some View {
        Menu {
            Picker("", selection: selection) {
                ForEach(options, id: \.0) { o in Text(o.1).tag(o.0) }
            }
        } label: {
            Label(options.first { $0.0 == selection.wrappedValue }?.1 ?? "", systemImage: icon)
                .font(.footnote.weight(.medium))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Theme.card, in: Capsule())
        }
    }

    private func markPaid(_ inv: Invoice) async {
        await BillingActions.markPaid(inv, store: store, l10n: l10n)
        paidCounter += 1
    }
}

enum BillingActions {
    @MainActor
    static func markPaid(_ inv: Invoice, store: AppStore, l10n: L10n) async {
        var next = inv
        next.status = .paid
        next.paidAt = store.sim.todayKey
        await store.save(next)
        if store.settings.notifyBilling {
            await store.notify(title: l10n.t("bill.markedPaid"), body: "\(inv.number) · \(inv.customer)", kind: .success, link: "/billing?open=\(inv.id)")
        }
        store.show(l10n.t("bill.markedPaid"))
    }
}

struct InvoiceRow: View {
    let invoice: Invoice
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(invoice.number).font(.subheadline.weight(.semibold)).environment(\.layoutDirection, .leftToRight)
                Text(invoice.customer).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Text("\(fmt.period(invoice.period)) · \(fmt.energy(invoice.energyKwh))").font(.caption2).foregroundStyle(.tertiary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(fmt.money2(invoice.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                StatusBadge(text: l10n.status(invoice.status.rawValue), color: invoice.status.color)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// "Generate invoices for month" sheet.
struct GenerateInvoicesSheet: View {
    var onGenerated: (String) -> Void

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @Environment(\.dismiss) private var dismiss
    @State var month = ""
    @State var busy = false

    private var months: [String] {
        let days = store.sim.days
        return (0..<12).map { days.monthKey(days.addMonths(store.now, -$0)) }
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker(l10n.t("bill.generateFor"), selection: $month) {
                    ForEach(months, id: \.self) { m in Text(fmt.period(m)).tag(m) }
                }
                .pickerStyle(.wheel)
            }
            .navigationTitle(l10n.t("bill.generate"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("common.cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("bill.generate")) { Task { await generate() } }.disabled(busy || month.isEmpty)
                }
            }
            .onAppear {
                if month.isEmpty { month = store.sim.days.monthKey(store.sim.days.addMonths(store.now, -1)) }
            }
        }
    }

    private func generate() async {
        busy = true
        defer { busy = false }
        let seed = SeedBuilder(sim: store.sim)
        let existing = Set(store.db.invoices.filter { $0.period == month }.map(\.siteId))
        let candidates = store.db.sites.filter { !existing.contains($0.id) }
        let list = candidates.enumerated()
            .map { seed.buildInvoice($0.element, month: month, seq: existing.count + $0.offset + 1) }
            .filter { $0.energyKwh > 0 }
        guard !list.isEmpty else {
            store.show(l10n.t("bill.alreadyExists"), tone: .warning)
            return
        }
        await store.saveMany(list)
        let message = l10n.t("bill.generated", ["n": String(list.count)])
        if store.settings.notifyBilling {
            await store.notify(title: message, body: month, kind: .info, link: "/billing")
        }
        store.show(message)
        onGenerated(month)
        dismiss()
    }
}
