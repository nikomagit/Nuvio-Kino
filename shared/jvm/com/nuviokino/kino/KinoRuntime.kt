package com.nuviokino.kino

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class KinoRuntime(private val root: File, private val network: KinoNetwork, private val device: String) {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private fun trace(stage: String) { if(java.lang.Boolean.getBoolean("nuviokino.kino.trace"))System.err.println("KinoRuntime: $stage") }

    suspend fun call(plugin: KinoPlugin, source: String, export: String, args: JsonArray, config: JsonObject): JsonElement =
        locks.getOrPut(plugin.id) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(30_000) {
                    val runtime = QuickJs.create(Dispatchers.IO)
                    trace("created")
                    try {
                        runtime.memoryLimit = 64L * 1024 * 1024
                        runtime.maxStackSize = 512L * 1024
                        // Both Nuvio's patched Android engine and recent Desktop engines implement this.
                        check(runCatching { runtime.javaClass.getMethod("setEvaluationTimeoutMillis", Long::class.javaPrimitiveType).invoke(runtime, 10_000L) }.isSuccess) { "El motor QuickJS no ofrece un límite de ejecución; instala una compilación compatible" }
                        runtime.function("__kino_native") { values ->
                            envelope {
                                val operation=values[0].toString()
                                require(export!="sign" || !operation.startsWith("storage") && operation!="cookies.clear" && !operation.startsWith("catalog")) { "permission_denied: sign no puede acceder a datos persistidos" }
                                native(plugin, config, operation, kinoJson.parseToJsonElement(values[1].toString()))
                            }
                        }
                        var requests = 0
                        runtime.asyncFunction("__kino_async") { values ->
                            suspendEnvelope {
                                val op = values[0].toString(); val value = kinoJson.parseToJsonElement(values[1].toString())
                                require(export!="sign") { "permission_denied: sign no puede acceder a la red ni esperar" }
                                when (op) {
                                    "sleep" -> { delay(value.jsonPrimitive.long.coerceIn(0, 30_000)); JsonNull }
                                    "fetch" -> {
                                        check(++requests <= 80) { "rate_limited: demasiadas solicitudes en una llamada" }
                                        val v = value.jsonObject
                                        network.fetch(plugin, config, v.text("url"), v.obj("options"))
                                    }
                                    else -> error("unavailable: $op")
                                }
                            }
                        }
                        runtime.addModule("kino-rank", resource("kino/rank.js"))
                        runtime.addModule("kino-bootstrap", resource("kino/bootstrap.js"))
                        // A real ES module: no regex rewriting of export statements, no platform imports.
                        runtime.addModule("kino-plugin", source)
                        trace("modules registered")
                        val name = JsonPrimitive(export).toString()
                        val captured = CompletableDeferred<String>()
                        runtime.function("__kino_result") { values -> trace("result captured"); captured.complete(values[0].toString()); null }
                        trace("evaluating")
                        runtime.evaluate<Any?>("""
                            import 'kino-bootstrap';
                            import * as plugin from 'kino-plugin';
                            globalThis.__kino_exports = plugin;
                            (async () => {
                              try {
                                if ($name === '__probe') { __kino_result(JSON.stringify({value: Object.keys(globalThis.__kino_exports).filter(k => typeof globalThis.__kino_exports[k] === 'function')})); return; }
                                const fn = globalThis.__kino_exports[$name];
                                if (typeof fn !== 'function') throw Object.assign(new Error('El plugin no exporta ' + $name), {code:'not_found'});
                                const value = await fn(...$args);
                                __kino_result(JSON.stringify({value: value === undefined ? null : value}));
                              } catch (e) { __kino_result(JSON.stringify({error: String(e.code || 'plugin_error'), message: String(e.userMessage || e.message || e)})); }
                            })()
                        """.trimIndent(),"invoke.mjs",true)
                        trace("evaluation returned")
                        val answer = kinoJson.parseToJsonElement(captured.await()).jsonObject
                        if ("error" in answer) error("${answer.text("error")}: ${answer.text("message")}")
                        answer["value"] ?: JsonNull
                    } finally { runtime.close() }
                } ?: error("timeout: el plugin no respondió en 30 segundos")
            }
        }

    private fun resource(path: String): String = javaClass.classLoader!!.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() } ?: error("Falta recurso $path")

    private fun native(plugin: KinoPlugin, config: JsonObject, op: String, value: JsonElement): JsonElement {
        val storageFile = File(root, "${plugin.id}/storage.json")
        fun read(file: File): JsonObject = if (file.exists()) kinoJson.parseToJsonElement(file.readText()).jsonObject else JsonObject(emptyMap())
        fun write(file: File, obj: JsonObject) = atomicWrite(file, obj.toString().toByteArray())
        fun bytes(data: String, encoding: String): ByteArray = when (encoding) { "hex" -> data.unhex(); "base64" -> Base64.getDecoder().decode(data); else -> data.toByteArray() }
        return when (op) {
            "setup" -> buildJsonObject { put("config", config); put("device", device); put("seed", sha256(root.absolutePath.toByteArray()).take(32)); put("fetchAnyHost", plugin.manifest.text("fetchHosts") == "any") }
            "url" -> {
                val v = value.jsonObject; val url = if (v.text("base").isEmpty()) v.text("value").toHttpUrl() else v.text("base").toHttpUrl().resolve(v.text("value")) ?: error("invalid_request: URL")
                buildJsonObject { put("href",url.toString()); put("protocol",url.scheme+":"); put("host",if (url.port == if(url.isHttps)443 else 80) url.host else "${url.host}:${url.port}"); put("hostname",url.host); put("port",if(url.port==443&&url.isHttps||url.port==80&&!url.isHttps) "" else url.port.toString()); put("pathname",url.encodedPath); put("search",url.encodedQuery?.let { "?$it" } ?: ""); put("hash",url.encodedFragment?.let { "#$it" } ?: ""); put("origin","${url.scheme}://${url.host}${if(url.port in setOf(80,443)) "" else ":${url.port}"}") }
            }
            "encode" -> JsonArray(value.jsonPrimitive.content.toByteArray().map { JsonPrimitive(it.toInt() and 255) })
            "decode" -> JsonPrimitive(value.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray().toString(Charsets.UTF_8))
            "atob" -> JsonPrimitive(Base64.getMimeDecoder().decode(value.jsonPrimitive.content).toString(Charsets.ISO_8859_1))
            "btoa" -> JsonPrimitive(Base64.getEncoder().encodeToString(value.jsonPrimitive.content.toByteArray(Charsets.ISO_8859_1)))
            "storage.keys", "storage.get", "storage.set", "storage.remove", "storage.clear" -> {
                val now = System.currentTimeMillis()
                val stored = read(storageFile).filterValues { it.jsonObject.number("expires", Long.MAX_VALUE) > now }.toMutableMap()
                when (op) {
                    "storage.keys" -> JsonArray(stored.keys.map(::JsonPrimitive))
                    "storage.get" -> stored[value.jsonPrimitive.content]?.jsonObject?.get("value") ?: JsonNull
                    "storage.set" -> {
                        val v = value.jsonObject; require(v.text("key").length <= 200) { "quota_exceeded: clave demasiado larga" }
                        stored[v.text("key")] = buildJsonObject { put("value",v.text("value")); put("expires",if(v.number("ttlMs")>0) now+v.number("ttlMs") else Long.MAX_VALUE) }
                        val output = JsonObject(stored); val large = "storageLarge" in plugin.capabilities
                        require(stored.size <= if(large)1000 else 200) { "quota_exceeded: demasiadas claves" }
                        require(output.toString().toByteArray().size <= if(large)16*1024*1024 else 256*1024) { "quota_exceeded: almacenamiento lleno" }
                        write(storageFile,output); JsonNull
                    }
                    else -> { if(op=="storage.clear") stored.clear() else stored.remove(value.jsonPrimitive.content); write(storageFile,JsonObject(stored)); JsonNull }
                }
            }
            "cookies.clear" -> { network.clearCookies(plugin.id); JsonNull }
            "crypto.uuid" -> JsonPrimitive(UUID.randomUUID().toString())
            "crypto.random" -> JsonPrimitive(ByteArray(value.jsonPrimitive.int.coerceIn(1,4096)).also { SecureRandom().nextBytes(it) }.hex())
            "crypto.hash", "crypto.hmac" -> {
                val v=value.jsonObject; val algorithm=v.text("algorithm").lowercase().replace("-", "")
                require(algorithm in setOf("sha1","sha256","sha512","md5")) { "invalid_request: algoritmo" }
                val data=bytes(v.text("data"),v.obj("options").text("inputEncoding","utf8"))
                val hash=if(op=="crypto.hash") MessageDigest.getInstance(algorithm).digest(data) else Mac.getInstance("Hmac"+algorithm).run { init(SecretKeySpec(bytes(v.text("key"),v.obj("options").text("keyEncoding","utf8")),this.algorithm)); doFinal(data) }
                JsonPrimitive(if(v.obj("options").text("outputEncoding")=="base64") Base64.getEncoder().encodeToString(hash) else hash.hex())
            }
            "crypto.convert" -> { val v=value.jsonObject; val raw=bytes(v.text("data"),v.text("from")); JsonPrimitive(when(v.text("to")){"hex"->raw.hex();"base64"->Base64.getEncoder().encodeToString(raw);else->raw.toString(Charsets.UTF_8)}) }
            "crypto.gunzip" -> error("unavailable: gunzip")
            "catalog.get", "catalog.search", "catalog.upsert", "catalog.remove", "catalog.count", "catalog.clear" -> {
                require("catalog" in plugin.capabilities) { "permission_denied: catalog" }
                val file = File(root,"${plugin.id}/catalog.json"); val rows=read(file).toMutableMap()
                when(op) {
                    "catalog.get" -> rows[value.jsonPrimitive.content] ?: JsonNull
                    "catalog.count" -> JsonPrimitive(rows.size)
                    "catalog.search" -> { val q=if(value is JsonObject)value.text("q") else value.jsonPrimitive.content; buildJsonObject { put("items",JsonArray(rows.values.filter { it.jsonObject.text("title").contains(q,true) }.take(100))) } }
                    "catalog.upsert" -> { value.jsonArray.filterIsInstance<JsonObject>().forEach { rows[it.text("id")] = it }; require(rows.size<=10000){"quota_exceeded: catálogo"}; write(file,JsonObject(rows)); JsonNull }
                    "catalog.clear" -> { write(file,JsonObject(emptyMap())); JsonNull }
                    else -> { value.jsonArray.forEach { rows.remove(it.jsonPrimitive.content) }; write(file,JsonObject(rows)); JsonNull }
                }
            }
            else -> error("unavailable: $op")
        }
    }

    private fun envelope(block: () -> JsonElement): String = try { buildJsonObject { put("value",block()) }.toString() } catch(e: Exception) { failure(e) }
    private suspend fun suspendEnvelope(block: suspend () -> JsonElement): String = try { val v=block(); buildJsonObject { put("value",v) }.toString() } catch(e: CancellationException) { throw e } catch(e: Exception) { failure(e) }
    private fun failure(e: Exception) = buildJsonObject { put("error",e.message?.substringBefore(':')?.takeIf { it.matches(Regex("[a-z_]+")) } ?: "host_error"); put("message",e.message ?: "Error del host Kino") }.toString()
}
