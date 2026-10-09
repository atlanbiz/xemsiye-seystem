package com.solarpulse.core.data

import com.solarpulse.core.model.Database
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One JSON configuration for PostgREST rows, the demo-mode file and backups. */
object SolarJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // null / unknown enum value → property default
        explicitNulls = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(db: Database): String = json.encodeToString(Database.serializer(), db)

    /** Decodes a saved database and reports whether it already had the `alert_rules` key. */
    fun decode(text: String): Pair<Database, Boolean> {
        val obj: JsonObject = json.parseToJsonElement(text).jsonObject
        return json.decodeFromJsonElement(Database.serializer(), obj) to obj.containsKey("alert_rules")
    }
}
