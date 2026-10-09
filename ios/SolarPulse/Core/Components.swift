import SwiftUI
import UIKit
import Charts
import SolarPulseKit

// MARK: - Card with title

/// Rounded card with an optional title row and trailing accessory.
struct Card<Content: View, Trailing: View>: View {
    var title: String?
    var systemImage: String?
    @ViewBuilder var trailing: () -> Trailing
    @ViewBuilder var content: () -> Content

    init(_ title: String? = nil, systemImage: String? = nil,
         @ViewBuilder trailing: @escaping () -> Trailing,
         @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.systemImage = systemImage
        self.trailing = trailing
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if title != nil || Trailing.self != EmptyView.self {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    if let title {
                        if let systemImage {
                            Label(title, systemImage: systemImage)
                                .font(.headline)
                                .labelStyle(.titleAndIcon)
                        } else {
                            Text(title).font(.headline)
                        }
                    }
                    Spacer(minLength: 8)
                    trailing()
                }
                .accessibilityElement(children: .contain)
            }
            content()
        }
        .card()
    }
}

extension Card where Trailing == EmptyView {
    init(_ title: String? = nil, systemImage: String? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.init(title, systemImage: systemImage, trailing: { EmptyView() }, content: content)
    }
}

// MARK: - Small building blocks

/// Rounded square icon tile ("bg-blue-100 text-blue-600").
struct IconTile: View {
    let systemName: String
    var tint: Color = Brand.primary
    var size: CGFloat = 36

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Coloured capsule with a dot — status chips.
struct StatusBadge: View {
    let text: String
    let color: Color
    var showDot = true

    var body: some View {
        HStack(spacing: 5) {
            if showDot { Circle().fill(color).frame(width: 6, height: 6) }
            Text(text).lineLimit(1)
        }
        .font(.caption2.weight(.semibold))
        .foregroundStyle(color)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.13), in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

/// Pulsing "Live" badge for real telemetry.
struct LiveBadge: View {
    let text: String
    @State var pulse = false

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(Brand.green)
                .frame(width: 7, height: 7)
                .scaleEffect(pulse ? 1.25 : 0.85)
                .opacity(pulse ? 0.6 : 1)
            Text(text)
        }
        .font(.caption2.weight(.bold))
        .foregroundStyle(Brand.green)
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(Brand.green.opacity(0.12), in: Capsule())
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

/// Circular progress ring with centred content.
struct Ring<Center: View>: View {
    var value: Double // 0…100
    var color: Color = Brand.b500
    var lineWidth: CGFloat = 7
    var size: CGFloat = 88
    @ViewBuilder var center: () -> Center

    var body: some View {
        ZStack {
            Circle().stroke(color.opacity(0.15), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0, min(1, value / 100)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(duration: 0.8), value: value)
            center()
        }
        .frame(width: size, height: size)
    }
}

/// Thin horizontal bar for health / efficiency.
struct HealthBar: View {
    let value: Double
    var width: CGFloat? = 64

    var body: some View {
        HStack(spacing: 6) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.subtleFill)
                    Capsule().fill(healthColor(value))
                        .frame(width: geo.size.width * max(0, min(1, value / 100)))
                }
            }
            .frame(width: width, height: 6)
            Text("\(Int(value.rounded()))%")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .environment(\.layoutDirection, .leftToRight)
    }
}

/// Tiny line chart used in KPI tiles and lists.
struct Sparkline: View {
    let values: [Double]
    var color: Color = Brand.b500
    var fill = true

