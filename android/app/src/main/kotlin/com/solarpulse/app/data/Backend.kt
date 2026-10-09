package com.solarpulse.app.data

import com.solarpulse.core.data.SolarJson
import com.solarpulse.core.model.AlertRule
import com.solarpulse.core.model.AppNotification
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Device
import com.solarpulse.core.model.Integration
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.Reading
import com.solarpulse.core.model.SavedReport
import com.solarpulse.core.model.Settings
import com.solarpulse.core.model.Site
import com.solarpulse.core.model.Ticket
import com.solarpulse.core.model.withFinanceDefaults
import com.solarpulse.core.seed.normalized
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Supabase table names (PLATFORM.md §1), in parent → child order. */
enum class Table(val sql: String) {
    SITES("sites"),
    DEVICES("devices"),
    TICKETS("tickets"),
    INVOICES("invoices"),
    NOTIFICATIONS("notifications"),
    REPORTS("reports"),
    ALERT_RULES("alert_rules"),
    INTEGRATIONS("integrations"),
}

/** Persistence behind [DataRepository]: the demo JSON file or Supabase. */
interface Backend {
    val remote: Boolean
    suspend fun load(): Database
    /** [snapshot] is the full state after the change (what the local backend writes). */
    suspend fun upsert(table: Table, rows: List<JsonObject>, snapshot: Database)
    suspend fun delete(table: Table, ids: List<String>, snapshot: Database)
    suspend fun saveSettings(settings: Settings, snapshot: Database)
    suspend fun replaceAll(db: Database)
    suspend fun setIntegrationSecret(integrationId: String, secret: Map<String, String>)
    /** Newest real telemetry per site since [sinceIso] (empty in demo mode). */
    suspend fun latestReadings(sinceIso: String): List<Reading>
}

/** Sorts like the web (`sortByDate`). */
fun Database.sorted(): Database = copy(
    notifications = notifications.sortedByDescending { it.createdAt },
    tickets = tickets.sortedByDescending { it.createdAt },
    invoices = invoices.sortedWith(compareByDescending<Invoice> { it.period }.thenBy { it.number }),
    reports = reports.sortedByDescending { it.createdAt },
    sites = sites.sortedBy { it.id },
    devices = devices.sortedBy { it.id },
    alertRules = alertRules.sortedBy { it.createdAt },
    integrations = integrations.sortedBy { it.createdAt },
)

/** Demo mode: the whole database as one JSON file in the app's files dir (atomic writes). */
class LocalBackend(
    private val file: File,
    private val seed: () -> Database,
    private val defaultRules: () -> List<AlertRule>,
) : Backend {
    private val lock = Mutex()
    override val remote = false

    override suspend fun load(): Database = lock.withLock {
        if (file.isFile) {
            val parsed = runCatching { SolarJson.decode(file.readText()) }.getOrNull()
            if (parsed != null) {
                val db = parsed.first.normalized(parsed.second, defaultRules).sorted()
                write(db)
                return@withLock db
            }
        }
        seed().also { write(it) }
    }

    private fun write(db: Database) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(SolarJson.encode(db))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    override suspend fun upsert(table: Table, rows: List<JsonObject>, snapshot: Database) = lock.withLock { write(snapshot) }
    override suspend fun delete(table: Table, ids: List<String>, snapshot: Database) = lock.withLock { write(snapshot) }
    override suspend fun saveSettings(settings: Settings, snapshot: Database) = lock.withLock { write(snapshot) }
    override suspend fun replaceAll(db: Database) = lock.withLock { write(db) }

    /** Demo mode intentionally never stores vendor credentials. */
    override suspend fun setIntegrationSecret(integrationId: String, secret: Map<String, String>) = Unit
    override suspend fun latestReadings(sinceIso: String): List<Reading> = emptyList()
}

