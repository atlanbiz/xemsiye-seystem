import SwiftUI
import SolarPulseKit

/// Devices — health / efficiency, restart and firmware actions, create ticket (src/pages/Devices.tsx).
struct DevicesView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var query = ""
    @State var siteFilter: String?
    @State var typeFilter: DeviceType?
    @State var statusFilter: DeviceStatus?
    @State var detail: Device?
    @State var editing: Device?
    @State var showForm = false
    @State var deleting: Device?
    @State var openTicket: String?

    private var rows: [Device] {
        let q = query.lowercased()
        return store.db.devices.filter { d in
            (typeFilter == nil || d.type == typeFilter) && (statusFilter == nil || d.status == statusFilter) &&
                (siteFilter == nil || d.siteId == siteFilter) &&
                (q.isEmpty || [d.name, d.model, d.serial].contains { $0.lowercased().contains(q) })
        }
    }

    var body: some View {
        let list = rows
        let devices = store.db.devices
        List {
            Section {
                HStack(spacing: 10) {
                    StatTile(icon: "checkmark.circle.fill", tint: Brand.green, title: l10n.t("status.online"), value: "\(devices.filter { $0.status == .online }.count)")
                    StatTile(icon: "exclamationmark.triangle.fill", tint: Brand.amber, title: l10n.t("status.warning"), value: "\(devices.filter { $0.status == .warning }.count)")
                    StatTile(icon: "xmark.circle.fill", tint: Brand.danger, title: l10n.t("status.offline"), value: "\(devices.filter { $0.status == .offline }.count)")
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                filters
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                    .listRowBackground(Color.clear)
            }
            Section {
                if list.isEmpty {
                    ContentUnavailableView(l10n.t("common.noData"), systemImage: "cpu", description: Text(l10n.t("dev.subtitle")))
                } else {
                    ForEach(list) { d in
                        Button { detail = d } label: { DeviceRow(device: d) }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) { deleting = d } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                                Button { editing = d; showForm = true } label: { Label(l10n.t("common.edit"), systemImage: "pencil") }
                                    .tint(Brand.b500)
                            }
                            .swipeActions(edge: .leading) {
                                Button { Task { await restart(d) } } label: { Label(l10n.t("dev.restart"), systemImage: "arrow.clockwise") }
                                    .tint(Brand.green)
                            }
                    }
                }
            } header: {
                Text("\(l10n.t("common.showing")) \(list.count) \(l10n.t("common.of")) \(devices.count)")
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("dev.title"))
        .searchable(text: $query, prompt: Text(l10n.t("common.search")))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = nil; showForm = true } label: { Label(l10n.t("dev.add"), systemImage: "plus") }
            }
        }
        .sheet(item: $detail) { d in
            DeviceDetailSheet(deviceId: d.id) { ticketId in
                detail = nil
                openTicket = ticketId
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showForm) { DeviceFormView(initial: editing) }
        .navigationDestination(item: $openTicket) { id in TicketEditorView(ticketId: id) }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                if let d = deleting {
                    Task {
                        await store.delete(Device.self, id: d.id)
                        store.show(l10n.t("common.deleted"), tone: .info)
                    }
                }
            }
        }
    }

    private var siteOptions: [(String?, String)] {
        var out: [(String?, String)] = [(nil, l10n.t("an.allSites"))]
        for s in store.db.sites { out.append((s.id, s.name)) }
        return out
    }

    private var typeOptions: [(DeviceType?, String)] {
        var out: [(DeviceType?, String)] = [(nil, "\(l10n.t("common.type")): \(l10n.t("common.all"))")]
        for t in DeviceType.allCases { out.append((t, l10n.deviceType(t))) }
        return out
    }

    private var statusOptions: [(DeviceStatus?, String)] {
        var out: [(DeviceStatus?, String)] = [(nil, "\(l10n.t("common.status")): \(l10n.t("common.all"))")]
        for s in DeviceStatus.allCases { out.append((s, l10n.status(s.rawValue))) }
        return out
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 8) {
            Menu {
                Picker(l10n.t("common.site"), selection: $siteFilter) {
                    ForEach(siteOptions, id: \.0) { option in Text(option.1).tag(option.0) }
                }
            } label: {
                Label(siteOptions.first { $0.0 == siteFilter }?.1 ?? l10n.t("an.allSites"), systemImage: "building.2")
                    .font(.footnote.weight(.medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Theme.card, in: Capsule())
            }
            ChipPicker(options: typeOptions, selection: $typeFilter)
            ChipPicker(options: statusOptions, selection: $statusFilter)
        }
    }

    private func restart(_ d: Device) async {
        var next = d
        next.status = .online
        next.lastSeen = ISODate.string(Date())
        next.health = max(d.health, 85)
        await store.save(next)
        store.show(l10n.t("dev.restarted"))
    }
}

