package com.solarpulse.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Local-calendar helpers matching src/lib/utils.ts. Days are `YYYY-MM-DD` keys so they compare
 * lexicographically exactly like the web (`day < site.installDate`).
 */
object Days {
    fun key(d: LocalDate): String = d.toString() // ISO yyyy-MM-dd (4-digit years in our range)
    fun monthKey(d: LocalDate): String = YearMonth.from(d).toString()
    fun parse(key: String): LocalDate {
        val p = key.split("-")
        return LocalDate.of(p[0].toInt(), p.getOrElse(1) { "1" }.toInt(), p.getOrNull(2)?.toIntOrNull() ?: 1)
    }

    fun parseMonth(month: String): YearMonth {
        val p = month.split("-")
        return YearMonth.of(p[0].toInt(), p[1].toInt())
    }

    /** Inclusive list of calendar days from..to. */
    fun between(from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = from
        while (!d.isAfter(to)) {
            out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    private val ISO_MILLIS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** Same shape as JavaScript `Date.prototype.toISOString()` (always milliseconds, UTC). */
    fun iso(millis: Long): String = ISO_MILLIS.format(Instant.ofEpochMilli(millis))

    /** Parses ISO-8601 timestamps as stored by the web (`toISOString`) or Postgres (`+00:00`). */
    fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.recoverCatching {
        java.time.OffsetDateTime.parse(iso).toInstant()
    }.recoverCatching {
        java.time.OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }.recoverCatching {
        // Postgres may send "2026-06-21 07:30:00+00" (space separator, short offset)
        val normalized = iso.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it }
        java.time.OffsetDateTime.parse(normalized).toInstant()
    }.recoverCatching {
        LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
    }.getOrNull()

    fun parseMillis(iso: String?): Long? = iso?.let { parseInstant(it)?.toEpochMilli() }

    /** JS `new Date('YYYY-MM-DD')` is UTC midnight (date-only ISO form). */
    fun utcMidnightMillis(key: String): Long = parse(key).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Local date of an instant. */
    fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Fractional local hour of an instant (`getHours() + getMinutes()/60 + getSeconds()/3600`). */
    fun localHour(millis: Long, zone: ZoneId): Double {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return t.hour + t.minute / 60.0 + t.second / 3600.0
    }
}
