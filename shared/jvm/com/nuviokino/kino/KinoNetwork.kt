package com.nuviokino.kino

import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit

internal class KinoNetwork {
    fun connectionAddresses(plugin: KinoPlugin, settings: JsonObject, hostname: String): List<InetAddress> {
        val addresses=Dns.SYSTEM.lookup(hostname)
        val configured=plugin.settings.any { setting ->
            val value=settings[setting.text("key")]
            when(setting.text("type")) {
                "url" -> runCatching { (value as? JsonPrimitive)?.content?.toHttpUrl()?.host==hostname }.getOrDefault(false)
                "list" -> (value as? JsonArray).orEmpty().filterIsInstance<JsonObject>().any { row -> setting.array("fields").filterIsInstance<JsonObject>().any { field -> field.text("type")=="url" && runCatching { row.text(field.text("key")).toHttpUrl().host==hostname }.getOrDefault(false) } }
                else -> false
            }
        }
        require(configured || addresses.all(::publicAddress)) { "host_not_allowed: dirección de red privada" }
        return addresses
    }
    fun permitted(plugin: KinoPlugin, settings: JsonObject, raw: String, stream: Boolean = false, live: Boolean = false): Boolean {
        val url = runCatching { raw.toHttpUrl() }.getOrNull() ?: return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val configured = plugin.settings.flatMap { setting ->
            val value = settings[setting.text("key")]
            when (setting.text("type")) {
                "url" -> listOfNotNull((value as? JsonPrimitive)?.contentOrNull)
                "list" -> (value as? JsonArray).orEmpty().filterIsInstance<JsonObject>().flatMap { row ->
                    setting.array("fields").filterIsInstance<JsonObject>().filter { it.text("type") == "url" }.map { row.text(it.text("key")) }
                }
                else -> emptyList()
            }
        }.mapNotNull { runCatching { it.toHttpUrl() }.getOrNull() }
        if (configured.any { it.scheme == url.scheme && it.host == url.host && it.port == url.port && (url.encodedPath.startsWith(it.encodedPath.trimEnd('/') + "/") || url.encodedPath == it.encodedPath) }) return true
        if (!isPublic(url.host)) return false
        val declared = plugin.manifest.array("hosts").any {
            val name = (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.text("host") ?: ""
            val matches = if (name.startsWith("*.")) url.host.endsWith(name.substring(1)) && url.host != name.substring(2) else url.host == name
            matches && (url.isHttps || (it as? JsonObject)?.flag("insecureHttp") == true)
        }
        if (declared) return true
        if (stream && (plugin.manifest.text("streamHosts") == "any" || live && plugin.manifest.text("liveStreamHosts") == "any")) return true
        return !stream && plugin.manifest.text("fetchHosts") == "any" && url.isHttps && url.port == 443
    }

    private fun isPublic(host: String): Boolean {
        if (!host.contains('.') || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".localhost")) return false
        return runCatching { InetAddress.getAllByName(host).all(::publicAddress) }.getOrDefault(false)
    }

