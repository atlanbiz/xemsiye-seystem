package com.solarpulse.app.data

import com.solarpulse.core.sim.Sim
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.roundToInt

data class Weather(
    val temp: Int,
    val code: Int,
    val irradiance: Int,
    val wind: Int,
    val humidity: Int,
    val live: Boolean,
)

enum class WeatherKind { SUNNY, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

/** WMO weather code → kind (web `weatherKind`). */
fun weatherKind(code: Int): WeatherKind = when {
    code == 0 || code == 1 -> WeatherKind.SUNNY
    code == 2 -> WeatherKind.PARTLY
    code == 3 -> WeatherKind.CLOUDY
    code == 45 || code == 48 -> WeatherKind.FOG
    (code in 71..77) || code == 85 || code == 86 -> WeatherKind.SNOW
    code >= 95 -> WeatherKind.STORM
    else -> WeatherKind.RAIN
}

/** Live weather from Open-Meteo (no API key), falling back to the simulator offline (web `useWeather`). */
class WeatherRepository(private val http: HttpClient, private val sim: Sim) {
    fun simulated(): Weather = sim.simWeather().let { Weather(it.temp, it.code, it.irradiance, it.wind, it.humidity, live = false) }

    suspend fun current(lat: Double, lng: Double): Weather = runCatching {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,shortwave_radiation&timezone=auto",
            lat, lng,
        )
        val response = http.get(url)
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val c = Json.parseToJsonElement(response.bodyAsText()).jsonObject["current"]!!.jsonObject
        fun num(k: String) = c[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
        Weather(
            temp = num("temperature_2m").roundToInt(),
            code = num("weather_code").toInt(),
            irradiance = num("shortwave_radiation").roundToInt(),
            wind = num("wind_speed_10m").roundToInt(),
            humidity = num("relative_humidity_2m").roundToInt(),
            live = true,
        )
    }.getOrElse { simulated() }
}