struct DeviceRow: View {
    let device: Device
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        HStack(spacing: 12) {
            IconTile(systemName: device.type.icon, tint: Brand.b500, size: 38)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(device.name).font(.subheadline.weight(.semibold)).environment(\.layoutDirection, .leftToRight)
                    Spacer()
                    StatusBadge(text: l10n.status(device.status.rawValue), color: device.status.color)
                }
                Text("\(l10n.deviceType(device.type)) · \(store.siteName(device.siteId))")
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                HStack(spacing: 12) {
                    HealthBar(value: device.health, width: 60)
                    Label(fmt.pct(device.efficiency), systemImage: "bolt.horizontal")
                        .font(.caption).foregroundStyle(.secondary).monospacedDigit()
                    Spacer()
                    Text(fmt.ago(device.lastSeen, now: store.now)).font(.caption2).foregroundStyle(.tertiary)
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// Device detail with the web's actions.
struct DeviceDetailSheet: View {
    let deviceId: String
    var onTicketCreated: (String) -> Void

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @Environment(\.dismiss) private var dismiss
    @State var busy = false
    @State var updated = 0

    var body: some View {
        NavigationStack {
            if let d = store.db.devices.first(where: { $0.id == deviceId }) {
                List {
                    Section {
                        HStack(spacing: 14) {
                            IconTile(systemName: d.type.icon, tint: Brand.b500, size: 52)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(d.name).font(.title3.bold()).environment(\.layoutDirection, .leftToRight)
                                StatusBadge(text: l10n.status(d.status.rawValue), color: d.status.color)
                            }
                        }
                        .listRowBackground(Color.clear)
                    }
                    Section {
                        InfoRow(label: l10n.t("common.type"), value: l10n.deviceType(d.type))
                        InfoRow(label: l10n.t("common.site"), value: store.siteName(d.siteId))
                        InfoRow(label: l10n.t("dev.model"), value: d.model, ltrValue: true)
                        InfoRow(label: l10n.t("dev.serial"), value: d.serial, ltrValue: true)
                        InfoRow(label: l10n.t("dev.firmware"), value: d.firmware, ltrValue: true)
                        InfoRow(label: l10n.t("dev.efficiency"), value: fmt.pct(d.efficiency), ltrValue: true)
                        InfoRow(label: l10n.t("sites.installDate"), value: fmt.date(d.installedAt))
                        InfoRow(label: l10n.t("dev.lastSeen"), value: fmt.ago(d.lastSeen, now: store.now))
                        VStack(alignment: .leading, spacing: 6) {
                            Text(l10n.t("dev.health")).font(.subheadline).foregroundStyle(.secondary)
                            HealthBar(value: d.health, width: nil)
                        }
                    }
                    Section {
                        Button { Task { await restart(d) } } label: { Label(l10n.t("dev.restart"), systemImage: "arrow.clockwise.circle.fill") }
                        Button { Task { await updateFirmware(d) } } label: { Label(l10n.t("dev.updateFw"), systemImage: "arrow.down.circle.fill") }
                        Button { Task { await createTicket(d) } } label: { Label(l10n.t("dev.createTicket"), systemImage: "wrench.and.screwdriver.fill") }
                    }
                    .disabled(busy)
                }
                .navigationTitle(l10n.t("common.details"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button(l10n.t("common.done")) { dismiss() } }
                }
                .sensoryFeedback(.success, trigger: updated)
            } else {
                ContentUnavailableView(l10n.t("common.noData"), systemImage: "cpu")
            }
        }
    }

    private func restart(_ d: Device) async {
        busy = true
        defer { busy = false }
        var next = d
        next.status = .online
        next.lastSeen = ISODate.string(Date())
        next.health = max(d.health, 85)
        await store.save(next)
        updated += 1
        store.show(l10n.t("dev.restarted"))
    }

    private func updateFirmware(_ d: Device) async {
        busy = true
        defer { busy = false }
        let parts = d.firmware.replacingOccurrences(of: "v", with: "").split(separator: ".").map { Int($0) ?? 0 }
        let v = "v\(parts.first ?? 1).\((parts.count > 1 ? parts[1] : 0) + 1).0"
        var next = d
        next.firmware = v
        next.lastSeen = ISODate.string(Date())
        await store.save(next)
        updated += 1
        store.show(l10n.t("dev.fwUpdated", ["v": v]))
    }

    private func createTicket(_ d: Device) async {
        busy = true
        defer { busy = false }
        let id = makeId("tkt-")
        let ticket = Ticket(id: id, siteId: d.siteId, deviceId: d.id,
                            title: "\(d.name) — \(l10n.status(d.status.rawValue))",
                            description: "\(d.model) (\(d.serial))",
                            priority: d.status == .offline ? .high : .medium, status: .open, assignee: "",
                            dueDate: store.sim.days.dayKey(Date().addingTimeInterval(3 * 86_400)),
                            createdAt: ISODate.string(Date()))
        await store.save(ticket)
        if store.settings.notifyMaintenance {
            await store.notify(title: l10n.t("mt.created"), body: d.name, kind: .info, link: "/maintenance?open=\(id)")
        }
        store.show(l10n.t("mt.created"))
        onTicketCreated(id)
    }
}

/// Add / edit device.
struct DeviceFormView: View {
    let initial: Device?

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State var form: Device?
    @State var showErrors = false

