package com.solarpulse.app.data

import com.solarpulse.core.data.SolarJson
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.AppNotification
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Device
import com.solarpulse.core.model.Integration
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.InvoiceStatus
import com.solarpulse.core.model.NotificationKind
import com.solarpulse.core.model.Reading
import com.solarpulse.core.model.SavedReport
import com.solarpulse.core.model.Settings
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.sim.Sim
import com.solarpulse.core.time.Days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Single source of truth for the UI (web `DataProvider`): optimistic in-memory updates published
 * through [db], then persisted by the [Backend]. Failures surface through [error].
 */
class DataRepository(
    private val backend: Backend,
    private val sim: Sim,
    private val seed: () -> Database,
) {
    private val mutex = Mutex()
    private val loadMutex = Mutex()
    private val _db = MutableStateFlow<Database?>(null)
    val db: StateFlow<Database?> = _db.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, Reading>>(emptyMap())
    /** Latest real telemetry per site (Supabase `readings`, last 15 minutes). */
    val readings: StateFlow<Map<String, Reading>> = _readings.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val isRemote: Boolean get() = backend.remote

    fun clearError() {
        _error.value = null
    }

    suspend fun ensureLoaded(): Database = _db.value ?: load()

    /** (Re)loads everything; on failure falls back to demo data so the UI stays usable. */
    suspend fun load(): Database = loadMutex.withLock {
        _loading.value = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    backend.load()
                } catch (e: Exception) {
                    _error.value = e.message ?: e.toString()
                    _db.value ?: seed()
                }
            }
            val fresh = refreshOverdue(loaded)
            _db.value = fresh
            refreshReadings()
            fresh
        } finally {
            _loading.value = false
        }
    }

    /** Forgets the loaded data (after signing out of Supabase). */
    fun clear() {
        _db.value = null
        _readings.value = emptyMap()
    }

    suspend fun refreshReadings() {
        if (!backend.remote) return
        val since = Days.iso(sim.now() - 15 * 60_000L)
        val list = runCatching { withContext(Dispatchers.IO) { backend.latestReadings(since) } }.getOrElse { emptyList() }
        _readings.value = list.associateBy { it.siteId }
    }

    /** Pending invoices whose due date passed become overdue (web `refreshOverdue`). */
    private suspend fun refreshOverdue(db: Database): Database {
        val today = sim.todayKey()
        val changed = db.invoices.filter { it.status == InvoiceStatus.PENDING && it.dueAt < today }
        if (changed.isEmpty()) return db
        val updated = changed.map { it.copy(status = InvoiceStatus.OVERDUE) }.associateBy { it.id }
        val next = db.copy(invoices = db.invoices.map { updated[it.id] ?: it })
        runCatching {
            withContext(Dispatchers.IO) {
                backend.upsert(Table.INVOICES, updated.values.map { encode(Invoice.serializer(), it) }, next)
            }
        }
        return next
    }

    // ── generic mutation helpers ────────────────────────────────────────────

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
        SolarJson.json.encodeToJsonElement(serializer, value).jsonObject

    private suspend fun mutate(transform: (Database) -> Database, persist: suspend (Database) -> Unit) {
        val next = mutex.withLock {
            val cur = _db.value ?: return
            transform(cur).also { _db.value = it }
        }
        try {
            withContext(Dispatchers.IO) { persist(next) }
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
        }
    }

    private fun <T> List<T>.upsertBy(row: T, id: (T) -> String): List<T> {
        val i = indexOfFirst { id(it) == id(row) }
        return if (i >= 0) toMutableList().also { it[i] = row } else listOf(row) + this
    }

    private suspend fun <T> upsertRows(
        table: Table,
        serializer: KSerializer<T>,
        rows: List<T>,
        id: (T) -> String,
        lens: (Database, (List<T>) -> List<T>) -> Database,
    ) {
        if (rows.isEmpty()) return
        mutate(
            transform = { db -> lens(db) { list -> rows.fold(list) { acc, r -> acc.upsertBy(r, id) } } },
            persist = { snap -> backend.upsert(table, rows.map { encode(serializer, it) }, snap) },
        )
    }

    private suspend fun <T> removeRows(
        table: Table,
        ids: List<String>,
        id: (T) -> String,
        lens: (Database, (List<T>) -> List<T>) -> Database,
    ) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        mutate(
            transform = { db -> lens(db) { list -> list.filterNot { id(it) in set } } },
            persist = { snap -> backend.delete(table, ids, snap) },
        )
    }

    // ── typed API ───────────────────────────────────────────────────────────

    suspend fun upsertSite(site: Site) =
        upsertRows(Table.SITES, Site.serializer(), listOf(site), { it.id }) { db, f -> db.copy(sites = f(db.sites)) }

    /** Deletes a site and everything that belongs to it (web `removeSite`). */
    suspend fun removeSite(id: String) {
        val cur = _db.value ?: return
        removeDevices(cur.devices.filter { it.siteId == id }.map { it.id })
        removeTickets(cur.tickets.filter { it.siteId == id }.map { it.id })
        removeInvoices(cur.invoices.filter { it.siteId == id }.map { it.id })
        removeIntegration(cur.integrations.filter { it.siteId == id }.map { it.id })
        removeAlertRules(cur.alertRules.filter { it.siteId == id }.map { it.id })
        removeRows<Site>(Table.SITES, listOf(id), { it.id }) { db, f -> db.copy(sites = f(db.sites)) }
    }

    suspend fun upsertDevice(device: Device) =
        upsertRows(Table.DEVICES, Device.serializer(), listOf(device), { it.id }) { db, f -> db.copy(devices = f(db.devices)) }

    suspend fun removeDevices(ids: List<String>) =
        removeRows<Device>(Table.DEVICES, ids, { it.id }) { db, f -> db.copy(devices = f(db.devices)) }

    suspend fun upsertTicket(ticket: Ticket) =
        upsertRows(Table.TICKETS, Ticket.serializer(), listOf(ticket), { it.id }) { db, f -> db.copy(tickets = f(db.tickets)) }

    suspend fun removeTickets(ids: List<String>) =
        removeRows<Ticket>(Table.TICKETS, ids, { it.id }) { db, f -> db.copy(tickets = f(db.tickets)) }

    suspend fun upsertInvoices(invoices: List<Invoice>) =
        upsertRows(Table.INVOICES, Invoice.serializer(), invoices, { it.id }) { db, f -> db.copy(invoices = f(db.invoices)) }

    suspend fun removeInvoices(ids: List<String>) =
        removeRows<Invoice>(Table.INVOICES, ids, { it.id }) { db, f -> db.copy(invoices = f(db.invoices)) }

    suspend fun upsertReport(report: SavedReport) =
        upsertRows(Table.REPORTS, SavedReport.serializer(), listOf(report), { it.id }) { db, f -> db.copy(reports = f(db.reports)) }

    suspend fun removeReport(id: String) =
        removeRows<SavedReport>(Table.REPORTS, listOf(id), { it.id }) { db, f -> db.copy(reports = f(db.reports)) }

    suspend fun upsertAlertRules(rules: List<AlertRule>) =
        upsertRows(Table.ALERT_RULES, AlertRule.serializer(), rules, { it.id }) { db, f -> db.copy(alertRules = f(db.alertRules)) }

    suspend fun removeAlertRules(ids: List<String>) =
        removeRows<AlertRule>(Table.ALERT_RULES, ids, { it.id }) { db, f -> db.copy(alertRules = f(db.alertRules)) }

    suspend fun upsertIntegration(integration: Integration) =
        upsertRows(Table.INTEGRATIONS, Integration.serializer(), listOf(integration), { it.id }) { db, f -> db.copy(integrations = f(db.integrations)) }

    suspend fun removeIntegration(ids: List<String>) =
        removeRows<Integration>(Table.INTEGRATIONS, ids, { it.id }) { db, f -> db.copy(integrations = f(db.integrations)) }

    /** Throws on failure so the caller can tell the user (web `int.secretFailed`). */
    suspend fun setIntegrationSecret(id: String, secret: Map<String, String>) =
        withContext(Dispatchers.IO) { backend.setIntegrationSecret(id, secret) }

    suspend fun upsertNotifications(list: List<AppNotification>) =
        upsertRows(Table.NOTIFICATIONS, AppNotification.serializer(), list, { it.id }) { db, f -> db.copy(notifications = f(db.notifications)) }

    suspend fun removeNotifications(ids: List<String>) =
        removeRows<AppNotification>(Table.NOTIFICATIONS, ids, { it.id }) { db, f -> db.copy(notifications = f(db.notifications)) }

    suspend fun markAllRead() {
        val unread = _db.value?.notifications?.filter { !it.read } ?: return
        upsertNotifications(unread.map { it.copy(read = true) })
    }

    /** In-app notification; skipped when the user turned in-app notifications off (web `notify`). */
    suspend fun notify(title: String, body: String, kind: NotificationKind, link: String?): AppNotification? {
        val s = _db.value?.settings ?: return null
        if (!s.notifyPush) return null
        val n = AppNotification(newId("ntf-"), title, body, kind, link, false, Days.iso(sim.now()))
        upsertNotifications(listOf(n))
        return n
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        mutate(
            transform = { db -> db.copy(settings = transform(db.settings)) },
            persist = { snap -> backend.saveSettings(snap.settings, snap) },
        )
    }

    suspend fun replaceAll(next: Database) {
        mutex.withLock { _db.value = next }
        try {
            withContext(Dispatchers.IO) { backend.replaceAll(next) }
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
        }
    }

    /** Fresh demo data, keeping who the user is and how the app looks (web `resetDemo`). */
    suspend fun resetDemo() {
        sim.clearCache()
        val fresh = seed()
        val s = _db.value?.settings
        val next = if (s == null) fresh else fresh.copy(
            settings = fresh.settings.copy(language = s.language, theme = s.theme, userName = s.userName, email = s.email),
        )
        replaceAll(next)
    }

    companion object {
        fun newId(prefix: String): String = prefix + UUID.randomUUID().toString()
        /** 32 hex characters, like the database default for `ingest_token`. */
        fun newToken(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
