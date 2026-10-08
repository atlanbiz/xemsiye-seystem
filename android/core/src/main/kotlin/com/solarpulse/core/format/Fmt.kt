package com.solarpulse.core.format

import com.solarpulse.core.model.Currency
import com.solarpulse.core.model.Lang
import com.solarpulse.core.time.Days
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number / date formatting shared by every screen and the PDF exports — mirrors `fmt` in
 * src/context/i18n.tsx. Numbers always use Latin digits and en-US grouping in every language
 * (PLATFORM.md §4); Uyghur dates are composed by hand because ICU data for `ug` is incomplete.
 */
class Fmt(val lang: Lang, val currency: Currency, val zone: ZoneId = ZoneId.systemDefault()) {

    private val symbols = DecimalFormatSymbols.getInstance(Locale.US)

    private fun nf(max: Int, min: Int = 0, grouping: Boolean = true) = DecimalFormat().apply {
        decimalFormatSymbols = symbols
        isGroupingUsed = grouping
        groupingSize = 3
        maximumFractionDigits = max
        minimumFractionDigits = min
        roundingMode = RoundingMode.HALF_UP // Intl "halfExpand"
    }

    private val n0 = nf(0)
    private val n1 = nf(1)
    private val n2 = nf(2, 2)
    private val money0 = nf(0)
    private val money2f = nf(2, 2)

    private fun clean(v: Double) = if (v.isNaN() || v.isInfinite()) 0.0 else v

    fun num(v: Double, d: Int = 0): String = when (d) {
        0 -> n0.format(clean(v))
        1 -> n1.format(clean(v))
        else -> nf(d).format(clean(v))
    }.let { if (it == "-0") "0" else it }

    fun dec2(v: Double): String = n2.format(clean(v))

    fun money(v: Double): String = withSign(clean(v)) { money0.format(it) }
    fun money2(v: Double): String = withSign(clean(v)) { money2f.format(it) }

    private inline fun withSign(v: Double, f: (Double) -> String): String {
        val body = f(abs(v))
        val zero = body.all { it == '0' || it == '.' || it == ',' }
        return (if (v < 0 && !zero) "-" else "") + currency.symbol + body
    }

    /** Auto-scales kW → MW. */
    fun power(kw: Double): String = if (kw >= 1000) "${n2.format(kw / 1000)} MW" else "${n1.format(clean(kw))} kW"

    /** Auto-scales kWh → MWh → GWh. */
    fun energy(kwh: Double): String = when {
        kwh >= 1e6 -> "${n2.format(kwh / 1e6)} GWh"
        kwh >= 1000 -> "${n2.format(kwh / 1000)} MWh"
        else -> "${n1.format(clean(kwh))} kWh"
    }

    fun pct(v: Double, d: Int = 1): String = "${num(v, d)}%"
    fun signedPct(v: Double): String = "${if (v >= 0) "+" else ""}${n1.format(clean(v))}%"

    /** Compact axis labels with explicit units (web `axisEnergy` / `axisPower`). */
    fun axisEnergy(v: Double): String = when {
        v >= 1e6 -> "${trim1(v / 1e6)} GWh"
        v >= 1000 -> "${trim1(v / 1000)} MWh"
        else -> "${v.roundToLong()} kWh"
    }

    fun axisPower(v: Double): String = if (v >= 1000) "${trim1(v / 1000)} MW" else "${v.roundToLong()} kW"

    private fun trim1(v: Double) = nf(1, grouping = false).format(v)

    // ── dates ────────────────────────────────────────────────────────────────

