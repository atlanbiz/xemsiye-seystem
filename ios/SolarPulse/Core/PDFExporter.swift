import SwiftUI
import SolarPulseKit

/// Renders a SwiftUI document view to a paginated A4 PDF with `ImageRenderer`.
/// The view gets the app language's layout direction, so Uyghur/Arabic PDFs are right-to-left
/// and shaped by Core Text (PLATFORM.md §3.2).
@MainActor
enum PDFExporter {
    static let pageSize = CGSize(width: 595, height: 842) // A4 @ 72 dpi

    static func render<V: View>(_ view: V, fileName: String, l10n: L10n, fmt: Fmt) -> URL? {
        let content = view
            .frame(width: pageSize.width)
            .background(Color.white)
            .environment(\.l10n, l10n)
            .environment(\.fmt, fmt)
            .environment(\.layoutDirection, l10n.layoutDirection)
            .environment(\.locale, l10n.locale)
            .environment(\.colorScheme, .light)

        let renderer = ImageRenderer(content: content)
        renderer.proposedSize = ProposedViewSize(width: pageSize.width, height: nil)

        let safeName = fileName.replacingOccurrences(of: "/", with: "-")
        let url = URL.temporaryDirectory.appending(path: safeName)
        try? FileManager.default.removeItem(at: url)

        var succeeded = false
        renderer.render { size, draw in
            let page = pageSize
            var box = CGRect(origin: .zero, size: page)
            guard let ctx = CGContext(url as CFURL, mediaBox: &box, nil) else { return }
            let height = max(size.height, 1)
            let pages = max(1, Int((height / page.height).rounded(.up)))
            for index in 0..<pages {
                ctx.beginPDFPage(nil)
                ctx.saveGState()
                // PDF space is bottom-up: shift so the slice [index·H, (index+1)·H] from the top fills this page.
                let offsetFromTop = CGFloat(index) * page.height
                ctx.translateBy(x: 0, y: -(height - offsetFromTop - page.height))
                draw(ctx)
                ctx.restoreGState()
                ctx.endPDFPage()
            }
            ctx.closePDF()
            succeeded = true
        }
        return succeeded ? url : nil
    }
}

/// Shared header used by printable documents.
struct DocumentHeader: View {
    let title: String
    let subtitle: String
    var company: String

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 20, weight: .bold))
                Text(subtitle).font(.system(size: 10)).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                BrandMark(size: 26)
                Text(company).font(.system(size: 9)).foregroundStyle(.secondary)
            }
        }
    }
}

/// Lightning-bolt logo + wordmark.
struct BrandMark: View {
    var size: CGFloat = 32
    var showWordmark = true

    var body: some View {
        HStack(spacing: size * 0.3) {
            Image(systemName: "bolt.fill")
                .font(.system(size: size * 0.5, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Brand.gradient, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
            if showWordmark {
                Text(verbatim: "SolarPulse")
                    .font(.system(size: size * 0.55, weight: .bold, design: .rounded))
                    .environment(\.layoutDirection, .leftToRight)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "SolarPulse"))
    }
}
