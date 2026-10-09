import SwiftUI
import SolarPulseKit

/// Maintenance tickets grouped by status with swipe actions (src/pages/Maintenance.tsx).
struct MaintenanceView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var query = ""
    @State var priority: TicketPriority?
    @State var siteFilter: String?
    @State var showNew = false
    @State var deleting: Ticket?
    @State var moved = 0

    private var rows: [Ticket] {
        let q = query.lowercased()
        return store.db.tickets
            .filter { (priority == nil || $0.priority == priority) && (siteFilter == nil || $0.siteId == siteFilter) }
            .filter { t in q.isEmpty || [t.title, t.description, t.assignee, store.siteName(t.siteId)].contains { $0.lowercased().contains(q) } }
            .sorted { $0.priority.order != $1.priority.order ? $0.priority.order < $1.priority.order : $0.dueDate < $1.dueDate }
    }

    var body: some View {
        let today = store.sim.todayKey
        let week = store.sim.days.dayKey(store.sim.days.addDays(store.now, 7))
        let tickets = store.db.tickets
        let overdue = tickets.filter { $0.status != .resolved && $0.dueDate < today }.count
        let upcoming = tickets.filter { $0.status != .resolved && $0.dueDate >= today && $0.dueDate <= week }.count
        let list = rows

        List {
            Section {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                    StatTile(icon: "circle.dotted", tint: Brand.b500, title: l10n.t("mt.openCount"), value: "\(tickets.filter { $0.status == .open }.count)")
                    StatTile(icon: "arrow.triangle.2.circlepath", tint: Brand.violet, title: l10n.t("mt.inProgress"), value: "\(tickets.filter { $0.status == .inProgress }.count)")
                    StatTile(icon: "exclamationmark.octagon.fill", tint: Brand.danger, title: l10n.t("mt.overdue"), value: "\(overdue)")
                    StatTile(icon: "calendar.badge.clock", tint: Brand.green, title: l10n.t("mt.upcoming"), value: "\(upcoming)")
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                filters
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                    .listRowBackground(Color.clear)
            }

            if list.isEmpty {
                ContentUnavailableView(l10n.t("common.noData"), systemImage: "wrench.and.screwdriver", description: Text(l10n.t("mt.subtitle")))
                    .listRowBackground(Color.clear)
            }

            ForEach(TicketStatus.allCases) { status in
                let items = list.filter { $0.status == status }
                if !items.isEmpty {
                    Section {
                        ForEach(items) { t in
                            NavigationLink(value: AppRoute.ticket(t.id)) {
                                TicketRow(ticket: t, today: today)
                            }
                            .swipeActions(edge: .leading, allowsFullSwipe: true) {
                                if let next = nextStatus(t.status) {
                                    Button { Task { await move(t, to: next) } } label: {
                                        Label(l10n.status(next.rawValue), systemImage: next.icon)
                                    }
                                    .tint(next.color)
                                }
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) { deleting = t } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                            }
                            .contextMenu {
                                Menu {
                                    ForEach(TicketStatus.allCases.filter { $0 != t.status }) { s in
                                        Button { Task { await move(t, to: s) } } label: { Label(l10n.status(s.rawValue), systemImage: s.icon) }
                                    }
                                } label: {
                                    Label(l10n.t("mt.moveTo"), systemImage: "arrow.right.circle")
                                }
                                Button(role: .destructive) { deleting = t } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                            }
                        }
                    } header: {
                        HStack {
                            StatusBadge(text: l10n.status(status.rawValue), color: status.color)
                            Spacer()
                            Text("\(items.count)").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("mt.title"))
        .searchable(text: $query, prompt: Text(l10n.t("common.search")))
        .refreshable { await store.refresh() }
        .animation(.snappy, value: list.map { "\($0.id)\($0.status.rawValue)" })
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showNew = true } label: { Label(l10n.t("mt.add"), systemImage: "plus") }
            }
        }
        .sheet(isPresented: $showNew) {
            NavigationStack { TicketEditorView(ticketId: nil, isSheet: true) }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: moved)
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                if let t = deleting {
                    Task {
                        await store.delete(Ticket.self, id: t.id)
                        store.show(l10n.t("common.deleted"), tone: .info)
                    }
                }
            }
        }
    }

    private var priorityOptions: [(TicketPriority?, String)] {
        var out: [(TicketPriority?, String)] = [(nil, "\(l10n.t("mt.priority")): \(l10n.t("common.all"))")]
        for p in [TicketPriority.critical, .high, .medium, .low] { out.append((p, l10n.priority(p))) }
        return out
    }

    private var siteOptions: [(String?, String)] {
        var out: [(String?, String)] = [(nil, l10n.t("an.allSites"))]
        for s in store.db.sites { out.append((s.id, s.name)) }
        return out
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 8) {
            Menu {
                Picker(l10n.t("common.site"), selection: $siteFilter) {
                    ForEach(siteOptions, id: \.0) { o in Text(o.1).tag(o.0) }
                }
            } label: {
                Label(siteOptions.first { $0.0 == siteFilter }?.1 ?? l10n.t("an.allSites"), systemImage: "building.2")
                    .font(.footnote.weight(.medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Theme.card, in: Capsule())
            }
            ChipPicker(options: priorityOptions, selection: $priority)
        }
    }

    private func nextStatus(_ s: TicketStatus) -> TicketStatus? {
        switch s {
        case .open: return .inProgress
        case .inProgress: return .resolved
        case .resolved: return nil
        }
    }

    private func move(_ t: Ticket, to status: TicketStatus) async {
        guard t.status != status else { return }
        var next = t
        next.status = status
        await store.save(next)
        moved += 1
        store.show("\(t.title) → \(l10n.status(status.rawValue))", tone: .info)
    }
}