    private fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address
        if (b.size == 4) {
            val first = b[0].toInt() and 255; val second = b[1].toInt() and 255
            if (first == 0 || first >= 224 || first == 100 && second in 64..127 || first == 169 && second == 254 || first == 198 && second in 18..19) return false
        } else if (b.size == 16 && ((b[0].toInt() and 254) == 252 || b.take(12).all { it == 0.toByte() })) return false
        return true
    }

    // Each plugin has its own cookie jar; redirects are handled here, never automatically.
    private val jars = mutableMapOf<String, MutableList<Cookie>>()
    @Synchronized private fun jar(id: String) = jars.getOrPut(id) { mutableListOf() }
    @Synchronized fun clearCookies(id: String) { jars.remove(id) }

    fun fetch(plugin: KinoPlugin?, settings: JsonObject, raw: String, options: JsonObject = JsonObject(emptyMap()), limit: Int = 5_000_000): JsonObject {
        var url = raw.toHttpUrl()
        var method = options.text("method", "GET").uppercase()
        require(method in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")) { "invalid_request: método no permitido" }
        val headers = options.obj("headers").mapValues { it.value.jsonPrimitive.content }
        val bodyValue = options["body"]
        var contentType = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value ?: "text/plain"
        val bytes: ByteArray? = when (bodyValue) {
            is JsonPrimitive -> bodyValue.content.toByteArray()
            is JsonObject -> when {
                "json" in bodyValue -> { contentType = "application/json"; bodyValue["json"].toString().toByteArray() }
                "base64" in bodyValue -> Base64.getDecoder().decode(bodyValue.text("base64"))
                "form" in bodyValue -> { contentType = "application/x-www-form-urlencoded"; bodyValue.obj("form").entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value.jsonPrimitive.content, "UTF-8") }.toByteArray() }
                else -> error("invalid_request: cuerpo desconocido")
            }
            else -> null
        }
        var requestBody = bytes?.toRequestBody(contentType.toMediaType())
        val timeout = options.number("timeoutMs", 15000).coerceIn(1, 30000)
        val maxBytes = options.number("maxBytes", limit.toLong()).coerceIn(1, limit.toLong()).toInt()
        val jar = jar(plugin?.id ?: "__store")
        val cookiesEnabled = options["cookies"] != JsonPrimitive(false)
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).callTimeout(timeout, TimeUnit.MILLISECONDS)
            .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> {
                if(plugin!=null)return connectionAddresses(plugin,settings,hostname)
                val addresses = Dns.SYSTEM.lookup(hostname)
                // Recheck DNS at connection time, including DNS rebinding after URL validation.
                require(addresses.all(::publicAddress)) { "host_not_allowed: dirección de red privada" }
                return addresses
            } }).build()
        try {
            repeat(11) { hop ->
                require(plugin == null && url.isHttps && url.host in setOf("api.github.com", "raw.githubusercontent.com") || plugin != null && permitted(plugin, settings, url.toString())) { "host_not_allowed: ${url.host}" }
                val request = Request.Builder().url(url).header("User-Agent", "Nuvio-Kino/0.1").header("Accept", "application/json, */*")
                headers.forEach { (key, value) -> if (hop == 0 || key.lowercase() !in setOf("authorization", "cookie", "proxy-authorization")) request.header(key, value) }
                if (cookiesEnabled) synchronized(jar) { jar.removeAll { it.expiresAt < System.currentTimeMillis() }; val matching = jar.filter { it.matches(url) }; if (matching.isNotEmpty()) request.header("Cookie", matching.joinToString("; ") { "${it.name}=${it.value}" }) }
                request.method(method, if (method in setOf("POST", "PUT", "PATCH")) requestBody ?: ByteArray(0).toRequestBody() else if (method == "DELETE") requestBody else null)
                client.newCall(request.build()).execute().use { response ->
                    if (cookiesEnabled) synchronized(jar) { Cookie.parseAll(url, response.headers).forEach { cookie -> jar.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }; jar.add(cookie) } }
                    val location = response.header("Location")
                    if (response.code in setOf(301, 302, 303, 307, 308) && location != null && options.text("redirect") != "manual") {
                        require(hop < 10) { "network: demasiadas redirecciones" }
                        url = url.resolve(location) ?: error("network: redirección inválida")
                        if (response.code == 303 || response.code in setOf(301,302) && method == "POST") { method = "GET"; requestBody = null }
                    } else {
                        val body = response.body?.byteStream()?.use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (out.size() <= maxBytes) { val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - out.size())); if (count <= 0) break; out.write(buffer, 0, count) }
                            out.toByteArray()
                        } ?: ByteArray(0)
                        if (body.size > maxBytes && "maxBytes" !in options) error("too_large: respuesta excede el límite")
                        var text = body.take(maxBytes).toByteArray().toString(Charsets.UTF_8)
                        var truncated = body.size > maxBytes
                        val stop = options.text("stopAt")
                        if (stop.isNotEmpty()) { val index = text.indexOf(stop, ignoreCase = true); if (index >= 0 && index + stop.length < text.length) { text = text.substring(0, index + stop.length); truncated = true } }
                        return buildJsonObject {
                            put("status", response.code); put("ok", response.isSuccessful); put("url", url.toString()); put("text", text)
                            put("base64", Base64.getEncoder().encodeToString(body.take(maxBytes).toByteArray())); put("truncated", truncated)
                            put("headers", buildJsonObject { response.headers.names().forEach { put(it.lowercase(), response.headers.values(it).joinToString(", ")) } })
                        }
                    }
                }
            }
            error("network: demasiadas redirecciones")
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