/** Supabase (PostgREST) backend; rows are snake_case JSON from the shared serializers. */
class SupabaseBackend(
    private val client: SupabaseClient,
    private val seed: () -> Database,
) : Backend {
    override val remote = true
    private val json = SolarJson.json

    private suspend fun <T> list(table: String, serializer: KSerializer<T>): List<T> {
        val result = client.from(table).select()
        return json.decodeFromString(ListSerializer(serializer), result.data)
    }

    override suspend fun load(): Database {
        val settingsRows = list("settings", Settings.serializer())
        var db = Database(
            sites = list(Table.SITES.sql, Site.serializer()).map { it.withFinanceDefaults() },
            devices = list(Table.DEVICES.sql, Device.serializer()),
            tickets = list(Table.TICKETS.sql, Ticket.serializer()),
            invoices = list(Table.INVOICES.sql, Invoice.serializer()),
            notifications = list(Table.NOTIFICATIONS.sql, AppNotification.serializer()),
            reports = list(Table.REPORTS.sql, SavedReport.serializer()),
            settings = settingsRows.firstOrNull { it.id == "settings" } ?: Settings(),
            alertRules = list(Table.ALERT_RULES.sql, AlertRule.serializer()),
            integrations = list(Table.INTEGRATIONS.sql, Integration.serializer()),
        )
        // First run on an empty project: seed it so the dashboard isn't blank (same as the web).
        if (db.sites.isEmpty()) {
            db = seed()
            replaceAll(db)
        }
        return db.sorted()
    }

    override suspend fun upsert(table: Table, rows: List<JsonObject>, snapshot: Database) {
        if (rows.isNotEmpty()) client.from(table.sql).upsert(rows)
    }

    override suspend fun delete(table: Table, ids: List<String>, snapshot: Database) {
        for (id in ids) client.from(table.sql).delete { filter { eq("id", id) } }
    }

    override suspend fun saveSettings(settings: Settings, snapshot: Database) {
        val row = json.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        client.from("settings").upsert(row)
    }

    override suspend fun replaceAll(db: Database) {
        // delete children first, insert parents first
        for (t in Table.entries.reversed()) client.from(t.sql).delete { filter { neq("id", "") } }
        suspend fun <T> insert(t: Table, s: KSerializer<T>, rows: List<T>) {
            if (rows.isEmpty()) return
            client.from(t.sql).insert(rows.map { json.encodeToJsonElement(s, it).jsonObject })
        }
        insert(Table.SITES, Site.serializer(), db.sites)
        insert(Table.DEVICES, Device.serializer(), db.devices)
        insert(Table.TICKETS, Ticket.serializer(), db.tickets)
        insert(Table.INVOICES, Invoice.serializer(), db.invoices)
        insert(Table.NOTIFICATIONS, AppNotification.serializer(), db.notifications)
        insert(Table.REPORTS, SavedReport.serializer(), db.reports)
        insert(Table.ALERT_RULES, AlertRule.serializer(), db.alertRules)
        insert(Table.INTEGRATIONS, Integration.serializer(), db.integrations)
        saveSettings(db.settings, db)
    }

    /** Write-only: secrets go through the security-definer RPC and are never read back. */
    override suspend fun setIntegrationSecret(integrationId: String, secret: Map<String, String>) {
        val params = buildJsonObject {
            put("p_integration_id", integrationId)
            put("p_secret", JsonObject(secret.mapValues { JsonPrimitive(it.value) }))
        }
        client.postgrest.rpc("set_integration_secret", params)
    }

    override suspend fun latestReadings(sinceIso: String): List<Reading> {
        val result = client.from("readings").select(Columns.list("site_id", "ts", "power_kw")) {
            filter { gte("ts", sinceIso) }
            order("ts", Order.DESCENDING)
            limit(1000)
        }
        return json.decodeFromString(ListSerializer(Reading.serializer()), result.data)
            .groupBy { it.siteId }
            .map { (_, v) -> v.first() }
    }
}