    private func blank() -> Device {
        Device(id: makeId("dev-"), siteId: store.db.sites.first?.id ?? "", name: "", type: .inverter, model: "", serial: "",
               status: .online, health: 100, efficiency: 97, firmware: "v1.0.0",
               installedAt: store.sim.days.dayKey(Date()), lastSeen: ISODate.string(Date()))
    }

    var body: some View {
        NavigationStack {
            Form {
                if let binding = Binding($form) {
                    fields(binding)
                }
            }
            .navigationTitle(l10n.t(initial == nil ? "dev.add" : "dev.edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("common.cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("common.save")) { Task { await save() } }.fontWeight(.semibold)
                }
            }
            .onAppear { if form == nil { form = initial ?? blank() } }
        }
    }

    @ViewBuilder
    private func fields(_ f: Binding<Device>) -> some View {
        Section {
            TextField(l10n.t("common.name"), text: f.name)
            if showErrors && f.wrappedValue.name.trimmingCharacters(in: .whitespaces).isEmpty { required }
            Picker(l10n.t("common.site"), selection: f.siteId) {
                ForEach(store.db.sites) { Text($0.name).tag($0.id) }
            }
            Picker(l10n.t("common.type"), selection: f.type) {
                ForEach(DeviceType.allCases) { Text(l10n.deviceType($0)).tag($0) }
            }
            Picker(l10n.t("common.status"), selection: f.status) {
                ForEach(DeviceStatus.allCases) { Text(l10n.status($0.rawValue)).tag($0) }
            }
        }
        Section {
            TextField(l10n.t("dev.model"), text: f.model)
            if showErrors && f.wrappedValue.model.trimmingCharacters(in: .whitespaces).isEmpty { required }
            TextField(l10n.t("dev.serial"), text: f.serial)
            TextField(l10n.t("dev.firmware"), text: f.firmware)
        }
        Section {
            LabeledContent(l10n.t("dev.health") + " (%)") {
                TextField("", value: f.health, format: .plainNumber).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
            }
            LabeledContent(l10n.t("dev.efficiency") + " (%)") {
                TextField("", value: f.efficiency, format: .plainNumber).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
            }
            DatePicker(l10n.t("sites.installDate"), selection: Binding(
                get: { store.sim.days.parseDay(f.wrappedValue.installedAt) },
                set: { f.wrappedValue.installedAt = store.sim.days.dayKey($0) }), displayedComponents: .date)
        }
    }

    private var required: some View {
        Label(l10n.t("common.required"), systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(Brand.danger)
    }

    private func save() async {
        guard var d = form else { return }
        d.name = d.name.trimmingCharacters(in: .whitespaces)
        d.model = d.model.trimmingCharacters(in: .whitespaces)
        guard !d.name.isEmpty, !d.model.isEmpty, !d.siteId.isEmpty else {
            withAnimation { showErrors = true }
            return
        }
        d.health = min(100, max(0, d.health))
        d.efficiency = min(100, max(0, d.efficiency))
        let previous = store.db.devices.first { $0.id == d.id }
        await store.save(d)
        if d.status == .offline && previous?.status != .offline && store.settings.notifyDeviceAlerts {
            await store.notify(title: "\(d.name) \(l10n.status("offline"))", body: store.siteName(d.siteId), kind: .danger, link: "/devices")
        }
        store.show(l10n.t("common.saved"))
        dismiss()
    }
}
