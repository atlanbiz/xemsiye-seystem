import SwiftUI
import Charts
import SolarPulseKit

/// Reports — 5 kinds, preview, save, PDF / CSV export (src/pages/Reports.tsx).
struct ReportsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var kind: ReportKind = .energy
    @State var from = Date()
    @State var to = Date()
    @State var siteIds: Set<String> = []
    @State var generated: GeneratedReport?
    @State var pdfURL: URL?
    @State var csvURL: URL?
    @State var initialized = false
    @State var busy = false

    struct GeneratedReport: Equatable {
        let kind: ReportKind
        let from: String
        let to: String
        let siteIds: [String]
        let report: ReportBuilder.Report
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                builder
                if busy {
                    ProgressView(l10n.t("common.preparing")).frame(maxWidth: .infinity, minHeight: 120)
                }
                if let g = generated {
                    preview(g)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                history
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground()
        .navigationTitle(l10n.t("rep.title"))
        .onAppear {
            guard !initialized else { return }
            initialized = true
            let days = store.sim.days
            from = days.addMonths(store.now, -1)
            to = days.addDays(days.startOfMonth(store.now), -1)
        }
        .sensoryFeedback(.success, trigger: generated?.report)
    }

    // MARK: builder

    private var builder: some View {
        Card(l10n.t("rep.kind")) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 8)], spacing: 8) {
                ForEach(ReportKind.allCases) { k in
                    let on = k == kind
                    Button {
                        withAnimation(.snappy) { kind = k }
                    } label: {
                        Label(l10n.reportKind(k), systemImage: k.icon)
                            .font(.footnote.weight(.medium))
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
                            .padding(10)
                            .foregroundStyle(on ? Brand.b700 : Color.primary)
                            .background(on ? Brand.b50 : Theme.subtleFill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(on ? Brand.b400 : Color.clear, lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            DatePicker(l10n.t("common.from"), selection: $from, in: ...to, displayedComponents: .date)
            DatePicker(l10n.t("common.to"), selection: $to, in: from..., displayedComponents: .date)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    quick(7, "common.last7")
                    quick(30, "common.last30")
                    quick(90, "common.last90")
                    quick(365, "common.last12m")
                }
            }
            Text(l10n.t("rep.sites")).font(.caption.weight(.medium)).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    chip(l10n.t("rep.allSites"), on: siteIds.isEmpty) { siteIds.removeAll() }
                    ForEach(store.db.sites) { s in
                        chip(s.name, on: siteIds.contains(s.id)) {
                            if siteIds.contains(s.id) { siteIds.remove(s.id) } else { siteIds.insert(s.id) }
                        }
                    }
                }
            }
            Button {
                Task { await generate() }
            } label: {
                Label(l10n.t("rep.generate"), systemImage: "doc.text.fill")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .foregroundStyle(.white)
                    .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(busy || from > to)
        }
    }

    private func quick(_ n: Int, _ key: String) -> some View {
        Button(l10n.t(key)) {
            let days = store.sim.days
            from = days.addDays(store.now, -n + 1)
            to = store.now
        }
        .font(.caption)
        .buttonStyle(.bordered)
    }

    private func chip(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption)
                .lineLimit(1)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .foregroundStyle(on ? Color.white : Color.primary)
                .background(on ? Brand.primary : Theme.subtleFill, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    // MARK: preview

    private func preview(_ g: GeneratedReport) -> some View {
        Card(l10n.t("rep.preview")) {
            HStack(spacing: 12) {
                Button { Task { await save(g) } } label: { Image(systemName: "tray.and.arrow.down") }
                    .accessibilityLabel(Text(l10n.t("common.save")))
                if let csvURL {
                    ShareLink(item: csvURL) { Image(systemName: "tablecells") }
                        .accessibilityLabel(Text(l10n.t("common.export")))
                }
                if let pdfURL {
                    ShareLink(item: pdfURL) { Image(systemName: "square.and.arrow.up") }
                        .accessibilityLabel(Text(l10n.t("common.downloadPdf")))
                }
            }
        } content: {
            ScrollView(.horizontal, showsIndicators: false) {
                ReportDocument(kind: g.kind, from: g.from, to: g.to, siteCount: g.siteIds.count, report: g.report,
                               company: store.settings.company, l10n: l10n, fmt: fmt, compact: false)
                    .frame(minWidth: 320)
            }
        }
    }

    // MARK: history

    private var history: some View {
        Card(l10n.t("rep.history")) {
            if store.db.reports.isEmpty {
                ContentUnavailableView(l10n.t("rep.empty"), systemImage: "folder")
            } else {
                ForEach(store.db.reports) { r in
                    HStack {
                        IconTile(systemName: r.kind.icon, tint: Brand.b500, size: 32)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(l10n.reportKind(r.kind)).font(.subheadline.weight(.medium))
                            Text("\(r.from) → \(r.to)").font(.caption).foregroundStyle(.secondary).environment(\.layoutDirection, .leftToRight)
                            Text("\(fmt.ago(r.createdAt, now: store.now)) · \(r.siteIds.isEmpty ? l10n.t("rep.allSites") : "\(r.siteIds.count) \(l10n.t("rep.sites"))")")
                                .font(.caption2).foregroundStyle(.tertiary)
                        }
                        Spacer()
                        Button(l10n.t("rep.open")) { Task { await open(r) } }
                            .buttonStyle(.bordered)
                            .font(.caption)
                        Button(role: .destructive) {
                            Task { await store.delete(SavedReport.self, id: r.id) }
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(Text(l10n.t("common.delete")))
                    }
                }
            }
        }
    }

    // MARK: actions

    private func generate() async {
        let days = store.sim.days
        await run(kind: kind, from: days.dayKey(from), to: days.dayKey(to), siteIds: Array(siteIds).sorted())
    }

    private func open(_ r: SavedReport) async {
        let days = store.sim.days
        kind = r.kind
        from = days.parseDay(r.from)
        to = days.parseDay(r.to)
        siteIds = Set(r.siteIds)
        await run(kind: r.kind, from: r.from, to: r.to, siteIds: r.siteIds)
    }

    private func run(kind: ReportKind, from fromKey: String, to toKey: String, siteIds ids: [String]) async {
        busy = true
        defer { busy = false }
        let sim = store.sim
        let db = store.db
        let f = fmt
        let t = l10n
        let sites = ids.isEmpty ? db.sites : db.sites.filter { ids.contains($0.id) }
        let fromDate = sim.days.parseDay(fromKey)
        let toParsed = sim.days.parseDay(toKey)
        let toDate = toParsed > sim.now ? sim.now : toParsed
        let report = await Task.detached(priority: .userInitiated) {
            ReportBuilder(sim: sim).build(kind: kind, from: fromDate, to: toDate, sites: sites, db: db, fmt: f, t: { t.t($0) })
        }.value
        let g = GeneratedReport(kind: kind, from: fromKey, to: toKey, siteIds: ids, report: report)
        withAnimation(.spring(duration: 0.45)) { generated = g }
        exportFiles(g)
    }

    private func exportFiles(_ g: GeneratedReport) {
        let base = "\(g.kind.rawValue)-report-\(g.from)_\(g.to)"
        let csv = ReportBuilder.csv([g.report.columns] + g.report.rows)
        let csvFile = URL.temporaryDirectory.appending(path: base + ".csv")
        do {
            try csv.write(to: csvFile, atomically: true, encoding: .utf8)
            csvURL = csvFile
        } catch {
            csvURL = nil
        }
        pdfURL = PDFExporter.render(
            ReportDocument(kind: g.kind, from: g.from, to: g.to, siteCount: g.siteIds.count, report: g.report,
                           company: store.settings.company, l10n: l10n, fmt: fmt, compact: true).padding(36),
            fileName: base + ".pdf", l10n: l10n, fmt: fmt)
        if pdfURL == nil { store.show(l10n.t("common.pdfFailed"), tone: .error) }
    }

    private func save(_ g: GeneratedReport) async {
        let r = SavedReport(id: makeId("rep-"), kind: g.kind, title: "\(l10n.reportKind(g.kind)) · \(g.from) → \(g.to)",
                            from: g.from, to: g.to, siteIds: g.siteIds, createdAt: ISODate.string(Date()))
        await store.save(r)
        store.show(l10n.t("rep.saved"))
    }
}