struct TicketRow: View {
    let ticket: Ticket
    let today: String
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let late = ticket.status != .resolved && ticket.dueDate < today
        VStack(alignment: .leading, spacing: 5) {
            HStack(alignment: .top) {
                Text(ticket.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                Spacer()
                StatusBadge(text: l10n.priority(ticket.priority), color: ticket.priority.color, showDot: false)
            }
            if !ticket.description.isEmpty {
                Text(ticket.description).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            HStack(spacing: 10) {
                Label(store.siteName(ticket.siteId), systemImage: "building.2").lineLimit(1)
                Spacer()
                Label(fmt.date(ticket.dueDate), systemImage: "calendar")
                    .foregroundStyle(late ? Brand.danger : Color.secondary)
                    .fontWeight(late ? .semibold : .regular)
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
            if !ticket.assignee.isEmpty {
                Label(ticket.assignee, systemImage: "person.fill").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }
}

/// Create / edit a ticket (pushed from lists and deep links, or presented as a sheet).
struct TicketEditorView: View {
    let ticketId: String?
    var isSheet = false

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State var form: Ticket?
    @State var showErrors = false
    @State var confirmDelete = false

    private var isNew: Bool { ticketId == nil || !store.db.tickets.contains { $0.id == ticketId } }

    private func blank() -> Ticket {
        Ticket(id: makeId("tkt-"), siteId: store.db.sites.first?.id ?? "", deviceId: nil, title: "", description: "",
               priority: .medium, status: .open, assignee: "",
               dueDate: store.sim.days.dayKey(store.sim.days.addDays(Date(), 3)), createdAt: ISODate.string(Date()))
    }

    var body: some View {
        Form {
            if let f = Binding($form) {
                Section {
                    TextField(l10n.t("mt.ticketTitle"), text: f.title)
                    if showErrors && f.wrappedValue.title.trimmingCharacters(in: .whitespaces).isEmpty {
                        Label(l10n.t("common.required"), systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(Brand.danger)
                    }
                    TextField(l10n.t("common.description"), text: f.description, axis: .vertical)
                        .lineLimit(3...8)
                }
                Section {
                    Picker(l10n.t("common.site"), selection: Binding(get: { f.wrappedValue.siteId }, set: { new in
                        f.wrappedValue.siteId = new
                        f.wrappedValue.deviceId = nil
                    })) {
                        ForEach(store.db.sites) { Text($0.name).tag($0.id) }
                    }
                    Picker(l10n.t("mt.device"), selection: f.deviceId) {
                        Text(l10n.t("common.none")).tag(String?.none)
                        ForEach(store.db.devices.filter { $0.siteId == f.wrappedValue.siteId }) { d in
                            Text(d.name).tag(String?.some(d.id))
                        }
                    }
                    Picker(l10n.t("mt.priority"), selection: f.priority) {
                        ForEach(TicketPriority.allCases) { Text(l10n.priority($0)).tag($0) }
                    }
                    Picker(l10n.t("common.status"), selection: f.status) {
                        ForEach(TicketStatus.allCases) { Text(l10n.status($0.rawValue)).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    TextField(l10n.t("mt.assignee"), text: f.assignee)
                    DatePicker(l10n.t("mt.due"), selection: Binding(
                        get: { store.sim.days.parseDay(f.wrappedValue.dueDate) },
                        set: { f.wrappedValue.dueDate = store.sim.days.dayKey($0) }), displayedComponents: .date)
                }
                if !isNew {
                    Section {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label(l10n.t("common.delete"), systemImage: "trash").frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
        .navigationTitle(l10n.t(isNew ? "mt.add" : "mt.edit"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isSheet {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("common.cancel")) { dismiss() } }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(l10n.t("common.save")) { Task { await save() } }.fontWeight(.semibold)
            }
        }
        .onAppear {
            if form == nil {
                form = store.db.tickets.first { $0.id == ticketId } ?? blank()
            }
        }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                guard let id = form?.id else { return }
                Task {
                    await store.delete(Ticket.self, id: id)
                    store.show(l10n.t("common.deleted"), tone: .info)
                    dismiss()
                }
            }
        }
    }

    private func save() async {
        guard var t = form else { return }
        t.title = t.title.trimmingCharacters(in: .whitespaces)
        guard !t.title.isEmpty, !t.siteId.isEmpty else {
            withAnimation { showErrors = true }
            return
        }
        let wasNew = !store.db.tickets.contains { $0.id == t.id }
        await store.save(t)
        if wasNew && store.settings.notifyMaintenance {
            await store.notify(title: l10n.t("mt.created"), body: "\(t.title) · \(store.siteName(t.siteId))",
                               kind: t.priority == .critical ? .danger : .info, link: "/maintenance?open=\(t.id)")
        }
        store.show(l10n.t(wasNew ? "mt.created" : "common.saved"))
        dismiss()
    }
}
