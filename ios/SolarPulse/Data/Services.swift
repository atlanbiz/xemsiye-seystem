import Foundation
import UserNotifications
import SolarPulseKit

/// Current conditions for the dashboard weather card.
struct WeatherNow: Sendable, Equatable {
    var temp: Int
    var code: Int
    var irradiance: Int
    var wind: Int
    var humidity: Int
    /// true = Open-Meteo, false = simulated fallback
    var live: Bool

    init(sim: Simulator.SimWeather) {
        temp = sim.temp
        code = sim.code
        irradiance = sim.irradiance
        wind = sim.wind
        humidity = sim.humidity
        live = false
    }

    init(temp: Int, code: Int, irradiance: Int, wind: Int, humidity: Int, live: Bool) {
        self.temp = temp
        self.code = code
        self.irradiance = irradiance
        self.wind = wind
        self.humidity = humidity
        self.live = live
    }
}

/// Live weather from Open-Meteo (no API key) — mirrors src/lib/weather.ts.
enum WeatherService {
    private struct Response: Decodable {
        struct Current: Decodable {
            let temperature_2m: Double?
            let relative_humidity_2m: Double?
            let weather_code: Int?
            let wind_speed_10m: Double?
            let shortwave_radiation: Double?
        }
        let current: Current?
    }

    static func fetch(lat: Double, lng: Double) async -> WeatherNow? {
        var comps = URLComponents(string: "https://api.open-meteo.com/v1/forecast")
        comps?.queryItems = [
            URLQueryItem(name: "latitude", value: String(lat)),
            URLQueryItem(name: "longitude", value: String(lng)),
            URLQueryItem(name: "current", value: "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,shortwave_radiation"),
            URLQueryItem(name: "timezone", value: "auto"),
        ]
        guard let url = comps?.url else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
            let decoded = try JSONDecoder().decode(Response.self, from: data)
            guard let c = decoded.current else { return nil }
            return WeatherNow(
                temp: Int(jsRound(c.temperature_2m ?? 0)),
                code: c.weather_code ?? 0,
                irradiance: Int(jsRound(c.shortwave_radiation ?? 0)),
                wind: Int(jsRound(c.wind_speed_10m ?? 0)),
                humidity: Int(jsRound(c.relative_humidity_2m ?? 0)),
                live: true
            )
        } catch {
            return nil
        }
    }

    /// Weather city names in the UI language (web `CITIES`).
    struct City: Sendable, Identifiable {
        let name: String
        let en: String
        let ar: String
        let tr: String
        let lat: Double
        let lng: Double
        var id: String { name }

        func label(_ lang: AppLanguage) -> String {
            switch lang {
            case .ug: return name
            case .en: return en
            case .ar: return ar
            case .tr: return tr
            }
        }
    }

    static let cities: [City] = [
        City(name: "ئۈرۈمچى", en: "Urumqi", ar: "أورومتشي", tr: "Urumçi", lat: 43.825, lng: 87.617),
        City(name: "قەشقەر", en: "Kashgar", ar: "كاشغر", tr: "Kaşgar", lat: 39.47, lng: 75.99),
        City(name: "تۇرپان", en: "Turpan", ar: "توربان", tr: "Turfan", lat: 42.95, lng: 89.18),
        City(name: "خوتەن", en: "Hotan", ar: "خوتان", tr: "Hotan", lat: 37.11, lng: 79.92),
        City(name: "غۇلجا", en: "Ghulja", ar: "غولجا", tr: "Gulca", lat: 43.92, lng: 81.32),
        City(name: "ئاقسۇ", en: "Aksu", ar: "آقسو", tr: "Aksu", lat: 41.17, lng: 80.26),
        City(name: "قۇمۇل", en: "Hami", ar: "قومول", tr: "Kumul", lat: 42.82, lng: 93.51),
        City(name: "كورلا", en: "Korla", ar: "كورلا", tr: "Korla", lat: 41.76, lng: 86.15),
        City(name: "ئالمۇتا", en: "Almaty", ar: "ألماتي", tr: "Almatı", lat: 43.24, lng: 76.89),
        City(name: "ئىستانبۇل", en: "Istanbul", ar: "إسطنبول", tr: "İstanbul", lat: 41.01, lng: 28.98),
    ]

    static func cityLabel(_ name: String, lang: AppLanguage) -> String {
        cities.first { $0.name == name }?.label(lang) ?? name
    }
}

/// Local notifications (UserNotifications) for alert-rule firings in demo mode.
enum LocalNotifier {
    static func authorizationStatus() async -> UNAuthorizationStatus {
        await withCheckedContinuation { (cont: CheckedContinuation<UNAuthorizationStatus, Never>) in
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                cont.resume(returning: settings.authorizationStatus)
            }
        }
    }

    /// Asks for permission. Only called from an explicit user action ("Turn on notifications").
    static func requestAuthorization() async -> Bool {
        await withCheckedContinuation { (cont: CheckedContinuation<Bool, Never>) in
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                cont.resume(returning: granted)
            }
        }
    }

    static func post(id: String, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = "solarpulse.alerts"
        let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}

/// Shows alert banners while the app is in the foreground too.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate, Sendable {
    static let shared = NotificationDelegate()

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
