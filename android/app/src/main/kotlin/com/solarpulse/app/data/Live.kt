package com.solarpulse.app.data

import com.solarpulse.core.model.Reading
import com.solarpulse.core.model.Site
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days

/**
 * Current power per PLATFORM.md §2: a site with a reading in the last 15 minutes shows the real
 * powerKw (badge "Live"); otherwise the simulator's value.
 */
object Live {
    private const val WINDOW_MS = 15 * 60_000L

    fun reading(site: Site, readings: Map<String, Reading>, now: Long): Reading? {
        val r = readings[site.id] ?: return null
        val ts = Days.parseMillis(r.ts) ?: return null
        return if (now - ts <= WINDOW_MS) r else null
    }

    fun siteKw(sim: Sim, site: Site, readings: Map<String, Reading>, now: Long): Double =
        reading(site, readings, now)?.powerKw ?: sim.siteKw(site, Days.key(Days.localDate(now, sim.zone)), Days.localHour(now, sim.zone))

    /** Fleet power: real readings where available, the jittered simulation for the rest. */
    fun fleetKw(sim: Sim, sites: List<Site>, readings: Map<String, Reading>, now: Long): Double {
        val (live, simulated) = sites.partition { reading(it, readings, now) != null }
        return live.sumOf { reading(it, readings, now)!!.powerKw } + if (simulated.isEmpty()) 0.0 else sim.liveKw(simulated, now)
    }

    fun anyLive(sites: List<Site>, readings: Map<String, Reading>, now: Long): Boolean =
        sites.any { reading(it, readings, now) != null }
}
