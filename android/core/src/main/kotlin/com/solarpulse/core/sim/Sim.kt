package com.solarpulse.core.sim

import com.solarpulse.core.model.Device
import com.solarpulse.core.model.DeviceStatus
import com.solarpulse.core.model.DeviceType
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.model.jsRound
import com.solarpulse.core.time.Days
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, clock-free parts of the deterministic simulator — an exact port of src/lib/sim.ts.
 *
 * `hash`/`rand` reproduce JavaScript's 32-bit semantics: `Math.imul` is Kotlin's wrapping `Int`
 * multiplication, `x >>> n` is `ushr`, and `x >>> 0` is the unsigned reinterpretation. Trigonometry
 * uses [StrictMath] (fdlibm, like V8) so results agree with the web to the last few ulps.
 */
object SimMath {
    const val PERF = 0.82 // system performance ratio
    private const val TWO_POW_32 = 4294967296.0

    /** FNV-1a over UTF-16 code units, returned as an unsigned 32-bit value. */
    fun hash(str: String): Long = hashInt(str).toLong() and 0xffffffffL

    private fun hashInt(str: String): Int {
        var h = 0x811c9dc5.toInt() // 2166136261
        for (c in str) {
            h = h xor c.code
            h *= 16777619 // Math.imul
        }
        return h
    }

    /** mulberry32-style mix of [hash]; uniform in [0, 1). */
    fun rand(seed: String): Double {
        var t = hashInt(seed) + 0x6d2b79f5
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        val u = (t xor (t ushr 14)).toLong() and 0xffffffffL
        return u / TWO_POW_32
    }

    /** Normalised irradiance curve 0..1 for a fractional hour. */
    fun solarCurve(h: Double): Double {
        if (h <= 6 || h >= 20) return 0.0
        val x = StrictMath.sin((Math.PI * (h - 6)) / 14)
        return StrictMath.pow(max(0.0, x), 1.6)
    }

    /** ∫ solarCurve over a day with siteDayKwh's 15-minute midpoint rule (web `DAY_CURVE`). */
    val DAY_CURVE: Double = run {
        var sum = 0.0
        var h = 0.0
        while (h < 24) {
            sum += solarCurve(h + 0.125) * 0.25
            h += 0.25
        }
        sum
    }

    /** Cloudiness factor for a day (shared by all sites — same weather region). */
    fun weatherFactor(day: String): Double {
        val r = rand("wx$day")
        return if (r < 0.12) 0.35 + r * 2 else 0.78 + r * 0.22
    }

    /** Seasonal factor from a day-of-year computed like the web (see [Sim.dayOfYear]). */
    fun seasonFromDoy(doy: Double): Double = 0.68 + 0.32 * StrictMath.sin((2 * Math.PI * (doy - 80)) / 365)

    fun co2Tons(kwh: Double, factorKgPerKwh: Double): Double = (kwh * factorKgPerKwh) / 1000
}

data class HourPoint(val hour: Double, val label: String, val kw: Double?)
data class DayPoint(val key: String, val date: LocalDate, val kwh: Double)

data class Flow(
    val solar: Double,
    val consumption: Double,
    /** + charging, − discharging */
    val battery: Double,
    /** + import, − export */
    val grid: Double,
    val batterySoc: Double,
    val batteryCapacity: Double,
)

data class DeviceStats(
    val availability: Double,
    val inverterEfficiency: Double,
    val batteryHealth: Double,
    val online: Int,
    val warning: Int,
    val offline: Int,
)

data class SimWeather(val temp: Int, val code: Int, val irradiance: Int, val wind: Int, val humidity: Int)

enum class RangeKind(val days: Int) { D7(6), D30(29), D90(89), M12(364) }

/**
 * Clock-aware simulator. Every number in the UI is derived from site capacity + date + hour so
 * charts, KPIs, reports and invoices agree on every platform. [zone] is the device's local time
 * zone (the web uses the browser's), [clock] returns epoch millis.
 */