/// Printable report (preview + PDF).
struct ReportDocument: View {
    let kind: ReportKind
    let from: String
    let to: String
    let siteCount: Int
    let report: ReportBuilder.Report
    let company: String
    let l10n: L10n
    let fmt: Fmt
    /// PDF mode: smaller type so wide tables fit the A4 width.
    var compact: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            DocumentHeader(title: l10n.reportKind(kind),
                           subtitle: "\(fmt.date(from)) — \(fmt.date(to)) · \(siteCount > 0 ? "\(siteCount) \(l10n.t("rep.sites"))" : l10n.t("rep.allSites"))",
                           company: company)
            Divider()
            LazyVGrid(columns: [GridItem(.adaptive(minimum: compact ? 110 : 140), spacing: 8)], spacing: 8) {
                ForEach(report.summary) { s in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.label).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
                        Text(s.value).font(compact ? .footnote.weight(.semibold) : .headline).monospacedDigit()
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.gray.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            if report.chart.count > 1 {
                Chart(report.chart) { p in
                    AreaMark(x: .value("d", p.date, unit: .day), y: .value("v", p.value))
                        .foregroundStyle(Brand.b500.opacity(0.18))
                    LineMark(x: .value("d", p.date, unit: .day), y: .value("v", p.value))
                        .foregroundStyle(Brand.b500)
                }
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 5)) { v in
                        AxisValueLabel { if let d = v.as(Date.self) { Text(fmt.dateShort(d)) } }
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [3, 3]))
                        AxisValueLabel {
                            if let x = v.as(Double.self) { Text(report.chartIsCO2 ? "\(fmt.num(x, 1)) t" : Fmt.axisEnergy(x)) }
                        }
                    }
                }
                .frame(height: compact ? 150 : 190)
                .environment(\.layoutDirection, .leftToRight)
            }
            if report.display.isEmpty {
                Text(l10n.t("common.noData")).font(.footnote).foregroundStyle(.secondary)
            } else {
                Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 6) {
                    GridRow {
                        ForEach(Array(report.columns.enumerated()), id: \.offset) { c in
                            Text(c.element).font(.system(size: compact ? 7.5 : 11, weight: .semibold)).foregroundStyle(.secondary)
                        }
                    }
                    Divider().gridCellUnsizedAxes(.horizontal)
                    ForEach(Array(report.display.enumerated()), id: \.offset) { row in
                        GridRow {
                            ForEach(Array(row.element.enumerated()), id: \.offset) { cell in
                                Text(cell.element).font(.system(size: compact ? 7.5 : 12)).lineLimit(2)
                            }
                        }
                    }
                }
            }
        }
    }
}
