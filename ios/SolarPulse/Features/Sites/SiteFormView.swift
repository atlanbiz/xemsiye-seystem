import SwiftUI
import SolarPulseKit

/// Number field style shared by all forms: Latin digits, no grouping.
extension FormatStyle where Self == FloatingPointFormatStyle<Double> {
    static var plainNumber: FloatingPointFormatStyle<Double> {
        FloatingPointFormatStyle<Double>(locale: Locale(identifier: "en_US_POSIX")).grouping(.never)
    }
}

/// Add / edit site (src/components/SiteForm.tsx, incl. ROI fields).
struct SiteFormView: View {
    let initial: Site?

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss

    @State var form: Site = SiteFormView.blank()
    @State var showErrors = false
    @State var loaded = false

    static func blank() -> Site {
        let days = Simulator.live.days
        return Site(id: makeId("site-"), name: "", location: "", type: .commercial, status: .active,
                    capacityKw: 50, batteryKwh: 0, pricePerKwh: 0.12, customer: "",
                    installDate: days.dayKey(Date()), lat: 43.82, lng: 87.61).withDefaults()
    }

    private var nameMissing: Bool { form.name.trimmingCharacters(in: .whitespaces).isEmpty }
    private var locationMissing: Bool { form.location.trimmingCharacters(in: .whitespaces).isEmpty }
    private var customerMissing: Bool { form.customer.trimmingCharacters(in: .whitespaces).isEmpty }
    private var capacityInvalid: Bool { !(form.capacityKw > 0) }
    private var isValid: Bool { !nameMissing && !locationMissing && !customerMissing && !capacityInvalid }

    private var installDate: Binding<Date> {
        Binding(get: { store.sim.days.parseDay(form.installDate) },
                set: { form.installDate = store.sim.days.dayKey($0) })
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(l10n.t("common.name"), text: $form.name)
                    if showErrors && nameMissing { requiredHint }
                    TextField(l10n.t("common.location"), text: $form.location)
                    if showErrors && locationMissing { requiredHint }
                    TextField(l10n.t("sites.customer"), text: $form.customer)
                    if showErrors && customerMissing { requiredHint }
                    Picker(l10n.t("common.type"), selection: $form.type) {
                        ForEach(SiteType.allCases) { Text(l10n.siteType($0)).tag($0) }
                    }
                    Picker(l10n.t("common.status"), selection: $form.status) {
                        ForEach(SiteStatus.allCases) { Text(l10n.status($0.rawValue)).tag($0) }
                    }
                }
                Section {
                    numberRow(l10n.t("sites.capacity"), value: $form.capacityKw)
                    if showErrors && capacityInvalid { requiredHint }
                    numberRow(l10n.t("sites.battery"), value: $form.batteryKwh)
                    numberRow(l10n.t("sites.price"), value: $form.pricePerKwh)
                    DatePicker(l10n.t("sites.installDate"), selection: installDate, displayedComponents: .date)
                }
                Section(l10n.t("common.location")) {
                    numberRow(l10n.t("sites.lat"), value: $form.lat)
                    numberRow(l10n.t("sites.lng"), value: $form.lng)
                }
                Section {
                    numberRow(l10n.t("sites.systemCost"), value: $form.systemCost)
                    numberRow(l10n.t("sites.annualOpex"), value: $form.annualOpex)
                    numberRow(l10n.t("sites.degradation"), value: $form.degradationPct)
                    numberRow(l10n.t("sites.escalation"), value: $form.tariffEscalationPct)
                    Button(l10n.t("sites.useDefaults")) {
                        let d = Site.financeDefaults(capacityKw: form.capacityKw)
                        withAnimation {
                            form.systemCost = d.systemCost
                            form.annualOpex = d.annualOpex
                            form.degradationPct = 0.5
                            form.tariffEscalationPct = 2
                        }
                    }
                } header: {
                    Text(l10n.t("sites.financials"))
                }
            }
            .navigationTitle(l10n.t(initial == nil ? "sites.add" : "sites.edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(l10n.t("common.cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(l10n.t("common.save")) { Task { await save() } }
                        .fontWeight(.semibold)
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                if let initial { form = initial }
            }
            .sensoryFeedback(.error, trigger: showErrors) { _, new in new }
        }
    }

    private var requiredHint: some View {
        Label(l10n.t("common.required"), systemImage: "exclamationmark.circle")
            .font(.caption)
            .foregroundStyle(Brand.danger)
    }

    private func numberRow(_ title: String, value: Binding<Double>) -> some View {
        HStack {
            Text(title)
            Spacer()
            TextField(title, value: value, format: .plainNumber)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 140)
                .environment(\.layoutDirection, .leftToRight)
        }
    }

    private func save() async {
        guard isValid else {
            withAnimation { showErrors = true }
            return
        }
        var site = form
        site.name = site.name.trimmingCharacters(in: .whitespaces)
        site.location = site.location.trimmingCharacters(in: .whitespaces)
        site.customer = site.customer.trimmingCharacters(in: .whitespaces)
        site = site.withDefaults()
        let isNew = !store.db.sites.contains { $0.id == site.id }
        await store.save(site)
        if isNew {
            await store.notify(title: l10n.t("sites.add"), body: site.name, kind: .success, link: "/sites/\(site.id)")
        }
        store.show(l10n.t("common.saved"))
        dismiss()
    }
}
