import Foundation
import Supabase
import SolarPulseKit

/// Build-time configuration (Info.plist ← Config.xcconfig / Secrets.xcconfig).
enum AppConfig {
    static var supabaseURLString: String {
        (Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var supabaseAnonKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "SUPABASE_ANON_KEY") as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// nil → demo mode.
    static var supabaseURL: URL? {
        let s = supabaseURLString
        guard !s.isEmpty, !supabaseAnonKey.isEmpty, !s.contains("$("), let url = URL(string: s), url.scheme?.hasPrefix("http") == true else { return nil }
        return url
    }

    static var isSupabase: Bool { supabaseURL != nil }

    /// `POST {SUPABASE_URL}/functions/v1/ingest` (PLATFORM.md §3.4).
    static var ingestURL: String {
        let base = supabaseURL?.absoluteString ?? "https://<project-ref>.supabase.co"
        return (base.hasSuffix("/") ? String(base.dropLast()) : base) + "/functions/v1/ingest"
    }

    static var appVersion: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(v) (\(b))"
    }
}

/// Lazily created shared Supabase client (nil in demo mode).
final class Backend: @unchecked Sendable {
    static let shared = Backend()

    let client: SupabaseClient?

    private init() {
        if let url = AppConfig.supabaseURL {
            client = SupabaseClient(supabaseURL: url, supabaseKey: AppConfig.supabaseAnonKey)
        } else {
            client = nil
        }
    }

    func makeRepository() -> any Repository {
        if let client { return SupabaseRepository(client: client) }
        return LocalRepository()
    }
}