    var body: some View {
        Chart {
            ForEach(Array(values.enumerated()), id: \.offset) { item in
                if fill {
                    AreaMark(x: .value("i", item.offset), y: .value("v", item.element))
                        .interpolationMethod(.catmullRom)
                        .foregroundStyle(LinearGradient(colors: [color.opacity(0.35), color.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                }
                LineMark(x: .value("i", item.offset), y: .value("v", item.element))
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(color)
                    .lineStyle(StrokeStyle(lineWidth: 1.8))
            }
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartLegend(.hidden)
        .ltrChart()
        .accessibilityHidden(true)
    }
}

/// KPI tile: icon, label, big value, change / footnote, sparkline.
struct KPITile: View {
    let icon: String
    let tint: Color
    let title: String
    let value: String
    var change: Double?
    var changeLabel: String?
    var footnote: AnyView?
    var spark: [Double] = []

    @Environment(\.fmt) private var fmt

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            IconTile(systemName: icon, tint: tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Text(value)
                    .font(.title3.weight(.semibold))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .contentTransition(.numericText())
                if let change {
                    HStack(spacing: 4) {
                        Text(fmt.signedPct(change))
                            .foregroundStyle(change >= 0 ? Brand.green : Brand.danger)
                            .environment(\.layoutDirection, .leftToRight)
                        if let changeLabel { Text(changeLabel).foregroundStyle(.secondary) }
                    }
                    .font(.caption2)
                    .lineLimit(1)
                } else if let footnote {
                    footnote
                }
            }
            Spacer(minLength: 4)
            if spark.count > 1 {
                Sparkline(values: spark, color: tint)
                    .frame(width: 72, height: 34)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.cardStroke.opacity(0.6), lineWidth: 0.5))
        .shadow(color: .black.opacity(0.05), radius: 10, y: 4)
        .accessibilityElement(children: .combine)
    }
}

/// Simple stat tile used on list screens (Devices, Billing, Maintenance…).
struct StatTile: View {
    let icon: String
    let tint: Color
    let title: String
    let value: String
    var sub: String?

    var body: some View {
        HStack(spacing: 12) {
            IconTile(systemName: icon, tint: tint, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Text(value).font(.headline).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
                    .contentTransition(.numericText())
                if let sub { Text(sub).font(.caption2).foregroundStyle(.secondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.cardStroke.opacity(0.6), lineWidth: 0.5))
        .accessibilityElement(children: .combine)
    }
}

/// Label/value row (detail sheets).
struct InfoRow: View {
    let label: String
    let value: String
    /// Numbers, codes and serials stay left-to-right inside RTL layouts.
    var ltrValue = false
    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value)
                .fontWeight(.medium)
                .multilineTextAlignment(.trailing)
                .environment(\.layoutDirection, ltrValue ? .leftToRight : layoutDirection)
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
    }
}

/// Horizontal chip picker.
struct ChipPicker<Value: Hashable>: View {
    let options: [(Value, String)]
    @Binding var selection: Value

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.0) { option in
                    let on = option.0 == selection
                    Button {
                        withAnimation(.snappy) { selection = option.0 }
                    } label: {
                        Text(option.1)
                            .font(.footnote.weight(.medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .foregroundStyle(on ? Color.white : Color.primary)
                            .background(on ? AnyShapeStyle(Brand.gradient) : AnyShapeStyle(Theme.card), in: Capsule())
                            .overlay(Capsule().strokeBorder(on ? Color.clear : Theme.cardStroke, lineWidth: 0.8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(.horizontal, 1)
            .padding(.vertical, 2)
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}

/// Shimmer for skeleton placeholders (`.redacted(reason: .placeholder)` + shimmer).
struct Shimmer: ViewModifier {
    @State var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geo in
                    LinearGradient(colors: [.clear, .white.opacity(0.35), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.6)
                        .offset(x: phase * geo.size.width * 1.6)
                }
                .allowsHitTesting(false)
            )
            .clipped()
            .onAppear {
                withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) { phase = 1 }
            }
    }
}

extension View {
    func shimmering(_ active: Bool = true) -> some View {
        Group {
            if active { self.redacted(reason: .placeholder).modifier(Shimmer()) } else { self }
        }
    }
}

/// Skeleton of the dashboard while data loads.
struct SkeletonDashboard: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                RoundedRectangle(cornerRadius: 26).fill(Theme.card).frame(height: 190)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 12)], spacing: 12) {
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 18).fill(Theme.card).frame(height: 84)
                    }
                }
                RoundedRectangle(cornerRadius: 22).fill(Theme.card).frame(height: 260)
                RoundedRectangle(cornerRadius: 22).fill(Theme.card).frame(height: 200)
            }
            .padding()
            .modifier(Shimmer())
        }
        .background(AppBackground())
        .accessibilityLabel(Text("…"))
    }
}

/// In-app toast.
struct ToastMessage: Identifiable, Equatable {
    enum Tone { case success, info, warning, error }
    let id = UUID()
    let text: String
    let tone: Tone

    var color: Color {
        switch tone {
        case .success: return Brand.green
        case .info: return Brand.b500
        case .warning: return Brand.amber
        case .error: return Brand.danger
        }
    }

    var icon: String {
        switch tone {
        case .success: return "checkmark.circle.fill"
        case .info: return "info.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .error: return "xmark.octagon.fill"
        }
    }
}

struct ToastView: View {
    let toast: ToastMessage

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.icon).foregroundStyle(toast.color)
            Text(toast.text).font(.subheadline.weight(.medium)).lineLimit(3)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: Capsule())
        .shadow(color: .black.opacity(0.15), radius: 16, y: 6)
        .padding(.horizontal, 24)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isStaticText)
    }
}

/// Copyable monospaced value (ingest URL / token).
struct CopyRow: View {
    let label: String
    let value: String
    var onCopied: () -> Void = {}
    @State var copied = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Text(value)
                    .font(.caption.monospaced())
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .textSelection(.enabled)
                Spacer(minLength: 4)
                Button {
                    UIPasteboard.general.string = value
                    copied += 1
                    onCopied()
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(label))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Theme.subtleFill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .environment(\.layoutDirection, .leftToRight)
        }
        .sensoryFeedback(.success, trigger: copied)
    }
}
