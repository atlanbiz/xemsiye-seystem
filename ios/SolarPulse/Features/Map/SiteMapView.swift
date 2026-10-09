import SwiftUI
import MapKit
import SolarPulseKit

/// Fleet map: status-coloured pins sized by capacity; tap → card → detail (PLATFORM.md §3.1).
struct SiteMapView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    @State var selectedId: String?
    @State var hidden: Set<SiteStatus> = []
    @State var position: MapCameraPosition = .automatic

    private var visibleSites: [Site] { store.db.sites.filter { !hidden.contains($0.status) } }

    var body: some View {
        Map(position: $position) {
            ForEach(visibleSites) { site in
                Annotation(site.name, coordinate: CLLocationCoordinate2D(latitude: site.lat, longitude: site.lng), anchor: .center) {
                    SitePin(site: site, selected: site.id == selectedId)
                        .onTapGesture {
                            withAnimation(.spring(duration: 0.35)) { selectedId = site.id }
                        }
                        .accessibilityLabel(Text("\(site.name), \(l10n.status(site.status.rawValue))"))
                        .accessibilityAddTraits(.isButton)
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(elevation: .realistic, pointsOfInterest: .excludingAll))
        .mapControls {
            MapCompass()
            MapScaleView()
        }
        .safeAreaInset(edge: .top) { legend }
        .safeAreaInset(edge: .bottom) {
            if let id = selectedId, let site = store.site(id) {
                SiteMapCard(site: site) { withAnimation { selectedId = nil } }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .navigationTitle(l10n.t("map.title"))
        .navigationBarTitleDisplayMode(.inline)
        .sensoryFeedback(.selection, trigger: selectedId)
    }

    private var legend: some View {
        VStack(spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(SiteStatus.allCases) { s in
                        let off = hidden.contains(s)
                        let count = store.db.sites.filter { $0.status == s }.count
                        Button {
                            withAnimation(.snappy) {
                                if off { hidden.remove(s) } else { hidden.insert(s) }
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Circle().fill(s.color).frame(width: 9, height: 9)
                                Text("\(l10n.status(s.rawValue)) \(count)")
                            }
                            .font(.caption.weight(.medium))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(.regularMaterial, in: Capsule())
                            .opacity(off ? 0.45 : 1)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(Text(l10n.t("map.filterHint")))
                    }
                }
                .padding(.horizontal, 12)
            }
            Text(l10n.t("map.size"))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(.ultraThinMaterial, in: Capsule())
        }
        .padding(.top, 6)
    }
}

/// Map pin: colour = status, diameter grows with capacity.
struct SitePin: View {
    let site: Site
    var selected = false

    static func diameter(_ capacityKw: Double) -> CGFloat {
        CGFloat(min(48, max(18, 14 + sqrt(max(capacityKw, 0)) * 1.2)))
    }

    var body: some View {
        let d = SitePin.diameter(site.capacityKw)
        ZStack {
            Circle()
                .fill(site.status.color.opacity(0.25))
                .frame(width: d + 12, height: d + 12)
            Circle()
                .fill(site.status.color.gradient)
                .frame(width: d, height: d)
                .overlay(Circle().strokeBorder(.white, lineWidth: 2.5))
                .shadow(color: .black.opacity(0.25), radius: 4, y: 2)
            Image(systemName: "bolt.fill")
                .font(.system(size: d * 0.42, weight: .bold))
                .foregroundStyle(.white)
        }
        .scaleEffect(selected ? 1.25 : 1)
        .animation(.spring(duration: 0.3), value: selected)
    }
}

/// Bottom card for the selected site.
private struct SiteMapCard: View {
    let site: Site
    let onClose: () -> Void

    @Environment(AppStore.self) private var store
    @Environment(\.l10n) private var l10n
    @Environment(\.fmt) private var fmt

    var body: some View {
        let day = store.sim.days.dayKey(store.now)
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(site.name).font(.headline)
                    Text("\(site.location) · \(l10n.siteType(site.type))").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                StatusBadge(text: l10n.status(site.status.rawValue), color: site.status.color)
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill").font(.title3).foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(l10n.t("common.close")))
            }
            HStack {
                stat(l10n.t("map.capacity"), fmt.power(site.capacityKw))
                stat(l10n.t("sites.currentPower"), fmt.power(store.currentKw(site)))
                stat(l10n.t("sites.energyToday"), fmt.energy(store.sim.siteDayKwhCached(site, day: day)))
            }
            NavigationLink(value: AppRoute.site(site.id)) {
                Text(l10n.t("map.openSite"))
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .foregroundStyle(.white)
                    .background(Brand.gradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 18, y: 8)
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            Text(value).font(.subheadline.weight(.semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
