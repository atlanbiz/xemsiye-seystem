import Foundation
import Supabase
import SolarPulseKit

/// Supabase-backed repository (PostgREST + RPC). Rows use the snake_case columns of
/// `supabase/schema.sql` through the models' CodingKeys, so no key strategy is needed.
final class SupabaseRepository: Repository, @unchecked Sendable {
    let client: SupabaseClient

    init(client: SupabaseClient) {
        self.client = client
    }

    var isRemote: Bool { true }

    private func fetch<T: Decodable>(_ table: String, as type: T.Type) async throws -> [T] {
        let response = try await client.from(table).select().execute()
        return try JSONDecoder().decode([T].self, from: response.data)
    }

    /// Tables added in schema v2 may not exist on older projects — treat them as empty.
    private func fetchOptional<T: Decodable>(_ table: String, as type: T.Type) async -> [T] {
        (try? await fetch(table, as: type)) ?? []
    }

    func load() async throws -> DB {
        var db = DB()
        db.sites = try await fetch(Site.table, as: Site.self).map { $0.withDefaults() }
        db.devices = try await fetch(Device.table, as: Device.self)
        db.tickets = try await fetch(Ticket.table, as: Ticket.self)
        db.invoices = try await fetch(Invoice.table, as: Invoice.self)
        db.notifications = try await fetch(AppNotification.table, as: AppNotification.self)
        db.reports = try await fetch(SavedReport.table, as: SavedReport.self)
        db.alertRules = await fetchOptional(AlertRule.table, as: AlertRule.self)
        db.integrations = await fetchOptional(Integration.table, as: Integration.self)
        let settingsRows = await fetchOptional("settings", as: AppSettings.self)
        db.settings = settingsRows.first { $0.id == "settings" } ?? .defaults

        // First run on an empty project: seed it so the dashboard isn't blank (same as the web).
        if db.sites.isEmpty {
            let seed = SeedBuilder(sim: .live).build()
            try await replaceAll(seed)
            return seed
        }
        db.sortForDisplay()
        return db
    }

    func upsert<T: TableRow>(_ row: T) async throws {
        try await client.from(T.table).upsert(row).execute()
    }

    func upsertMany<T: TableRow>(_ rows: [T]) async throws {
        guard !rows.isEmpty else { return }
        try await client.from(T.table).upsert(rows).execute()
    }

    func remove<T: TableRow>(_ type: T.Type, id: String) async throws {
        try await client.from(T.table).delete().eq("id", value: id).execute()
    }

    func saveSettings(_ settings: AppSettings) async throws {
        try await client.from("settings").upsert(settings).execute()
    }

    private func deleteAll(_ table: String) async throws {
        try await client.from(table).delete().neq("id", value: "").execute()
    }

    private func insert<T: TableRow>(_ rows: [T]) async throws {
        guard !rows.isEmpty else { return }
        // keep request bodies reasonable
        var start = 0
        while start < rows.count {
            let chunk = Array(rows[start..<min(start + 200, rows.count)])
            try await client.from(T.table).insert(chunk).execute()
            start += 200
        }
    }

    /// Children are deleted first and parents inserted first.
    func replaceAll(_ db: DB) async throws {
        for table in ["integrations", "alert_rules", "reports", "notifications", "invoices", "tickets", "devices", "sites"] {
            if table == "integrations" || table == "alert_rules" {
                try? await deleteAll(table)
            } else {
                try await deleteAll(table)
            }
        }
        try await insert(db.sites)
        try await insert(db.devices)
        try await insert(db.tickets)
        try await insert(db.invoices)
        try await insert(db.notifications)
        try await insert(db.reports)
        try? await insert(db.alertRules)
        try? await insert(db.integrations)
        try await saveSettings(db.settings)
    }

    private struct SecretParams: Encodable, Sendable {
        let p_integration_id: String
        let p_secret: [String: String]
    }

    func setIntegrationSecret(integrationId: String, secret: [String: String]) async throws {
        try await client.rpc("set_integration_secret", params: SecretParams(p_integration_id: integrationId, p_secret: secret)).execute()
    }

    func latestReadings(since: Date) async throws -> [String: Reading] {
        let response = try await client.from("readings")
            .select("site_id,ts,power_kw,energy_kwh")
            .gte("ts", value: ISODate.string(since))
            .order("ts", ascending: false)
            .limit(1000)
            .execute()
        let rows = try JSONDecoder().decode([Reading].self, from: response.data)
        var latest: [String: Reading] = [:]
        for r in rows where latest[r.siteId] == nil { latest[r.siteId] = r }
        return latest
    }
}
