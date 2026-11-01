package com.nuviokino.kino

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

val kinoJson = Json { ignoreUnknownKeys = true; prettyPrint = false }
fun JsonObject.text(key: String, fallback: String = ""): String = (get(key) as? JsonPrimitive)?.contentOrNull ?: fallback
fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true
fun JsonObject.number(key: String, fallback: Long = 0): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: fallback

data class KinoStoreEntry(
    val address: String, val name: String, val description: String = "",
    val kind: String = "kino", val categories: List<String> = emptyList(),
    val authorKey: String? = null,
)

data class KinoPlugin(val manifest: JsonObject, val address: String, val revision: String) {
    val id get() = manifest.text("id")
    val name get() = manifest.text("name", id)
    val version get() = manifest.text("version")
    val capabilities get() = manifest.array("capabilities").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    val settings get() = manifest.array("settings").filterIsInstance<JsonObject>()
    val limitations get() = capabilities.intersect(setOf("syncProvider", "externalResolve", "byteSource", "socket", "room", "ai", "identity", "coins", "schedule", "playerControl", "playerPanelTab", "notifications", "imageSource", "tracking", "meta", "subtitles", "segments", "download", "playlists", "nuvio", "drm")) + if(manifest.array("appThemes").isNotEmpty())setOf("themes")else emptySet()
}

data class KinoChannel(
    val pluginId: String, val id: String, val title: String,
    val category: String, val ref: String? = null, val stream: JsonObject? = null,
    val logo: String? = null, val number: Long = 0, val adult: Boolean = false,
    val guideId: String = id,
) { val key get() = "$pluginId:$id" }

data class KinoCategory(val pluginId: String, val id: String, val title: String, val playlist: JsonObject? = null)
data class KinoChannelPage(val channels: List<KinoChannel>, val next: String? = null)
data class KinoProgramme(val channelId: String, val title: String, val start: Long, val end: Long, val description: String = "", val pluginId: String = "")
data class KinoPlayback(val pluginId: String, val title: String, val stream: JsonObject, val live: Boolean, val ref: String? = null)

interface KinoRepository {
    val plugins: StateFlow<List<KinoPlugin>>
    val favorites: StateFlow<Set<String>>
    suspend fun store(): List<KinoStoreEntry>
    suspend fun inspect(entry: KinoStoreEntry): KinoPlugin
    suspend fun install(entry: KinoStoreEntry): KinoPlugin
    suspend fun uninstall(id: String)
    suspend fun call(id: String, export: String, args: JsonArray = JsonArray(emptyList())): JsonElement
    suspend fun settings(id: String): JsonObject
    suspend fun saveSettings(id: String, values: JsonObject)
    suspend fun action(id: String, key: String): JsonElement
    suspend fun categories(): List<KinoCategory>
    suspend fun channels(category: KinoCategory, cursor: String? = null): KinoChannelPage
    suspend fun searchChannels(query: String): List<KinoChannel>
    suspend fun guide(channels: List<KinoChannel>, from: Long, to: Long): List<KinoProgramme>
    suspend fun play(channel: KinoChannel): KinoPlayback
    suspend fun resolve(id: String, ref: String, title: String, live: Boolean = false): KinoPlayback
    suspend fun favorite(channel: KinoChannel)
}

object KinoHost {
    var repository: KinoRepository? = null
    var device: String = "phone"
}

object KinoNavigation {
    var open: ((String) -> Unit)? = null
}
