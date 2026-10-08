package com.solarpulse.core

import com.solarpulse.core.sim.Sim
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Values produced by the web's TypeScript simulator (src/lib) with a frozen clock
 * (2026-06-21 15:30 Asia/Shanghai). Regenerate with android/tools/gen-web-reference.mjs.
 */
object WebReference {
    val root: JsonObject by lazy {
        val text = WebReference::class.java.getResource("/web-reference.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    val now: Long get() = root["now"]!!.jsonPrimitive.long
    val zone: ZoneId get() = ZoneId.of(root["tz"]!!.jsonPrimitive.content)

    /** A simulator frozen at the reference instant. */
    fun sim(): Sim = Sim(zone) { now }

    operator fun get(key: String): JsonElement = root[key]!!
}

val JsonElement.arr: JsonArray get() = jsonArray
val JsonElement.obj: JsonObject get() = jsonObject
val JsonElement.num: Double get() = jsonPrimitive.double
val JsonElement.str: String get() = jsonPrimitive.content
val JsonElement?.strOrNull: String? get() = if (this == null || this is JsonNull) null else (this as JsonPrimitive).content
val JsonElement?.numOrNull: Double? get() = if (this == null || this is JsonNull) null else (this as JsonPrimitive).double

fun assertClose(expected: Double, actual: Double, tol: Double = 1e-6, what: String = "") {
    assertTrue(abs(expected - actual) <= tol, "$what expected <$expected> but was <$actual> (|Δ| = ${abs(expected - actual)})")
}
