import SwiftUI
import UIKit
import SolarPulseKit

/// Vendor integrations (PLATFORM.md §1 + §3.4). Secrets go only through `rpc('set_integration_secret')`.
struct IntegrationsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var editing: Integration?
    @State var showNew = false
    @State var deleting: Integration?

    var body: some View {
        List {
            Section {
                Text(l10n.t("int.subtitle")).font(.footnote).foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            }
            if store.db.integrations.isEmpty {
                ContentUnavailableView {
                    Label(l10n.t("int.empty"), systemImage: "point.3.connected.trianglepath.dotted")
                } actions: {
                    Button(l10n.t("int.add")) { showNew = true }.buttonStyle(.borderedProminent)
                }
                .listRowBackground(Color.clear)
            }
            ForEach(store.db.integrations) { x in
                Section {
                    HStack(spacing: 12) {
                        IconTile(systemName: x.vendor.icon, tint: Brand.b500, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(x.name).font(.subheadline.weight(.semibold))
                            Text([l10n.vendor(x.vendor), store.siteName(x.siteId), x.externalId].filter { !$0.isEmpty }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }
                        Spacer()
                        StatusBadge(text: l10n.integrationStatus(x.status), color: x.status.color)
                    }
                    if let sync = x.lastSyncAt {
                        InfoRow(label: l10n.t("int.lastSync"), value: fmt.ago(sync, now: store.now))
                    }
                    if let err = x.lastError, !err.isEmpty {
                        Label(err, systemImage: "exclamationmark.triangle.fill").font(.caption).foregroundStyle(Brand.danger)
                    }
                    if x.vendor == .webhook {
                        CopyRow(label: l10n.t("int.ingestUrl"), value: AppConfig.ingestURL) { store.show(l10n.t("common.copied")) }
                        CopyRow(label: l10n.t("int.token"), value: x.ingestToken) { store.show(l10n.t("common.copied")) }
                    }
                    HStack {
                        Button { editing = x } label: { Label(l10n.t("common.edit"), systemImage: "pencil") }
                        Spacer()
                        Button(role: .destructive) { deleting = x } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                    }
                    .buttonStyle(.borderless)
                    .font(.footnote)
                }
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("int.title"))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showNew = true } label: { Label(l10n.t("int.add"), systemImage: "plus") }
            }
        }
        .sheet(isPresented: $showNew) { IntegrationEditor(initial: nil) }
        .sheet(item: $editing) { x in IntegrationEditor(initial: x) }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(l10n.t("common.delete"), role: .destructive) {
                if let x = deleting {
                    Task {
                        await store.delete(Integration.self, id: x.id)
                        store.show(l10n.t("common.deleted"), tone: .info)
                    }
                }
            }
        }
    }
}

/// Add / edit an integration.
struct IntegrationEditor: View {
    let initial: Integration?

    static let fusionSolarDefaultBase = "https://eu5.fusionsolar.huawei.com"

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State var form: Integration?
    @State var secret = ""
    @State var showErrors = false
    @State var saving = false

    private var isNew: Bool { initial == nil }

    /// 32 lowercase hex chars (PLATFORM.md §1).
    static func newToken() -> String { UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased() }

