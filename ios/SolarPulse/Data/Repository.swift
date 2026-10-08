import Foundation
import SolarPulseKit

/// A row stored in one Supabase table / one demo collection.
protocol TableRow: IdentifiedRow {
    static var table: String { get }
    static var dbKeyPath: WritableKeyPath<DB, [Self]> { get }
}

extension Site: TableRow {
    static var table: String { "sites" }
    static var dbKeyPath: WritableKeyPath<DB, [Site]> { \DB.sites }
}

extension Device: TableRow {
    static var table: String { "devices" }
    static var dbKeyPath: WritableKeyPath<DB, [Device]> { \DB.devices }
}

extension Ticket: TableRow {
    static var table: String { "tickets" }
    static var dbKeyPath: WritableKeyPath<DB, [Ticket]> { \DB.tickets }
}

extension Invoice: TableRow {
    static var table: String { "invoices" }
    static var dbKeyPath: WritableKeyPath<DB, [Invoice]> { \DB.invoices }
}

extension AppNotification: TableRow {
    static var table: String { "notifications" }
    static var dbKeyPath: WritableKeyPath<DB, [AppNotification]> { \DB.notifications }
}

extension SavedReport: TableRow {
    static var table: String { "reports" }
    static var dbKeyPath: WritableKeyPath<DB, [SavedReport]> { \DB.reports }
}

extension AlertRule: TableRow {
    static var table: String { "alert_rules" }
    static var dbKeyPath: WritableKeyPath<DB, [AlertRule]> { \DB.alertRules }
}

extension Integration: TableRow {
    static var table: String { "integrations" }
    static var dbKeyPath: WritableKeyPath<DB, [Integration]> { \DB.integrations }
}

/// Persistence layer — Supabase when configured, otherwise a JSON file in Application Support.
protocol Repository: Sendable {
    var isRemote: Bool { get }
    func load() async throws -> DB
    func upsert<T: TableRow>(_ row: T) async throws
    func upsertMany<T: TableRow>(_ rows: [T]) async throws
    func remove<T: TableRow>(_ type: T.Type, id: String) async throws
    func saveSettings(_ settings: AppSettings) async throws
    func replaceAll(_ db: DB) async throws
    /// Stores vendor credentials write-only (rpc `set_integration_secret`). Demo mode never stores secrets.
    func setIntegrationSecret(integrationId: String, secret: [String: String]) async throws
    /// Newest reading per site at or after `since` (real telemetry).
    func latestReadings(since: Date) async throws -> [String: Reading]
}

// MARK: - Demo mode

/// Demo-mode store: the whole dataset as one JSON file in Application Support.
actor LocalRepository: Repository {
    nonisolated var isRemote: Bool { false }

    private var db: DB?
    private let fileURL: URL
    private let seed: SeedBuilder

    init(fileName: String = "solarpulse-db-v1.json", sim: Simulator = .live) {
        let base = (try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? URL.temporaryDirectory
        let dir = base.appending(path: "SolarPulse", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.fileURL = dir.appending(path: fileName)
        self.seed = SeedBuilder(sim: sim)
    }

    func load() async throws -> DB {
        if let data = try? Data(contentsOf: fileURL), let parsed = try? JSONDecoder().decode(DB.self, from: data) {
            var loaded = parsed
            loaded.sites = loaded.sites.map { $0.withDefaults() }
            db = loaded
            return loaded
        }
        let fresh = seed.build()
        db = fresh
        persist()
        return fresh
    }

    private func persist() {
        guard let db else { return }
        do {
            let data = try JSONEncoder().encode(db)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Storage full / unavailable: keep working in memory.
        }
    }

    private func apply<T: TableRow>(_ row: T, to list: inout [T]) {
        if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.insert(row, at: 0) }
    }

    func upsert<T: TableRow>(_ row: T) async throws {
        guard var current = db else { return }
        apply(row, to: &current[keyPath: T.dbKeyPath])
        db = current
        persist()
    }

    func upsertMany<T: TableRow>(_ rows: [T]) async throws {
        guard var current = db else { return }
        for row in rows { apply(row, to: &current[keyPath: T.dbKeyPath]) }
        db = current
        persist()
    }

    func remove<T: TableRow>(_ type: T.Type, id: String) async throws {
        guard var current = db else { return }
        current[keyPath: T.dbKeyPath].removeAll { $0.id == id }
        db = current
        persist()
    }

    func saveSettings(_ settings: AppSettings) async throws {
        db?.settings = settings
        persist()
    }

    func replaceAll(_ newDB: DB) async throws {
        db = newDB
        persist()
    }

    func setIntegrationSecret(integrationId: String, secret: [String: String]) async throws {
        // Demo mode: credentials are intentionally never stored on the device.
    }

    func latestReadings(since: Date) async throws -> [String: Reading] { [:] }
}
