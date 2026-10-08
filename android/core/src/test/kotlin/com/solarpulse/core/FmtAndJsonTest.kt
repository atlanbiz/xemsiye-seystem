package com.solarpulse.core

import com.solarpulse.core.data.SolarJson
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Currency
import com.solarpulse.core.model.Lang
import com.solarpulse.core.model.ThemeMode
import com.solarpulse.core.seed.Seed
import com.solarpulse.core.seed.normalized
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FmtAndJsonTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun numbers() {
        val f = Fmt(Lang.EN, Currency.USD, zone)
        assertEquals("$1,235", f.money(1234.5))
        assertEquals("$1,234.50", f.money2(1234.5))
        assertEquals("-$12", f.money(-12.3))
        assertEquals("CN¥7", Fmt(Lang.EN, Currency.CNY, zone).money(7.0))
        assertEquals("1.23 MW", f.power(1234.0))
        assertEquals("812.3 kW", f.power(812.34))
        assertEquals("4.55 MWh", f.energy(4545.85))
        assertEquals("1.20 GWh", f.energy(1_200_000.0))
        assertEquals("12.5 kWh", f.energy(12.46))
        assertEquals("+3.4%", f.signedPct(3.36))
        assertEquals("-3.4%", f.signedPct(-3.36))
        assertEquals("1.2 MWh", f.axisEnergy(1234.0))
    }

    @Test
    fun `latin digits in every language`() {
        for (lang in Lang.entries) {
            val f = Fmt(lang, Currency.EUR, zone)
            val s = f.money2(1234567.891) + f.date(LocalDate.of(2026, 6, 21)) + f.energy(98765.0) + f.time(0)
            assertFalse(s.any { it in '٠'..'٩' || it in '۰'..'۹' }, "$lang: $s")
        }
    }

    @Test
    fun `uyghur dates are composed like the web`() {
        val f = Fmt(Lang.UG, Currency.USD, zone)
        assertEquals("2026-يىلى 21-ئىيۇن", f.date(LocalDate.of(2026, 6, 21)))
        assertEquals("21-ئىيۇن", f.dateShort(LocalDate.of(2026, 6, 21)))
        assertEquals("2026-يىلى ئىيۇن", f.month("2026-06"))
        assertEquals("2026-يىلى 21-ئىيۇن، يەكشەنبە", f.dateLong(LocalDate.of(2026, 6, 21)))
    }

    @Test
    fun `relative time`() {
        val now = 1_782_027_000_000L
        val en = Fmt(Lang.EN, Currency.USD, zone)
        assertEquals("just now", en.ago(com.solarpulse.core.time.Days.iso(now - 5_000), now))
        assertEquals("25 minutes ago", en.ago(com.solarpulse.core.time.Days.iso(now - 25 * 60_000), now))
        assertEquals("قبل 3 ساعات", Fmt(Lang.AR, Currency.USD, zone).ago(com.solarpulse.core.time.Days.iso(now - 3 * 3600_000), now))
    }

    @Test
    fun `json round trip uses snake_case and tolerates nulls`() {
        val db = Seed(WebReference.sim()).build()
        val text = SolarJson.encode(db)
        assertTrue(text.contains("\"capacity_kw\""))
        assertTrue(text.contains("\"alert_rules\""))
        val (back, hasRules) = SolarJson.decode(text)
        assertTrue(hasRules)
        assertEquals(db, back)

        // A Supabase settings row with nulls falls back to defaults; unknown keys are ignored.
        val s = SolarJson.json.decodeFromString(
            com.solarpulse.core.model.Settings.serializer(),
            """{"id":"settings","theme":null,"language":"ar","discount_rate_pct":null,"unknown":1}""",
        )
        assertEquals(ThemeMode.LIGHT, s.theme)
        assertEquals(Lang.AR, s.language)
        assertEquals(6.0, s.discountRatePct)

        // Old saves without ROI fields or alert rules are upgraded.
        val old = """{"sites":[{"id":"s","name":"n","location":"l","type":"commercial","status":"active","capacity_kw":10,"customer":"c","install_date":"2024-01-01"}]}"""
        val (parsed, had) = SolarJson.decode(old)
        assertFalse(had)
        val up = parsed.normalized(had) { Seed(WebReference.sim()).buildAlertRules() }
        assertEquals(9000.0, up.sites.single().systemCost)
        assertEquals(135.0, up.sites.single().annualOpex)
        assertEquals(6, up.alertRules.size)
    }
}