    private func secretKey(_ vendor: IntegrationVendor) -> String? {
        switch vendor {
        case .solaredge: return "api_key"
        case .fusionsolar: return "system_code"
        case .webhook: return nil
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                if let f = Binding($form) {
                    Section(l10n.t("int.vendor")) {
                        Picker(l10n.t("int.vendor"), selection: f.vendor) {
                            ForEach(IntegrationVendor.allCases) { v in Label(l10n.vendor(v), systemImage: v.icon).tag(v) }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                        .disabled(!isNew)
                    }
                    Section {
                        TextField(l10n.t("int.name"), text: f.name)
                        if showErrors && f.wrappedValue.name.trimmingCharacters(in: .whitespaces).isEmpty { required }
                        Picker(l10n.t("common.site"), selection: f.siteId) {
                            ForEach(store.db.sites) { Text($0.name).tag($0.id) }
                        }
                    }
                    vendorFields(f)
                }
            }
            .navigationTitle(l10n.t(isNew ? "int.add" : "int.edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("common.cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("common.save")) { Task { await save() } }.fontWeight(.semibold).disabled(saving)
                }
            }
            .onAppear {
                if form == nil {
                    form = initial ?? Integration(id: makeId("int-"), vendor: .solaredge, name: "", siteId: store.db.sites.first?.id ?? "",
                                                  externalId: "", config: [:], ingestToken: IntegrationEditor.newToken(), status: .pending,
                                                  lastSyncAt: nil, lastError: nil, createdAt: ISODate.string(Date()))
                }
            }
        }
    }

    @ViewBuilder
    private func vendorFields(_ f: Binding<Integration>) -> some View {
        switch f.wrappedValue.vendor {
        case .solaredge:
            Section {
                ltrField(l10n.t("int.seSiteId"), text: f.externalId, placeholder: "1234567", keyboard: .numberPad)
                if showErrors && f.wrappedValue.externalId.trimmingCharacters(in: .whitespaces).isEmpty { required }
                SecureField(isNew ? l10n.t("int.apiKey") : l10n.t("int.secretKeep"), text: $secret)
                    .environment(\.layoutDirection, .leftToRight)
                if showErrors && isNew && store.isDemo == false && secret.isEmpty { required }
            } footer: { secretFooter }
        case .fusionsolar:
            Section {
                ltrField(l10n.t("int.fsBaseUrl"), text: config(f, "base_url"), placeholder: IntegrationEditor.fusionSolarDefaultBase, keyboard: .URL)
                ltrField(l10n.t("int.fsUser"), text: config(f, "username"), placeholder: "", keyboard: .default)
                if showErrors && (f.wrappedValue.config["username"] ?? "").trimmingCharacters(in: .whitespaces).isEmpty { required }
                SecureField(isNew ? l10n.t("int.fsSystemCode") : l10n.t("int.secretKeep"), text: $secret)
                    .environment(\.layoutDirection, .leftToRight)
                if showErrors && isNew && store.isDemo == false && secret.isEmpty { required }
                ltrField(l10n.t("int.fsStation"), text: f.externalId, placeholder: "NE=12345678", keyboard: .default)
                if showErrors && f.wrappedValue.externalId.trimmingCharacters(in: .whitespaces).isEmpty { required }
            } footer: { secretFooter }
        case .webhook:
            Section {
                Text(l10n.t("int.webhookHint")).font(.footnote).foregroundStyle(.secondary)
                CopyRow(label: l10n.t("int.ingestUrl"), value: AppConfig.ingestURL) { store.show(l10n.t("common.copied")) }
                CopyRow(label: l10n.t("int.token"), value: f.wrappedValue.ingestToken) { store.show(l10n.t("common.copied")) }
                VStack(alignment: .leading, spacing: 4) {
                    Text(l10n.t("int.example")).font(.caption).foregroundStyle(.secondary)
                    Text(verbatim: """
                    curl -X POST '\(AppConfig.ingestURL)' \\
                      -H 'x-ingest-token: \(f.wrappedValue.ingestToken)' \\
                      -H 'content-type: application/json' \\
                      -d '{"powerKw": 42.5, "energyKwh": 180.2}'
                    """)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(Color(hex: 0xE2E8F0))
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0x0F172A), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .textSelection(.enabled)
                    .environment(\.layoutDirection, .leftToRight)
                }
            }
        }
    }

    private var secretFooter: some View {
        Label(l10n.t(store.isDemo ? "int.demoNote" : "int.secretNote"), systemImage: "key.fill")
    }

    private var required: some View {
        Label(l10n.t("common.required"), systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(Brand.danger)
    }

    private func config(_ f: Binding<Integration>, _ key: String) -> Binding<String> {
        Binding(get: { f.wrappedValue.config[key] ?? "" }, set: { f.wrappedValue.config[key] = $0 })
    }

    private func ltrField(_ title: String, text: Binding<String>, placeholder: String, keyboard: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .environment(\.layoutDirection, .leftToRight)
        }
    }

    private func save() async {
        guard var x = form else { return }
        x.name = x.name.trimmingCharacters(in: .whitespaces)
        x.externalId = x.vendor == .webhook ? "" : x.externalId.trimmingCharacters(in: .whitespaces)
        let key = secretKey(x.vendor)
        let trimmedSecret = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        var invalid = x.name.isEmpty || x.siteId.isEmpty
        if x.vendor != .webhook && x.externalId.isEmpty { invalid = true }
        if x.vendor == .fusionsolar && (x.config["username"] ?? "").trimmingCharacters(in: .whitespaces).isEmpty { invalid = true }
        if !store.isDemo && isNew && key != nil && trimmedSecret.isEmpty { invalid = true }
        guard !invalid else {
            withAnimation { showErrors = true }
            return
        }
        switch x.vendor {
        case .fusionsolar:
            var base = (x.config["base_url"] ?? "").trimmingCharacters(in: .whitespaces)
            if base.isEmpty { base = IntegrationEditor.fusionSolarDefaultBase }
            while base.hasSuffix("/") { base.removeLast() }
            x.config = ["base_url": base, "username": (x.config["username"] ?? "").trimmingCharacters(in: .whitespaces)]
        case .solaredge, .webhook:
            x.config = [:]
        }
        if x.ingestToken.isEmpty { x.ingestToken = IntegrationEditor.newToken() }
        if !trimmedSecret.isEmpty {
            x.status = .pending
            x.lastError = nil
        }
        saving = true
        defer { saving = false }
        await store.save(x)
        if let key, !trimmedSecret.isEmpty {
            do {
                try await store.repo.setIntegrationSecret(integrationId: x.id, secret: [key: trimmedSecret])
            } catch {
                store.show(l10n.t("int.secretFailed", ["e": error.localizedDescription]), tone: .error)
                dismiss()
                return
            }
        }
        store.show(l10n.t("int.saved"))
        dismiss()
    }
}
