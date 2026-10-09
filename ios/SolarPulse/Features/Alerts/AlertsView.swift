import SwiftUI
import UserNotifications
import SolarPulseKit

/// Alert rules CRUD + triggered alerts (PLATFORM.md §3.3, web src/pages/Alerts.tsx).
struct AlertsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var editing: AlertRule?
    @State var showNew = false
    @State var checking = false
    @State var checks = 0
    @State var dismissedPrompt = false

    var body: some View {
        let rules = store.db.alertRules
        let evaluator = AlertEvaluator(sim: store.sim)
        let breaching = rules.filter { $0.enabled && !evaluator.matches($0, sites: store.db.sites, devices: store.db.devices, invoices: store.db.invoices, now: store.now).isEmpty }.count
        let dayAgo = store.now.addingTimeInterval(-86_400)
        let ruleNames = Set(rules.map(\.name))
        let recent = store.db.notifications.filter { ruleNames.contains($0.title) || $0.kind == .warning || $0.kind == .danger }
        let firedToday = recent.filter { (ISODate.parse($0.createdAt) ?? .distantPast) > dayAgo }.count

        List {
            if store.isDemo && store.notificationStatus == .notDetermined && !dismissedPrompt {
                Section { permissionCard }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }

            Section {
                HStack(spacing: 10) {
                    StatTile(icon: "checklist", tint: Brand.b500, title: l10n.t("alerts.activeRules"), value: "\(rules.filter(\.enabled).count)")
                    StatTile(icon: "exclamationmark.triangle.fill", tint: Brand.danger, title: l10n.t("alerts.firing"), value: "\(breaching)")
                    StatTile(icon: "bell.fill", tint: Brand.amber, title: l10n.t("alerts.firedToday"), value: "\(firedToday)")
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                Label(l10n.t(store.isDemo ? "alerts.mobileDemoHint" : "alerts.serverHint"), systemImage: "info.circle")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 4, bottom: 4, trailing: 4))
            }

            Section {
                if rules.isEmpty {
                    ContentUnavailableView(l10n.t("alerts.empty"), systemImage: "bell.slash")
                }
                ForEach(rules) { rule in
                    let matching = evaluator.matches(rule, sites: store.db.sites, devices: store.db.devices, invoices: store.db.invoices, now: store.now).count
                    RuleRow(rule: rule, matching: matching) { enabled in
                        var next = rule
                        next.enabled = enabled
                        let updated = next
                        Task { await store.save(updated) }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { editing = rule }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            Task { await store.delete(AlertRule.self, id: rule.id) }
                        } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                        Button { editing = rule } label: { Label(l10n.t("common.edit"), systemImage: "pencil") }
                            .tint(Brand.b500)
                    }
                }
            } header: {
                HStack {
                    Text(l10n.t("alerts.rules"))
                    Spacer()
                    if store.isDemo {
                        Button {
                            Task { await checkNow() }
                        } label: {
                            if checking { ProgressView() } else { Label(l10n.t("alerts.checkNow"), systemImage: "arrow.clockwise") }
                        }
                        .font(.caption)
                        .textCase(nil)
                        .disabled(checking)
                    }
                }
            }

            Section(l10n.t("alerts.recent")) {
                if recent.isEmpty {
                    Text(l10n.t("alerts.recentEmpty")).font(.footnote).foregroundStyle(.secondary)
                } else {
                    ForEach(recent.prefix(20)) { n in
                        NotificationRow(notification: n)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("alerts.title"))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showNew = true } label: { Label(l10n.t("alerts.add"), systemImage: "plus") }
            }
        }
        .sheet(isPresented: $showNew) { RuleEditor(initial: nil) }
        .sheet(item: $editing) { rule in RuleEditor(initial: rule) }
        .sensoryFeedback(.success, trigger: checks)
    }

    private var permissionCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                IconTile(systemName: "bell.badge.fill", tint: .white, size: 40)
                    .background(Color.white.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                Text(l10n.t("alerts.enableNotifs")).font(.headline).foregroundStyle(.white)
            }
            Text(l10n.t("alerts.enableNotifsBody")).font(.footnote).foregroundStyle(.white.opacity(0.9))
            HStack {
                Button {
                    Task {
                        let granted = await store.requestNotificationPermission()
                        if granted { store.show(l10n.t("set.permission.allowed")) }
                    }
                } label: {
                    Text(l10n.t("alerts.enableNotifs")).fontWeight(.semibold)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.white, in: Capsule())
                        .foregroundStyle(Brand.b700)
                }
                .buttonStyle(.plain)
                Button(l10n.t("alerts.notNow")) { withAnimation { dismissedPrompt = true } }
                    .buttonStyle(.plain)
                    .foregroundStyle(.white.opacity(0.85))
                    .font(.footnote)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.heroGradient, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func checkNow() async {
        checking = true
        defer { checking = false }
        let n = await store.evaluateAlertsIfDemo(force: true)
        checks += 1
        store.show(l10n.t("alerts.checked", ["n": "\(n)"]), tone: n > 0 ? .warning : .success)
    }
}