class Sim(
    val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = ConcurrentHashMap<String, Double>()

    fun now(): Long = clock()
    fun today(): LocalDate = Days.localDate(now(), zone)
    fun todayKey(): String = Days.key(today())
    fun nowHour(): Double = Days.localHour(now(), zone)

    /**
     * Web: `floor((d - new Date(d.getFullYear(), 0, 0)) / 86400000)` in local time, so DST offsets
     * shift the result exactly as they do in the browser.
     */
    fun dayOfYear(millis: Long): Double {
        val local = Days.localDate(millis, zone)
        val jan0 = LocalDate.of(local.year, 1, 1).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return floor((millis - jan0) / 86400000.0)
    }

    fun seasonFactorAt(millis: Long): Double = SimMath.seasonFromDoy(dayOfYear(millis))

    fun seasonFactor(day: String): Double =
        seasonFactorAt(Days.parse(day).atStartOfDay(zone).toInstant().toEpochMilli())

    private fun statusFactor(site: Site, day: String): Double {
        if (day != todayKey()) return 1.0
        return when (site.status) {
            SiteStatus.OFFLINE, SiteStatus.MAINTENANCE -> 0.0
            SiteStatus.IDLE -> 0.15
            SiteStatus.ACTIVE -> 1.0
        }
    }

    /** Instantaneous kW output for a site at a given day + fractional hour. */
    fun siteKw(site: Site, day: String, hour: Double): Double {
        if (day < site.installDate) return 0.0
        val siteVar = 0.92 + SimMath.rand(site.id + day) * 0.1
        return site.capacityKw * SimMath.PERF * SimMath.solarCurve(hour) * seasonFactor(day) *
            SimMath.weatherFactor(day) * siteVar * statusFactor(site, day)
    }

    /** kWh produced by a site on a day; with [untilHour] only up to that hour (for today). */
    fun siteDayKwh(site: Site, day: String, untilHour: Double = 24.0): Double {
        if (day < site.installDate) return 0.0
        // Same factors and multiplication order as siteKw, hoisted out of the 15-minute loop.
        val a = site.capacityKw * SimMath.PERF
        val season = seasonFactor(day)
        val weather = SimMath.weatherFactor(day)
        val siteVar = 0.92 + SimMath.rand(site.id + day) * 0.1
        val status = statusFactor(site, day)
        var sum = 0.0
        val step = 0.25
        var h = 0.0
        while (h < untilHour) {
            val kw = a * SimMath.solarCurve(h + step / 2) * season * weather * siteVar * status
            sum += kw * min(step, untilHour - h)
            h += step
        }
        return sum
    }

    /**
     * Full-day kWh of a past day, ignoring the install date (web `siteFullDayKwh`). Equals
     * siteDayKwh(site, day) for installed days, in O(1) — used for the 365-day finance estimate.
     */
    fun siteFullDayKwh(site: Site, day: String): Double =
        site.capacityKw * SimMath.PERF * SimMath.DAY_CURVE * seasonFactor(day) * SimMath.weatherFactor(day) *
            (0.92 + SimMath.rand(site.id + day) * 0.1)

    /** Full-day production (cached for past days); today counts up to the current hour. */
    fun siteDayKwhCached(site: Site, day: String): Double {
        val today = todayKey()
        if (day >= today) return siteDayKwh(site, day, if (day == today) nowHour() else 24.0)
        val k = "${site.id}|${site.capacityKw}|${site.installDate}|$day"
        return cache.getOrPut(k) { siteDayKwh(site, day) }
    }

    /** Small live jitter so the "current power" visibly breathes. */
    fun liveKw(sites: List<Site>, t: Long = now()): Double {
        val day = Days.key(Days.localDate(t, zone))
        val h = Days.localHour(t, zone)
        val jitter = 1 + StrictMath.sin(t / 7000.0) * 0.015 + (SimMath.rand(Math.floorDiv(t, 5000L).toString()) - 0.5) * 0.02
        return sites.sumOf { siteKw(it, day, h) } * jitter
    }

    fun siteKwNow(site: Site): Double = siteKw(site, todayKey(), nowHour())

    fun totalKwh(sites: List<Site>, from: LocalDate, to: LocalDate): Double {
        var s = 0.0
        for (d in Days.between(from, to)) {
            val k = Days.key(d)
            for (site in sites) s += siteDayKwhCached(site, k)
        }
        return s
    }

    fun dailySeries(sites: List<Site>, from: LocalDate, to: LocalDate): List<DayPoint> =
        Days.between(from, to).map { d ->
            val k = Days.key(d)
            var sum = 0.0
            for (x in sites) sum += siteDayKwhCached(x, k)
            DayPoint(k, d, sum)
        }

    fun hourlySeries(sites: List<Site>, day: String, stepMin: Int = 30): List<HourPoint> {
        val limit = if (day == todayKey()) nowHour() else 24.0
        val out = ArrayList<HourPoint>()
        var m = 0
        while (m <= 24 * 60) {
            val h = m / 60.0
            val label = "%02d:%02d".format(java.util.Locale.ROOT, floor(h).toInt() % 24, m % 60)
            out.add(HourPoint(h, label, if (h <= limit) sites.sumOf { siteKw(it, day, h) } else null))
            m += stepMin
        }
        return out
    }

    /** Building load model, kW. */
    fun consumptionKw(sites: List<Site>, h: Double, day: String): Double {
        val cap = sites.sumOf { it.capacityKw }
        val base = 0.09 + 0.07 * StrictMath.exp(-StrictMath.pow(h - 9, 2.0) / 6) +
            0.12 * StrictMath.exp(-StrictMath.pow(h - 19.5, 2.0) / 5) + 0.08 * SimMath.solarCurve(h)
        return cap * base * (0.95 + SimMath.rand("load" + day + floor(h * 4).toLong()) * 0.1)
    }

    fun energyFlow(sites: List<Site>, t: Long = now(), solarOverride: Double? = null): Flow {
        val day = Days.key(Days.localDate(t, zone))
        val h = Days.localHour(t, zone)
        val solar = solarOverride ?: liveKw(sites, t)
        val consumption = consumptionKw(sites, h, day)
        val batteryCapacity = sites.sumOf { it.batteryKwh }
        // SOC follows a simple daily shape: charges through the day, discharges at night
        val soc = max(0.15, min(0.98, 0.35 + 0.6 * StrictMath.sin((Math.PI * max(0.0, min(h, 22.0) - 8)) / 18)))
        val maxRate = batteryCapacity * 0.25
        var battery: Double
        var grid: Double
        val net = solar - consumption
        if (net >= 0) {
            battery = if (soc < 0.97) min(net, maxRate) else 0.0
            grid = -(net - battery)
        } else {
            val need = -net
            val discharge = if (soc > 0.2) min(need, maxRate) else 0.0
            battery = -discharge
            grid = need - discharge
        }
        return Flow(solar, consumption, battery, grid, soc * 100, batteryCapacity)
    }

    /** Range helpers relative to today. */
    fun rangeOf(kind: RangeKind): Pair<LocalDate, LocalDate> {
        val to = today()
        return to.minusDays(kind.days.toLong()) to to
    }

    /** Simulated weather for when the network weather API is unavailable. */
    fun simWeather(t: Long = now()): SimWeather {
        val local = java.time.Instant.ofEpochMilli(t).atZone(zone)
        val day = Days.key(local.toLocalDate())
        val wf = SimMath.weatherFactor(day)
        val h = local.hour + local.minute / 60.0
        val seasonal = seasonFactorAt(t)
        val temp = jsRound(-5 + seasonal * 32 + 6 * StrictMath.sin((Math.PI * (h - 8)) / 12) + (SimMath.rand("t$day") - 0.5) * 4)
        return SimWeather(
            temp = temp.toInt(),
            code = if (wf < 0.6) 3 else if (wf < 0.85) 2 else 0,
            irradiance = jsRound(1000 * SimMath.solarCurve(h) * seasonal * wf).toInt(),
            wind = jsRound(4 + SimMath.rand("w$day") * 18).toInt(),
            humidity = jsRound(25 + SimMath.rand("h$day") * 40).toInt(),
        )
    }

    fun clearCache() = cache.clear()

    companion object {
        fun deviceStats(devices: List<Device>): DeviceStats {
            val total = if (devices.isEmpty()) 1 else devices.size
            val online = devices.count { it.status == DeviceStatus.ONLINE }
            val warning = devices.count { it.status == DeviceStatus.WARNING }
            val inv = devices.filter { it.type == DeviceType.INVERTER }
            val bat = devices.filter { it.type == DeviceType.BATTERY }
            fun avg(a: List<Device>, f: (Device) -> Double) = if (a.isEmpty()) 0.0 else a.sumOf(f) / a.size
            return DeviceStats(
                availability = ((online + warning * 0.5) / total) * 100,
                inverterEfficiency = avg(inv) { if (it.status == DeviceStatus.OFFLINE) 0.0 else it.efficiency },
                batteryHealth = avg(bat) { it.health },
                online = online,
                warning = warning,
                offline = devices.size - online - warning,
            )
        }
    }
}
