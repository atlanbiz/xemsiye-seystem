import Foundation
import Observation
import Supabase

/// Email/password auth: Supabase Auth when configured, otherwise a local demo session.
@MainActor
@Observable
final class AuthStore {
    enum Outcome: Equatable {
        case signedIn
        case confirmEmail
        case failed(String)
    }

    private(set) var email: String?
    private(set) var isRestoring = true

    var isSignedIn: Bool { email != nil }
    var isSupabase: Bool { Backend.shared.client != nil }

    private static let demoKey = "solarpulse.demo.session"

    func restore() async {
        defer { isRestoring = false }
        if let client = Backend.shared.client {
            let session: Session? = try? await client.auth.session
            if let session {
                email = session.user.email ?? "user"
            }
        } else {
            email = UserDefaults.standard.string(forKey: AuthStore.demoKey)
        }
    }

    func signIn(email rawEmail: String, password: String) async -> Outcome {
        let address = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !address.isEmpty, password.count >= 6 else { return .failed("invalid") }
        if let client = Backend.shared.client {
            do {
                let session = try await client.auth.signIn(email: address, password: password)
                email = session.user.email ?? address
                return .signedIn
            } catch {
                return .failed(error.localizedDescription)
            }
        }
        UserDefaults.standard.set(address, forKey: AuthStore.demoKey)
        email = address
        return .signedIn
    }

    func signUp(email rawEmail: String, password: String) async -> Outcome {
        let address = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !address.isEmpty, password.count >= 6 else { return .failed("invalid") }
        guard let client = Backend.shared.client else { return await signIn(email: address, password: password) }
        do {
            let response = try await client.auth.signUp(email: address, password: password)
            if let session = response.session {
                email = session.user.email ?? address
                return .signedIn
            }
            return .confirmEmail
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    func signOut() async {
        if let client = Backend.shared.client {
            try? await client.auth.signOut()
        }
        UserDefaults.standard.removeObject(forKey: AuthStore.demoKey)
        email = nil
    }
}