private struct RuleRow: View {
    let rule: AlertRule
    let matching: Int
    let onToggle: (Bool) -> Void

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            IconTile(systemName: rule.severity == .danger ? "exclamationmark.octagon.fill" : "exclamationmark.triangle.fill",
                     tint: rule.severity == .danger ? Brand.danger : Brand.amber, size: 36)
            VStack(alignment: .leading, spacing: 4) {
                Text(rule.name).font(.subheadline.weight(.semibold))
                Text(condition).font(.caption).foregroundStyle(.secondary)
                Text(rule.siteId.map { store.siteName($0) } ?? l10n.t("alerts.allSites"))
                    .font(.caption2).foregroundStyle(.tertiary)
                HStack(spacing: 8) {
                    if rule.enabled && matching > 0 {
                        StatusBadge(text: l10n.t("alerts.matching", ["n": "\(matching)"]), color: Brand.danger)
                    }
                    if AlertEvaluator.isCoolingDown(rule, now: store.now), let last = rule.lastTriggeredAt, let d = ISODate.parse(last) {
                        Text(l10n.t("alerts.cooldown", ["t": fmt.time(d.addingTimeInterval(AlertEvaluator.cooldown))]))
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                Text("\(l10n.t("alerts.lastTriggered")): \(rule.lastTriggeredAt.map { fmt.ago($0, now: store.now) } ?? l10n.t("alerts.never"))")
                    .font(.caption2).foregroundStyle(.tertiary)
            }
            Spacer()
            Toggle(l10n.t("alerts.enabled"), isOn: Binding(get: { rule.enabled }, set: onToggle))
                .labelsHidden()
        }
        .padding(.vertical, 4)
    }

    private var condition: String {
        let base = l10n.metric(rule.metric)
        guard rule.metric.usesThreshold else { return base }
        let unit = l10n.metricUnit(rule.metric)
        return "\(base) \(fmt.num(rule.threshold, 2))\(unit == "%" ? "" : " ")\(unit)"
    }
}

/// Create / edit a rule.
struct RuleEditor: View {
    let initial: AlertRule?

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State var form: AlertRule?
    @State var showErrors = false

    var body: some View {
        NavigationStack {
            Form {
                if let f = Binding($form) {
                    Section {
                        TextField(l10n.t("alerts.name"), text: f.name)
                        if showErrors && f.wrappedValue.name.trimmingCharacters(in: .whitespaces).isEmpty {
                            Label(l10n.t("common.required"), systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(Brand.danger)
                        }
                    }
                    Section {
                        Picker(l10n.t("alerts.metric"), selection: Binding(get: { f.wrappedValue.metric }, set: { m in
                            if m != f.wrappedValue.metric { f.wrappedValue.threshold = m.defaultThreshold }
                            f.wrappedValue.metric = m
                        })) {
                            ForEach(AlertMetric.allCases) { Text(l10n.metric($0)).tag($0) }
                        }
                        Text(l10n.metricHelp(f.wrappedValue.metric)).font(.caption).foregroundStyle(.secondary)
                        if f.wrappedValue.metric.usesThreshold {
                            LabeledContent(l10n.t("alerts.threshold") + " (" + l10n.metricUnit(f.wrappedValue.metric) + ")") {
                                TextField("", value: f.threshold, format: .plainNumber)
                                    .keyboardType(.decimalPad)
                                    .multilineTextAlignment(.trailing)
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                        } else {
                            Text(l10n.t("alerts.noThreshold")).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    Section {
                        Picker(l10n.t("alerts.scope"), selection: f.siteId) {
                            Text(l10n.t("alerts.allSites")).tag(String?.none)
                            ForEach(store.db.sites) { s in Text(s.name).tag(String?.some(s.id)) }
                        }
                        Picker(l10n.t("alerts.severity"), selection: f.severity) {
                            ForEach(AlertSeverity.allCases) { Text(l10n.severity($0)).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Toggle(l10n.t("alerts.enabled"), isOn: f.enabled)
                    }
                    if let last = f.wrappedValue.lastTriggeredAt {
                        Section {
                            InfoRow(label: l10n.t("alerts.lastTriggered"), value: store.fmt.ago(last, now: store.now))
                        }
                    }
                }
            }
            .navigationTitle(l10n.t(initial == nil ? "alerts.add" : "alerts.edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("common.cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("common.save")) { Task { await save() } }.fontWeight(.semibold)
                }
            }
            .onAppear {
                if form == nil {
                    form = initial ?? AlertRule(id: makeId("rule-"), name: "", metric: .siteOffline, threshold: 0, siteId: nil,
                                                severity: .warning, enabled: true, lastTriggeredAt: nil, createdAt: ISODate.string(Date()))
                }
            }
        }
    }

    private func save() async {
        guard var r = form else { return }
        r.name = r.name.trimmingCharacters(in: .whitespaces)
        guard !r.name.isEmpty else {
            withAnimation { showErrors = true }
            return
        }
        if !r.metric.usesThreshold { r.threshold = 0 }
        await store.save(r)
        store.show(l10n.t("common.saved"))
        dismiss()
        // A newly enabled rule is evaluated right away in demo mode.
        await store.evaluateAlertsIfDemo(force: true)
    }
}

/// Notification row shared by Alerts and the notification centre.
struct NotificationRow: View {
    let notification: AppNotification
    @Environment(AppStore.self) private var store
    @Environment(\.fmt) private var fmt

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: notification.kind.icon)
                .foregroundStyle(notification.kind.color)
                .font(.title3)
                .frame(width: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(notification.title).font(.subheadline.weight(notification.read ? .regular : .semibold)).lineLimit(2)
                    if !notification.read { Circle().fill(Brand.b500).frame(width: 7, height: 7) }
                }
                Text(notification.body).font(.caption).foregroundStyle(.secondary).lineLimit(3)
                Text(fmt.ago(notification.createdAt, now: store.now)).font(.caption2).foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}
