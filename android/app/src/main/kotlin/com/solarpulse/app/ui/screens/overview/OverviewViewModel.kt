package com.solarpulse.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solarpulse.app.AppContainer
import com.solarpulse.app.data.Live
import com.solarpulse.app.data.Weather
import com.solarpulse.app.ui.pctChange
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Reading
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.SiteStatus
import com.solarpulse.core.sim.DeviceStats
import com.solarpulse.core.sim.Flow
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.sim.SimMath
import com.solarpulse.core.time.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class GenPeriod { DAILY, WEEKLY, MONTHLY }
enum class ImpactPeriod { MONTHLY, YEARLY, ALL }

data class Generation(
    val xs: List<Double>,
    val ys: List<Double>,
    /** Category labels for weekly / monthly (index = x). */
    val labels: List<LocalDate>,
    val total: Double,
    val change: Double,
    val peak: Double,
    val hourly: Boolean,
)

data class Impact(val co2: Double, val trees: Double, val cars: Double)

data class SiteRow(val site: Site, val kw: Double, val kwh: Double, val spark: List<Double>, val live: Boolean)

data class OverviewState(
    val now: Long,
    val userFirstName: String,
    val hour: Int,
    val health: Health,
    val totalSites: Int,
    val active: Int,
    val idle: Int,
    val capacityKw: Double,
    val liveKw: Double,
    val anyLive: Boolean,
    val liveSpark: List<Double>,
    val todayKwh: Double,
    val todayChange: Double,
    val co2Mtd: Double,
    val co2Change: Double,
    val revenueMtd: Double,
    val revenueChange: Double,
    val spark14: List<Double>,
    val revSpark: List<Double>,
    val stats: DeviceStats,
    val flow: Flow,
    val generation: Generation,
    val impact: Impact,
    val topSites: List<SiteRow>,
)

enum class Health { EXCELLENT, GOOD, ATTENTION }

/** Dashboard numbers — same derivations as the web Overview page. */
class OverviewViewModel(private val c: AppContainer) : ViewModel() {
    private val sim = c.sim
    val period = MutableStateFlow(GenPeriod.DAILY)
    val impactPeriod = MutableStateFlow(ImpactPeriod.MONTHLY)

    private val _weather = MutableStateFlow(c.weather.simulated())
    val weather: StateFlow<Weather> = _weather.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Heavy month/period aggregates are recomputed once a minute, not on every 5 s tick.
    private var minuteKey: Any? = null
    private var minuteCache: MinuteData? = null

    private data class MinuteData(
        val todayKwh: Double, val todayChange: Double, val mtd: Double, val prev: Double,
        val revM: Double, val revP: Double, val spark14: List<Double>, val liveSpark: List<Double>,
        val topBase: List<Triple<Site, Double, List<Double>>>,
    )

