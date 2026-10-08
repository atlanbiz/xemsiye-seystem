import XCTest
import SwiftUI
@testable import SolarPulse
import SolarPulseKit

final class SolarPulseAppTests: XCTestCase {
    /// The String Catalog is compiled into the app bundle (this test bundle is hosted by the app).
    func testLocalizationFollowsInAppLanguage() {
        XCTAssertEqual(L10n(lang: .en).t("nav.overview"), "Overview")
        XCTAssertEqual(L10n(lang: .ug).t("nav.overview"), "ئومۇمىي كۆرۈنۈش")
        XCTAssertNotEqual(L10n(lang: .ar).t("nav.overview"), "nav.overview")
        XCTAssertNotEqual(L10n(lang: .tr).t("nav.overview"), "nav.overview")
        // unknown keys fall back to the key itself
        XCTAssertEqual(L10n(lang: .tr).t("does.not.exist"), "does.not.exist")
    }

    func testPlaceholders() {
        XCTAssertEqual(L10n(lang: .en).t("bill.generated", ["n": "16"]), "16 invoices generated")
    }

    func testEveryLanguageHasTheIOSOnlyStrings() {
        for lang in AppLanguage.allCases {
            let l = L10n(lang: lang)
            for key in ["nav.more", "auth.heroTitle", "alerts.enableNotifs", "set.storage.device"] {
                XCTAssertNotEqual(l.t(key), key, "\(lang.rawValue) is missing \(key)")
            }
        }
    }

    func testRTL() {
        XCTAssertTrue(L10n(lang: .ug).isRTL)
        XCTAssertTrue(L10n(lang: .ar).isRTL)
        XCTAssertFalse(L10n(lang: .en).isRTL)
        XCTAssertFalse(L10n(lang: .tr).isRTL)
        XCTAssertEqual(L10n(lang: .ar).layoutDirection, .rightToLeft)
    }

    func testDeepLinks() {
        XCTAssertEqual(DeepLink.route(for: "/sites/site-11"), .site("site-11"))
        XCTAssertEqual(DeepLink.route(for: "/billing?open=inv-1"), .invoice("inv-1"))
        XCTAssertEqual(DeepLink.route(for: "/billing"), .section(.billing))
        XCTAssertEqual(DeepLink.route(for: "/maintenance?open=tkt-01"), .ticket("tkt-01"))
        XCTAssertEqual(DeepLink.route(for: "/devices?q=INV-01-1"), .section(.devices))
        XCTAssertNil(DeepLink.route(for: nil))
    }

    func testLocalRepositoryPersistsAcrossInstances() async throws {
        let name = "test-\(UUID().uuidString).json"
        let repo = LocalRepository(fileName: name)
        let db = try await repo.load()
        XCTAssertEqual(db.sites.count, 16)
        var site = db.sites[0]
        site.name = "Renamed"
        try await repo.upsert(site)
        try await repo.remove(Device.self, id: db.devices[0].id)

        let reopened = try await LocalRepository(fileName: name).load()
        XCTAssertEqual(reopened.sites.first { $0.id == site.id }?.name, "Renamed")
        XCTAssertEqual(reopened.devices.count, db.devices.count - 1)
    }

    @MainActor
    func testPinDiameterGrowsWithCapacity() {
        XCTAssertLessThan(SitePin.diameter(12), SitePin.diameter(820))
        XCTAssertLessThanOrEqual(SitePin.diameter(100_000), 48)
        XCTAssertGreaterThanOrEqual(SitePin.diameter(0), 18)
    }
}
