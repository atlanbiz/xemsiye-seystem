import XCTest
@testable import SolarPulseKit

final class FormatterTests: XCTestCase {
    let days = DayMath(timeZone: TimeZone(identifier: "UTC")!)

    func testNumbersAlwaysUseLatinDigits() {
        for lang in AppLanguage.allCases {
            let f = Fmt(lang: lang, currency: .USD, days: days)
            XCTAssertEqual(f.num(1234.567, 1), "1,234.6")
            XCTAssertEqual(f.num(12), "12")
            XCTAssertEqual(f.money(1234.4), "$1,234")
            XCTAssertEqual(f.money2(9786.24), "$9,786.24")
            XCTAssertEqual(f.power(579.0091), "579 kW")
            XCTAssertEqual(f.power(1234.5), "1.23 MW")
            XCTAssertEqual(f.energy(4545.85), "4.55 MWh")
            XCTAssertEqual(f.energy(2_500_000), "2.50 GWh")
            XCTAssertEqual(f.pct(97.25), "97.3%")
            XCTAssertEqual(f.signedPct(-3.21), "-3.2%")
            XCTAssertEqual(f.signedPct(4), "+4%")
        }
    }

    func testAxisLabels() {
        XCTAssertEqual(Fmt.axisEnergy(1500), "1.5 MWh")
        XCTAssertEqual(Fmt.axisEnergy(2_000_000), "2 GWh")
        XCTAssertEqual(Fmt.axisEnergy(420.4), "420 kWh")
        XCTAssertEqual(Fmt.axisPower(980), "980 kW")
    }

    func testUyghurDates() {
        let f = Fmt(lang: .ug, currency: .USD, days: days)
        let d = days.parseDay("2026-06-21") // Sunday
        XCTAssertEqual(f.date(d), "2026-يىلى 21-ئىيۇن")
        XCTAssertEqual(f.dateShort(d), "21-ئىيۇن")
        XCTAssertEqual(f.month(d), "2026-يىلى ئىيۇن")
        XCTAssertEqual(f.dateLong(d), "2026-يىلى 21-ئىيۇن، يەكشەنبە")
        XCTAssertEqual(f.period("2026-05"), "2026-يىلى ماي")
    }

    func testDayMath() {
        XCTAssertEqual(days.dayKey(days.makeDate(year: 2026, monthIndex0: 2, day: 0)), "2026-02-28")
        XCTAssertEqual(days.dayKey(days.addMonths(days.parseDay("2026-01-31"), 1)), "2026-02-01")
        XCTAssertEqual(days.dayKey(days.endOfMonth(days.parseDay("2024-02-10"))), "2024-02-29")
        XCTAssertEqual(days.daysBetween(days.parseDay("2026-06-01"), days.parseDay("2026-06-03")).count, 3)
        XCTAssertEqual(days.monthKey(days.parseDay("2026-12-31")), "2026-12")
    }

    func testCSVEscapes() {
        let csv = ReportBuilder.csv([["a", "b,c"], ["\"q\"", "x"]])
        XCTAssertEqual(csv, "\u{FEFF}a,\"b,c\"\n\"\"\"q\"\"\",x")
    }
}
