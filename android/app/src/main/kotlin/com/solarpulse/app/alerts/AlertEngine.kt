package com.solarpulse.app.alerts

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.solarpulse.app.R
import com.solarpulse.app.data.DataRepository
import com.solarpulse.core.alerts.Alerts
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.AlertMetric
import com.solarpulse.core.model.AlertSeverity
import com.solarpulse.core.model.Lang
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Demo-mode alert evaluation (PLATFORM.md §3.3): runs from WorkManager and whenever the app
 * resumes. Each firing rule gets lastTriggeredAt = now (6-hour cooldown), an in-app notification
 * (kind = severity, title = rule name, body names the matches, link to the first) and a system
 * notification. With Supabase the evaluate-alerts Edge Function does this on the server.
 */
class AlertEngine(
    private val repository: DataRepository,
    private val sim: Sim,
    private val notifier: Notifier,
    private val context: Context,
) {
    private val mutex = Mutex()

    /** Returns how many rules fired. */
    suspend fun evaluate(): Int = mutex.withLock {
        if (repository.isRemote) return@withLock 0
        val db = repository.ensureLoaded()
        val now = sim.now()
        val firings = Alerts.evaluate(db, sim, now)
        if (firings.isEmpty()) return@withLock 0
        val lang = appLanguage(db.settings.language)
        val ctx = localizedContext(context, lang)
        val fmt = Fmt(lang, db.settings.currency, sim.zone)
        repository.upsertAlertRules(firings.map { it.rule.copy(lastTriggeredAt = Days.iso(now)) })
        for (f in firings) {
            val unit = unitLabel(ctx, f.rule.metric)
            val body = Alerts.describe(f) { m ->
                val digits = if (f.rule.metric == AlertMetric.SITE_YIELD_BELOW) 2 else 0
                fmt.num(m.value ?: 0.0, digits) + (if (unit == "%") "" else " ") + (unit ?: "")
            }
            val kind = if (f.rule.severity == AlertSeverity.DANGER) NotificationKind.DANGER else NotificationKind.WARNING
            val n = repository.notify(f.rule.name, body, kind, f.matches.first().link)
            if (n != null) notifier.show(n)
        }
        firings.size
    }

    companion object {
        /** The language the UI is shown in: the per-app locale, else the shared setting. */
        fun appLanguage(fallback: Lang): Lang {
            val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language
            return if (tag.isNullOrBlank()) fallback else Lang.of(tag)
        }

        fun localizedContext(context: Context, lang: Lang): Context {
            val cfg = Configuration(context.resources.configuration)
            cfg.setLocale(Locale.forLanguageTag(lang.code))
            return context.createConfigurationContext(cfg)
        }

        /** Localised unit for a metric's threshold / measured value. */
        fun unitLabel(ctx: Context, metric: AlertMetric): String? = when (Alerts.unit(metric)) {
            "min" -> ctx.getString(R.string.alerts_unit_min)
            "d" -> ctx.getString(R.string.alerts_unit_days)
            else -> Alerts.unit(metric)
        }
    }
}
