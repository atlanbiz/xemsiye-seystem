import SwiftUI
import SolarPulseKit

/// Notification centre (the `notifications` table — in Supabase mode rows come from the server/Edge Function).
struct NotificationsView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @State var confirmClear = false

    var body: some View {
        let items = store.db.notifications.sorted { $0.createdAt > $1.createdAt }
        List {
            if items.isEmpty {
                ContentUnavailableView(l10n.t("ntf.empty"), systemImage: "checkmark.seal")
                    .listRowBackground(Color.clear)
            }
            ForEach(items) { n in
                Group {
                    if let route = DeepLink.route(for: n.link) {
                        NavigationLink(value: route) { NotificationRow(notification: n) }
                            .simultaneousGesture(TapGesture().onEnded { Task { await markRead(n) } })
                    } else {
                        NotificationRow(notification: n)
                            .contentShape(Rectangle())
                            .onTapGesture { Task { await markRead(n) } }
                    }
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        Task { await store.delete(AppNotification.self, id: n.id) }
                    } label: { Label(l10n.t("common.delete"), systemImage: "trash") }
                }
                .swipeActions(edge: .leading) {
                    if !n.read {
                        Button { Task { await markRead(n) } } label: { Label(l10n.t("ntf.markAll"), systemImage: "envelope.open") }
                            .tint(Brand.b500)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .screenBackground()
        .navigationTitle(l10n.t("ntf.title"))
        .refreshable { await store.refresh() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { Task { await store.markAllRead() } } label: { Label(l10n.t("ntf.markAll"), systemImage: "envelope.open") }
                    Button(role: .destructive) { confirmClear = true } label: { Label(l10n.t("ntf.clear"), systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel(Text(l10n.t("common.actions")))
                .disabled(items.isEmpty)
            }
        }
        .confirmationDialog(l10n.t("common.deleteConfirm"), isPresented: $confirmClear, titleVisibility: .visible) {
            Button(l10n.t("ntf.clear"), role: .destructive) { Task { await store.clearNotifications() } }
        }
    }

    private func markRead(_ n: AppNotification) async {
        guard !n.read else { return }
        var next = n
        next.read = true
        await store.save(next, haptic: false)
    }
}