    private val locale: Locale = when (lang) {
        Lang.EN -> Locale.forLanguageTag("en-US")
        Lang.TR -> Locale.forLanguageTag("tr-TR")
        Lang.AR -> Locale.forLanguageTag("ar-EG")
        Lang.UG -> Locale.forLanguageTag("ug-CN")
    }

    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale)

    private val pShort = pattern(when (lang) { Lang.EN -> "MMM d"; Lang.TR -> "d MMM"; else -> "d MMMM" })
    private val pDate = pattern(when (lang) { Lang.EN -> "MMM d, yyyy"; Lang.TR -> "d MMM yyyy"; else -> "d MMMM yyyy" })
    private val pLong = pattern(when (lang) { Lang.EN -> "EEEE, MMMM d, yyyy"; Lang.TR -> "d MMMM yyyy EEEE"; else -> "EEEE، d MMMM yyyy" })
    private val pMonth = pattern("MMMM yyyy")
    private val pMonthShort = pattern(if (lang == Lang.AR) "MMMM" else "MMM")
    private val pWeekday = pattern(if (lang == Lang.AR) "EEEE" else "EEE")

    /** Accepts a LocalDate, `YYYY-MM-DD` or an ISO timestamp. */
    fun toLocalDate(value: String): LocalDate =
        if (value.length == 10) Days.parse(value) else Days.parseInstant(value)?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone)

    fun date(value: String): String = date(toLocalDate(value))
    fun date(d: LocalDate): String = if (lang == Lang.UG) ug(d, year = true, day = true) else pDate.format(d)
    fun dateShort(d: LocalDate): String = if (lang == Lang.UG) ug(d, year = false, day = true) else pShort.format(d)
    fun dateLong(d: LocalDate): String =
        if (lang == Lang.UG) "${ug(d, year = true, day = true)}، ${UG_DAYS[d.dayOfWeek.value % 7]}" else pLong.format(d)

    fun month(ym: YearMonth): String = if (lang == Lang.UG) "${ym.year}-يىلى ${UG_MONTHS[ym.monthValue - 1]}" else pMonth.format(ym.atDay(1))
    fun month(period: String): String = month(Days.parseMonth(period))
    fun monthShort(d: LocalDate): String = if (lang == Lang.UG) UG_MONTHS[d.monthValue - 1] else pMonthShort.format(d)
    fun weekday(d: LocalDate): String = if (lang == Lang.UG) UG_DAYS_SHORT[d.dayOfWeek.value % 7] else pWeekday.format(d)

    fun time(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return "%02d:%02d".format(Locale.ROOT, t.hour, t.minute)
    }

    fun time(iso: String): String = Days.parseMillis(iso)?.let { time(it) } ?: iso

    fun dateTime(iso: String): String {
        val ms = Days.parseMillis(iso) ?: return iso
        return "${date(Days.localDate(ms, zone))} ${time(ms)}"
    }

    private fun ug(d: LocalDate, year: Boolean, day: Boolean): String {
        val m = UG_MONTHS[d.monthValue - 1]
        if (!day) return if (year) "${d.year}-يىلى $m" else m
        val md = "${d.dayOfMonth}-$m"
        return if (year) "${d.year}-يىلى $md" else md
    }

    /** Relative time ("5 minutes ago") in the UI language. */
    fun ago(iso: String, now: Long): String {
        val then = Days.parseMillis(iso) ?: return iso
        val s = (now - then) / 1000.0
        return when {
            s < 60 -> just()
            s < 3600 -> rel(Math.round(s / 60).toInt(), RelUnit.MINUTE)
            s < 86400 -> rel(Math.round(s / 3600).toInt(), RelUnit.HOUR)
            else -> rel(Math.round(s / 86400).toInt(), RelUnit.DAY)
        }
    }

    private enum class RelUnit { MINUTE, HOUR, DAY }

    private fun just() = when (lang) {
        Lang.UG -> "ھازىرلا"
        Lang.EN -> "just now"
        Lang.AR -> "الآن"
        Lang.TR -> "şimdi"
    }

    private fun rel(n: Int, u: RelUnit): String = when (lang) {
        Lang.UG -> "$n ${when (u) { RelUnit.MINUTE -> "مىنۇت"; RelUnit.HOUR -> "سائەت"; RelUnit.DAY -> "كۈن" }} ئىلگىرى"
        Lang.EN -> when {
            u == RelUnit.DAY && n == 1 -> "yesterday"
            else -> "$n ${when (u) { RelUnit.MINUTE -> "minute"; RelUnit.HOUR -> "hour"; RelUnit.DAY -> "day" }}${if (n == 1) "" else "s"} ago"
        }
        Lang.TR -> if (u == RelUnit.DAY && n == 1) "dün" else "$n ${when (u) { RelUnit.MINUTE -> "dakika"; RelUnit.HOUR -> "saat"; RelUnit.DAY -> "gün" }} önce"
        Lang.AR -> arabicAgo(n, u)
    }

    private fun arabicAgo(n: Int, u: RelUnit): String {
        val (one, two, few, many) = when (u) {
            RelUnit.MINUTE -> listOf("دقيقة واحدة", "دقيقتين", "دقائق", "دقيقة")
            RelUnit.HOUR -> listOf("ساعة واحدة", "ساعتين", "ساعات", "ساعة")
            RelUnit.DAY -> listOf("يوم واحد", "يومين", "أيام", "يومًا")
        }
        if (u == RelUnit.DAY && n == 1) return "أمس"
        return when {
            n == 1 -> "قبل $one"
            n == 2 -> "قبل $two"
            n % 100 in 3..10 -> "قبل $n $few"
            else -> "قبل $n $many"
        }
    }

    companion object {
        val UG_MONTHS = listOf("يانۋار", "فېۋرال", "مارت", "ئاپرېل", "ماي", "ئىيۇن", "ئىيۇل", "ئاۋغۇست", "سېنتەبىر", "ئۆكتەبىر", "نويابىر", "دېكابىر")
        /** Sunday first, like JavaScript `getDay()`. */
        val UG_DAYS = listOf("يەكشەنبە", "دۈشەنبە", "سەيشەنبە", "چارشەنبە", "پەيشەنبە", "جۈمە", "شەنبە")
        val UG_DAYS_SHORT = listOf("يە", "دۈ", "سە", "چا", "پە", "جۈ", "شە")

        /** City presets for the weather card (web `CITIES`). */
        data class City(val name: String, val en: String, val ar: String, val tr: String, val lat: Double, val lng: Double) {
            fun label(lang: Lang) = when (lang) { Lang.UG -> name; Lang.EN -> en; Lang.AR -> ar; Lang.TR -> tr }
        }

        val CITIES = listOf(
            City("ئۈرۈمچى", "Urumqi", "أورومتشي", "Urumçi", 43.825, 87.617),
            City("قەشقەر", "Kashgar", "كاشغر", "Kaşgar", 39.47, 75.99),
            City("تۇرپان", "Turpan", "توربان", "Turfan", 42.95, 89.18),
            City("خوتەن", "Hotan", "خوتان", "Hotan", 37.11, 79.92),
            City("غۇلجا", "Ghulja", "غولجا", "Gulca", 43.92, 81.32),
            City("ئاقسۇ", "Aksu", "آقسو", "Aksu", 41.17, 80.26),
            City("قۇمۇل", "Hami", "قومول", "Kumul", 42.82, 93.51),
            City("كورلا", "Korla", "كورلا", "Korla", 41.76, 86.15),
            City("ئالمۇتا", "Almaty", "ألماتي", "Almatı", 43.24, 76.89),
            City("ئىستانبۇل", "Istanbul", "إسطنبول", "İstanbul", 41.01, 28.98),
        )

        fun cityLabel(name: String, lang: Lang): String = CITIES.firstOrNull { it.name == name }?.label(lang) ?: name
    }
}
