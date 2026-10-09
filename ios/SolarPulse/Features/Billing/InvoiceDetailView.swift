import SwiftUI
import SolarPulseKit

/// Invoice detail with paper-style document, mark-paid and PDF export / share.
struct InvoiceDetailView: View {
    let invoiceId: String

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt
    @State var pdfURL: URL?
    @State var paid = 0

    var body: some View {
        if let inv = store.db.invoices.first(where: { $0.id == invoiceId }) {
            let site = store.site(inv.siteId)
            ScrollView {
                VStack(spacing: 16) {
                    InvoiceDocument(invoice: inv, site: site, settings: store.settings, l10n: l10n, fmt: fmt)
                        .padding(20)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .environment(\.colorScheme, .light)
                        .shadow(color: .black.opacity(0.12), radius: 18, y: 8)

                    if inv.status != .paid {
                        Button {
                            Task {
                                await BillingActions.markPaid(inv, store: store, l10n: l10n)
                                paid += 1
                            }
                        } label: {
                            Label(l10n.t("bill.markPaid"), systemImage: "checkmark.circle.fill")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .foregroundStyle(.white)
                                .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
                .frame(maxWidth: 700)
                .frame(maxWidth: .infinity)
            }
            .screenBackground()
            .navigationTitle(inv.number)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    if let pdfURL {
                        ShareLink(item: pdfURL) {
                            Label(l10n.t("common.downloadPdf"), systemImage: "square.and.arrow.up")
                        }
                    } else {
                        ProgressView()
                    }
                }
            }
            .sensoryFeedback(.success, trigger: paid)
            .task(id: "\(inv.id)|\(inv.status.rawValue)|\(l10n.lang.rawValue)|\(fmt.currency.rawValue)") {
                pdfURL = PDFExporter.render(
                    InvoiceDocument(invoice: inv, site: site, settings: store.settings, l10n: l10n, fmt: fmt).padding(36),
                    fileName: "\(inv.number).pdf", l10n: l10n, fmt: fmt)
                if pdfURL == nil { store.show(l10n.t("common.pdfFailed"), tone: .error) }
            }
        } else {
            ContentUnavailableView(l10n.t("common.noData"), systemImage: "doc.text.magnifyingglass")
        }
    }
}

/// The printable invoice (on screen and in the PDF). Takes everything explicitly so it renders the same in `ImageRenderer`.
struct InvoiceDocument: View {
    let invoice: Invoice
    let site: Site?
    let settings: AppSettings
    let l10n: L10n
    let fmt: Fmt

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    BrandMark(size: 30)
                    Text(settings.company).font(.caption).foregroundStyle(.secondary)
                    Text(settings.email).font(.caption).foregroundStyle(.secondary).environment(\.layoutDirection, .leftToRight)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    Text(invoice.number).font(.title3.bold()).environment(\.layoutDirection, .leftToRight)
                    StatusBadge(text: l10n.status(invoice.status.rawValue), color: invoice.status.color)
                }
            }

            HStack(alignment: .top, spacing: 12) {
                block(l10n.t("bill.billTo")) {
                    Text(invoice.customer).font(.subheadline.weight(.semibold))
                    Text(site?.name ?? "—").font(.caption).foregroundStyle(.secondary)
                }
                block(l10n.t("bill.issued")) {
                    Text(fmt.date(invoice.issuedAt)).font(.subheadline.weight(.medium))
                    Text(l10n.t("bill.due")).font(.caption).foregroundStyle(.secondary).padding(.top, 4)
                    Text(fmt.date(invoice.dueAt)).font(.subheadline.weight(.medium))
                }
                block(l10n.t("bill.period")) {
                    Text(fmt.period(invoice.period)).font(.subheadline.weight(.medium))
                    if let paidAt = invoice.paidAt {
                        Text(l10n.t("bill.paidOn")).font(.caption).foregroundStyle(.secondary).padding(.top, 4)
                        Text(fmt.date(paidAt)).font(.subheadline.weight(.medium))
                    }
                }
            }
            .padding(12)
            .background(Color(hex: 0xF8FAFC), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(spacing: 0) {
                HStack {
                    Text(l10n.t("common.description")).frame(maxWidth: .infinity, alignment: .leading)
                    Text(l10n.t("bill.energy")).frame(width: 100, alignment: .trailing)
                    Text(l10n.t("bill.rate")).frame(width: 70, alignment: .trailing)
                    Text(l10n.t("bill.amount")).frame(width: 100, alignment: .trailing)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.vertical, 8)
                Divider()
                HStack(alignment: .top) {
                    Text("\(l10n.t("rep.energy")) — \(site?.name ?? "")").frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(fmt.num(invoice.energyKwh)) kWh").frame(width: 100, alignment: .trailing)
                    Text(fmt.money2(invoice.rate)).frame(width: 70, alignment: .trailing)
                    Text(fmt.money2(invoice.amount)).fontWeight(.semibold).frame(width: 100, alignment: .trailing)
                }
                .font(.footnote)
                .monospacedDigit()
                .padding(.vertical, 10)
                Divider()
            }

            HStack {
                Spacer()
                HStack {
                    Text(l10n.t("common.total")).font(.headline)
                    Spacer()
                    Text(fmt.money2(invoice.amount)).font(.headline).monospacedDigit()
                }
                .padding(12)
                .frame(width: 240)
                .background(Brand.b50, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            Text(l10n.t("bill.thankYou"))
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
        }
        .foregroundStyle(Color(hex: 0x0F172A))
    }

    private func block<C: View>(_ title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