    val state: StateFlow<OverviewState?> = combine(
        c.repository.db.filterNotNull(),
        c.repository.readings,
        c.ticker,
        period,
        impactPeriod,
    ) { db, readings, now, p, ip -> compute(db, readings, now, p, ip) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            while (true) {
                val s = c.repository.db.value?.settings
                if (s != null) _weather.value = c.weather.current(s.lat, s.lng)
                delay(15 * 60_000L)
            }
        }
    }

    /** Reloads the weather when the configured city changes. */
    fun loadWeather(lat: Double, lng: Double) {
        viewModelScope.launch { _weather.value = c.weather.current(lat, lng) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            minuteKey = null
            c.repository.load()
            c.repository.db.value?.settings?.let { _weather.value = c.weather.current(it.lat, it.lng) }
            _refreshing.value = false
        }
    }

    private fun compute(db: Database, readings: Map<String, Reading>, now: Long, p: GenPeriod, ip: ImpactPeriod): OverviewState {
        val sites = db.sites
        val today = Days.localDate(now, sim.zone)
        val todayKey = Days.key(today)
        val hourNow = Days.localHour(now, sim.zone)
        val key = Triple(db.sites, now / 60_000, readings)
        val m = minuteCache.takeIf { minuteKey == key } ?: minuteData(sites, today, hourNow).also {
            minuteKey = key
            minuteCache = it
        }
        val stats = Sim.deviceStats(db.devices)
        val live = Live.fleetKw(sim, sites, readings, now)
        val anyLive = Live.anyLive(sites, readings, now)
        val s = db.settings
        return OverviewState(
            now = now,
            userFirstName = s.userName.split(' ').firstOrNull().orEmpty(),
            hour = Days.localHour(now, sim.zone).toInt(),
            health = when {
                stats.availability >= 95 -> Health.EXCELLENT
                stats.availability >= 85 -> Health.GOOD
                else -> Health.ATTENTION
            },
            totalSites = sites.size,
            active = sites.count { it.status == SiteStatus.ACTIVE },
            idle = sites.count { it.status == SiteStatus.IDLE },
            capacityKw = sites.sumOf { it.capacityKw },
            liveKw = live,
            anyLive = anyLive,
            liveSpark = m.liveSpark,
            todayKwh = m.todayKwh,
            todayChange = m.todayChange,
            co2Mtd = SimMath.co2Tons(m.mtd, s.co2KgPerKwh),
            co2Change = pctChange(m.mtd, m.prev),
            revenueMtd = m.revM,
            revenueChange = pctChange(m.revM, m.revP),
            spark14 = m.spark14,
            revSpark = m.spark14.mapIndexed { i, v -> v * (0.95 + (i % 3) * 0.03) },
            stats = stats,
            flow = sim.energyFlow(sites, now, if (anyLive) live else null),
            generation = generation(sites, today, todayKey, hourNow, p),
            impact = impact(db, today, ip),
            topSites = m.topBase.map { (site, kwh, spark) ->
                SiteRow(site, Live.siteKw(sim, site, readings, now), kwh, spark, Live.reading(site, readings, now) != null)
            },
        )
    }

    private fun minuteData(sites: List<Site>, today: LocalDate, hourNow: Double): MinuteData {
        val mStart = today.withDayOfMonth(1)
        val prevMonth = YearMonth.from(today).minusMonths(1)
        val pStart = prevMonth.atDay(1)
        val pEnd = prevMonth.atDay(minOf(today.dayOfMonth, prevMonth.lengthOfMonth()))
        val mtd = sim.totalKwh(sites, mStart, today)
        val prev = sim.totalKwh(sites, pStart, pEnd)
        fun revenue(from: LocalDate, to: LocalDate) = sites.sumOf { sim.totalKwh(listOf(it), from, to) * it.pricePerKwh }
        val todayKwh = sim.totalKwh(sites, today, today)
        val yd = Days.key(today.minusDays(1))
        // same partial-day comparison as the web: full 15-minute steps up to the current hour
        val ydSame = sites.sumOf { x ->
            var sum = 0.0
            var h = 0.0
            while (h < hourNow) {
                sum += sim.siteKw(x, yd, h + 0.125) * 0.25
                h += 0.25
            }
            sum
        }
        val todayKey = Days.key(today)
        val top = sites
            .map { s ->
                Triple(
                    s,
                    sim.siteDayKwhCached(s, todayKey),
                    sim.hourlySeries(listOf(s), todayKey, 60).filter { it.kw != null && it.hour >= 6 }.map { it.kw!! },
                )
            }
            .sortedByDescending { it.second }
            .take(6)
        return MinuteData(
            todayKwh = todayKwh,
            todayChange = pctChange(todayKwh, ydSame),
            mtd = mtd,
            prev = prev,
            revM = revenue(mStart, today),
            revP = revenue(pStart, pEnd),
            spark14 = sim.dailySeries(sites, today.minusDays(13), today).map { it.kwh },
            liveSpark = sim.hourlySeries(sites, todayKey, 60).mapNotNull { it.kw }.takeLast(10),
            topBase = top,
        )
    }

    private fun generation(sites: List<Site>, today: LocalDate, todayKey: String, hourNow: Double, p: GenPeriod): Generation {
        if (p == GenPeriod.DAILY) {
            val pts = sim.hourlySeries(sites, todayKey, 30).filter { it.kw != null }
            val total = sim.totalKwh(sites, today, today)
            val yd = Days.key(today.minusDays(1))
            val prevTotal = sites.sumOf { sim.siteDayKwh(it, yd, hourNow) }
            return Generation(
                xs = pts.map { it.hour },
                ys = pts.map { it.kw!! },
                labels = emptyList(),
                total = total,
                change = pctChange(total, prevTotal),
                peak = pts.maxOfOrNull { it.kw!! } ?: 0.0,
                hourly = true,
            )
        }
        val days = if (p == GenPeriod.WEEKLY) 7L else 30L
        val series = sim.dailySeries(sites, today.minusDays(days - 1), today)
        val prev = sim.dailySeries(sites, today.minusDays(2 * days - 1), today.minusDays(days))
        val total = series.sumOf { it.kwh }
        return Generation(
            xs = series.indices.map { it.toDouble() },
            ys = series.map { it.kwh },
            labels = series.map { it.date },
            total = total,
            change = pctChange(total, prev.sumOf { it.kwh }),
            peak = series.maxOfOrNull { it.kwh } ?: 0.0,
            hourly = false,
        )
    }

    private fun impact(db: Database, today: LocalDate, ip: ImpactPeriod): Impact {
        val from = when (ip) {
            ImpactPeriod.MONTHLY -> today.withDayOfMonth(1)
            ImpactPeriod.YEARLY -> today.withDayOfYear(1)
            ImpactPeriod.ALL -> db.sites.minOfOrNull { it.installDate }?.let { Days.parse(it) } ?: today
        }
        val kwh = sim.totalKwh(db.sites, from, today)
        val s = db.settings
        val co2 = SimMath.co2Tons(kwh, s.co2KgPerKwh)
        return Impact(co2, (co2 * 1000) / s.treeKgPerYear, co2 / s.carTonsPerYear)
    }
}
