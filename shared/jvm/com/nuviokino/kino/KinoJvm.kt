package com.nuviokino.kino

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File

object KinoJvm {
    @JvmStatic fun initialize(root: File, device: String) {
        if (KinoHost.repository != null) return
        KinoHost.device = device
        KinoHost.repository = KinoJvmRepository(root, device)
    }
}

class KinoJvmRepository(private val root: File, device: String = "phone") : KinoRepository {
    private val network = KinoNetwork()
    private val runtime = KinoRuntime(root, network, device)
    private val media by lazy { KinoMediaProxy(network) }
    private val lock = Mutex()
    private val installed = MutableStateFlow<List<KinoPlugin>>(emptyList())
    private val starred = MutableStateFlow<Set<String>>(emptySet())
    override val plugins = installed.asStateFlow()
    override val favorites = starred.asStateFlow()
    private data class Prepared(val plugin: KinoPlugin, val code: ByteArray)
    private val prepared = java.util.concurrent.ConcurrentHashMap<String, Prepared>()
    private val playlistChannels = java.util.concurrent.ConcurrentHashMap<String, List<KinoChannel>>()
    private val playlistGuides = java.util.concurrent.ConcurrentHashMap<String, List<KinoProgramme>>()
    init {
        root.mkdirs()
        installed.value = root.listFiles().orEmpty().filter { it.isDirectory && Regex("[a-z0-9][a-z0-9-]{1,39}").matches(it.name) }.mapNotNull { dir ->
            runCatching { val metadata=readObject(File(dir,"installed.json")); KinoPlugin(metadata.obj("manifest").ifEmpty { readObject(File(dir,"kino-plugin.json")) }, metadata.text("address"), metadata.text("revision")) }.getOrNull()
        }
        starred.value = runCatching { kinoJson.parseToJsonElement(File(root,"favorites.json").readText()).jsonArray.map { it.jsonPrimitive.content }.toSet() }.getOrDefault(emptySet())
    }
    private fun readObject(file: File) = kinoJson.parseToJsonElement(file.readText()).jsonObject
    private fun resource(path: String) = javaClass.classLoader!!.getResourceAsStream("kino/$path")!!.bufferedReader().use { it.readText() }
    private fun remote(url: String, limit: Int = 2_000_000): String {
        val answer = network.fetch(null,JsonObject(emptyMap()),url,limit=limit)
        require(answer.flag("ok")) { "HTTP ${answer.number("status")}: $url" }
        return answer.text("text")
    }

    override suspend fun store(): List<KinoStoreEntry> = withContext(Dispatchers.IO) {
        val base="https://raw.githubusercontent.com/kinotvapp/kino-plugin-store/HEAD/"
        val entries=linkedMapOf<String,KinoStoreEntry>()
        fun add(obj: JsonObject) {
            val address=obj.text("address",obj.text("repo",obj.text("stremio")))
            if(address.isEmpty()) return
            val kind=if("stremio" in obj || obj.text("type")=="stremio" || address.startsWith("https://")) "stremio" else "kino"
            entries[address]=KinoStoreEntry(address,obj.text("name",obj.text("id",address.substringAfterLast('/'))),obj.text("description"),kind,obj.array("categories").map { it.jsonPrimitive.content },obj.text("authorKey").ifEmpty { null })
        }
        for((file,key) in listOf("index.json" to "entries","catalog.json" to "plugins")) {
            val content=try { val text=remote(base+file); atomicWrite(File(root,"store-$file"),text.toByteArray()); text }
                catch(e: CancellationException) { throw e } catch(_: Exception) { File(root,"store-$file").takeIf { it.exists() }?.readText() ?: resource(file) }
            kinoJson.parseToJsonElement(content).jsonObject.array(key).filterIsInstance<JsonObject>().forEach(::add)
        }
        // Includes authors' utilities/themes not present in the older curated catalog.
        kinoJson.parseToJsonElement(resource("official.json")).jsonArray.filterIsInstance<JsonObject>().forEach { if(it.text("address") !in entries) add(it) }
        try {
            val repos=kinoJson.parseToJsonElement(remote("https://api.github.com/users/kinotvapp/repos?per_page=100")).jsonArray
            repos.filterIsInstance<JsonObject>().filter { it.text("name").startsWith("kino-plugin-") || it.text("name").startsWith("kino-theme") || it.text("name")=="kino-social" }.filterNot { it.text("name")=="kino-plugin-sdk" }.forEach { repo ->
                val address=repo.text("full_name")
                if(entries.keys.none { it==address || it.startsWith("$address/") })add(buildJsonObject { put("address",address); put("name",repo.text("name")); put("description",repo.text("description")) })
            }
        } catch(e: CancellationException) { throw e } catch(_: Exception) { /* The shipped author inventory remains available offline. */ }
        for(p in installed.value.filter { "pluginStore" in it.capabilities }) {
            try {
                val response=call(p.id,"storeSync",JsonArray(listOf(buildJsonObject { put("reason","manual"); put("since",JsonNull); put("state",JsonNull) }))).jsonObject
                response.array("entries").filterIsInstance<JsonObject>().take(2000).forEach(::add)
            } catch(e: CancellationException) { throw e } catch(_: Exception) { /* One third-party store cannot hide the other sources. */ }
        }
        entries.values.sortedWith(compareBy<KinoStoreEntry> { !it.address.startsWith("kinotvapp/") }.thenBy { it.name.lowercase() })
    }

    private fun normalizeAddress(raw: String): String {
        val address=raw.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        val parts=address.split('/')
        require(parts.size>=2 && parts.all { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) && it !in setOf(".","..") }) { "Usa owner/repositorio o owner/repositorio/carpeta" }
        return address
    }
    override suspend fun inspect(entry: KinoStoreEntry): KinoPlugin = withContext(Dispatchers.IO) {
        require(entry.kind=="kino") { "Este addon usa el instalador Stremio de Nuvio" }
        val address=normalizeAddress(entry.address)
        val repo=address.split('/').take(2).joinToString("/")
        val folder=address.split('/').drop(2).joinToString("/").let { if(it.isEmpty()) "" else "$it/" }
        val commit=kinoJson.parseToJsonElement(remote("https://api.github.com/repos/$repo/commits/HEAD")).jsonObject.text("sha")
        require(commit.matches(Regex("[a-f0-9]{40}"))) { "GitHub no devolvió un commit válido" }
        val base="https://raw.githubusercontent.com/$repo/$commit/$folder"
        val manifest=kinoJson.parseToJsonElement(remote(base+"kino-plugin.json",16_384)).jsonObject
        validateManifest(manifest)
        val code=remote(base+manifest.text("entry"),1_048_576).toByteArray()
        inspectSnapshot(address,commit,manifest,code,entry.authorKey)
    }

    internal suspend fun inspectSnapshot(address: String, commit: String, manifest: JsonObject, code: ByteArray, authorKey: String? = null): KinoPlugin {
        validateManifest(manifest)
        require(code.size<=1_048_576) { "Entry demasiado grande" }
        val plugin=KinoPlugin(manifest,address,commit)
        verifyPluginSignature(plugin,code,authorKey)
        val previous=installed.value.firstOrNull { it.id==plugin.id }
        if(previous!=null) {
            require(previous.address.equals(address,true)) { "Ya hay otro plugin con el id ${plugin.id}" }
            previous.manifest.obj("signature").text("authorKey").ifEmpty { null }?.let { verifyPluginSignature(plugin,code,it) }
        }
        val exports=runtime.call(plugin,code.toString(Charsets.UTF_8),"__probe",JsonArray(emptyList()),defaults(plugin)).jsonArray.map { it.jsonPrimitive.content }.toSet()
        val required=plugin.capabilities.flatMap { when(it) { "channels"->listOf("liveCategories","liveChannels");"tracking"->listOf("track");"syncProvider"->listOf("syncPull","syncPush");"byteSource"->listOf("read");"imageSource"->listOf("image");"screen"->listOf("screen","screenEvent");"pluginStore"->listOf("storeSync");"schedule"->listOf("job");"playerPanelTab"->listOf("panel");else->if(it in setOf("search","home","browse","episodes","resolve","meta","subtitles","segments","migrate"))listOf(it)else emptyList() } }
        require(exports.containsAll(required)) { "Faltan exports: ${(required-exports).joinToString()}" }
        prepared[address]=Prepared(plugin,code)
        return plugin
    }

    private fun validateManifest(m: JsonObject) {
        require(m.text("id").matches(Regex("[a-z0-9][a-z0-9-]{1,39}")) && m.text("id") !in setOf("live","local","unknown","plugin","own","kino")) { "ID de plugin inválido" }
        require(m.text("name").length in 1..40 && m.text("version").matches(Regex("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})"))) { "Nombre o versión inválidos" }
        require(m.number("apiVersion") in 1..17) { "Versión de API Kino no soportada: ${m.number("apiVersion")}" }
        require(m.text("entry").length in 1..200 && m.text("entry").split('/').all { it.matches(Regex("[A-Za-z0-9._-]+")) && it !in setOf(".","..") }) { "Ruta de entry inválida" }
        require(m["hosts"] is JsonArray) { "Falta hosts en el manifiesto" }
    }

    override suspend fun install(entry: KinoStoreEntry): KinoPlugin {
        val address=normalizeAddress(entry.address)
        val candidate=prepared.remove(address) ?: run { inspect(entry); prepared.remove(address)!! }
        return lock.withLock { withContext(Dispatchers.IO) {
            val plugin=candidate.plugin; val dir=File(root,plugin.id); dir.mkdirs()
            // Immutable entry first, then one atomic commit marker. A failed update keeps the old version.
            val digest=sha256(candidate.code)
            atomicWrite(File(dir,"entries/$digest.js"),candidate.code)
            atomicWrite(File(dir,"installed.json"),buildJsonObject { put("address",plugin.address); put("revision",plugin.revision); put("sha256",digest); put("entryFile","$digest.js"); put("manifest",plugin.manifest) }.toString().toByteArray())
            installed.value=installed.value.filterNot { it.id==plugin.id }+plugin
            plugin
        } }
    }

    override suspend fun uninstall(id: String) = lock.withLock { withContext(Dispatchers.IO) {
        val plugin=installed.value.firstOrNull { it.id==id } ?: return@withContext
        val target=File(root,plugin.id).canonicalFile
        require(target.parentFile==root.canonicalFile)
        check(target.deleteRecursively()) { "No se pudo desinstalar" }
        installed.value=installed.value.filterNot { it.id==id }
        starred.value=starred.value.filterNot { it.startsWith("$id:") }.toSet(); saveFavorites()
        network.clearCookies(id)
        Unit
    } }
    private fun plugin(id: String)=installed.value.firstOrNull { it.id==id } ?: error("Plugin no instalado: $id")
    private fun defaults(plugin: KinoPlugin)=buildJsonObject { plugin.settings.forEach { setting -> setting["default"]?.let { put(setting.text("key"),it) } } }
    override suspend fun settings(id: String): JsonObject = withContext(Dispatchers.IO) {
        val plugin=plugin(id); val file=File(root,"$id/settings.json")
        JsonObject(defaults(plugin)+(if(file.exists()) readObject(file) else emptyMap()))
    }
    override suspend fun saveSettings(id: String, values: JsonObject) {
        val plugin=plugin(id)
        plugin.settings.filter { it.flag("required") && it.text("type") in setOf("text","url","password") }.forEach { require(values.text(it.text("key")).isNotBlank()) { "Falta ${it.text("label",it.text("key"))}" } }
        val verdict=runCatching { call(id,"validateSettings",JsonArray(listOf(values))) }.getOrElse { if(it.message?.startsWith("not_found:")==true) JsonNull else throw it }
        if(verdict is JsonObject) require(verdict["ok"] != JsonPrimitive(false) && verdict["valid"] != JsonPrimitive(false) && verdict.obj("errors").isEmpty()) { verdict.text("message",verdict.toString()) }
        withContext(Dispatchers.IO) { atomicWrite(File(root,"$id/settings.json"),values.toString().toByteArray()) }
    }
    override suspend fun call(id: String, export: String, args: JsonArray): JsonElement {
        val p=plugin(id)
        return withContext(Dispatchers.IO) {
            val dir=File(root,id); val metadata=readObject(File(dir,"installed.json"))
            val entryName=metadata.text("entryFile")
            require(entryName.isEmpty() || entryName.matches(Regex("[a-f0-9]{64}\\.js"))) { "Entry instalado inválido" }
            val code=File(dir,if(entryName.isEmpty())"plugin.js" else "entries/$entryName").readBytes()
            require(sha256(code)==metadata.text("sha256")) { "El código instalado fue modificado" }
            runtime.call(p,code.toString(Charsets.UTF_8),export,args,settings(id))
        }
    }
    override suspend fun action(id: String, key: String): JsonElement {
        val p=plugin(id)
        require(p.settings.any { it.text("key")==key && it.text("type")=="action" }) { "Acción no declarada" }
        val answer=call(id,"action",JsonArray(listOf(JsonPrimitive(key))))
        if(answer is JsonObject && (answer.obj("setSettings").isNotEmpty() || answer.array("clearSettings").isNotEmpty())) {
            val allowed=p.settings.filter { it.text("type") in setOf("text","url","password","toggle","select","list") }.map { it.text("key") }.toSet()
            val values=settings(id).toMutableMap()
            answer.array("clearSettings").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it in allowed }.forEach(values::remove)
            answer.obj("setSettings").filterKeys { it in allowed }.forEach { (k,v) -> values[k]=v }
            withContext(Dispatchers.IO) { atomicWrite(File(root,"$id/settings.json"),JsonObject(values).toString().toByteArray()) }
        }
        return answer
    }

    override suspend fun categories(): List<KinoCategory> = withContext(Dispatchers.IO) {
        val result=mutableListOf<KinoCategory>()
        val errors=mutableListOf<String>()
        installed.value.filter { "channels" in it.capabilities }.forEach { p ->
            try {
                val answer=call(p.id,"liveCategories",JsonArray(listOf(JsonNull)))
                val rows=if(answer is JsonArray)answer else JsonArray(listOf(answer))
                for((index,value) in rows.withIndex()) {
                    val row=value as? JsonObject ?: continue
                    if("playlist" in row) {
                        val list=row.obj("playlist"); val key="${p.id}:playlist-$index"
                        val fetch=network.fetch(p,settings(p.id),list.text("url"),buildJsonObject { put("headers",list.obj("headers")) })
                        require(fetch.flag("ok")){"No se pudo descargar la lista M3U"}
                        val channels=KinoPlaylists.m3u(fetch.text("text"),p.id,list,fetch.text("url"))
                        playlistChannels[key]=channels
                        val epg=list.obj("epg").text("url")
                        if(epg.isNotBlank()) runCatching {
                            val xml=network.fetch(p,settings(p.id),epg,buildJsonObject { put("headers",list.obj("headers")) }); require(xml.flag("ok"))
                            val bytes=java.util.Base64.getDecoder().decode(xml.text("base64"))
                            val text=if(bytes.size>=2 && bytes[0]==0x1f.toByte() && bytes[1]==0x8b.toByte())java.util.zip.GZIPInputStream(bytes.inputStream()).use { input ->
                                val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
                                while(true) { val n=input.read(chunk); if(n<0)break; require(out.size()+n<=20_000_000){"Guía demasiado grande"}; out.write(chunk,0,n) }; out.toString("UTF-8")
                            } else bytes.toString(Charsets.UTF_8)
                            playlistGuides[key]=KinoPlaylists.xmltv(text).map { it.copy(pluginId=p.id) }
                        }
                        channels.map { it.category }.distinct().forEach { group -> result.add(KinoCategory(p.id,"playlist-$index:$group",group,list)) }
                    } else if(row.text("id").isNotBlank()) result.add(KinoCategory(p.id,row.text("id"),row.text("title",p.name)))
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { errors.add("${p.name}: ${e.message}") }
        }
        if(result.isEmpty() && errors.isNotEmpty()) error(errors.joinToString("\n"))
        result
    }
    override suspend fun channels(category: KinoCategory, cursor: String?): KinoChannelPage {
        if(category.playlist!=null) {
            val index=category.id.substringBefore(':'); val group=category.id.substringAfter(':')
            return KinoChannelPage(playlistChannels["${category.pluginId}:$index"].orEmpty().filter { it.category==group })
        }
        val answer=call(category.pluginId,"liveChannels",JsonArray(listOf(buildJsonObject { put("categoryId",category.id); put("cursor",cursor?.let(::JsonPrimitive) ?: JsonNull) })))
        val rows=if(answer is JsonArray) answer else answer.jsonObject.array("items")
        return KinoChannelPage(rows.filterIsInstance<JsonObject>().mapNotNull { row ->
            if(row.text("id").isEmpty() || row.text("title").isEmpty() || row.text("ref").isEmpty() && row.obj("stream").isEmpty()) null else KinoChannel(category.pluginId,row.text("id"),row.text("title"),row.text("categoryId",category.id),row.text("ref").ifEmpty { null },(row["stream"] as? JsonObject),row.text("logo").ifEmpty { null },row.number("number"),row.flag("adult"))
        },(answer as? JsonObject)?.text("next")?.ifEmpty { null })
    }
    override suspend fun guide(channels: List<KinoChannel>, from: Long, to: Long): List<KinoProgramme> {
        val result=mutableListOf<KinoProgramme>()
        for((id,group) in channels.groupBy { it.pluginId }) {
            val wanted=group.map { it.guideId }.toSet()
            result+=playlistGuides.filterKeys { it.startsWith("$id:") }.values.flatten().filter { it.channelId in wanted && it.start<to && it.end>from }
            for(chunk in group.chunked(50)) {
                try {
                    val answer=call(id,"guide",JsonArray(listOf(buildJsonObject { put("channelIds",JsonArray(chunk.map { JsonPrimitive(it.id) })); put("from",from); put("to",minOf(to,from+86_400_000)) })))
                    result+=answer.jsonArray.filterIsInstance<JsonObject>().map { KinoProgramme(it.text("channelId"),it.text("title"),it.number("start"),it.number("end"),it.text("description"),id) }
                } catch(e: CancellationException) { throw e } catch(_: Exception) { /* EPG is optional; a channel still plays without it. */ }
            }
        }
        return result.distinctBy { listOf(it.pluginId,it.channelId,it.start,it.end,it.title) }.sortedBy { it.start }
    }
    override suspend fun searchChannels(query: String): List<KinoChannel> {
        require(query.trim().length>=2) { "Escribe al menos dos caracteres" }
        val results=mutableListOf<KinoChannel>()
        var fallbackCategories: List<KinoCategory>?=null
        for(p in installed.value.filter { "channels" in it.capabilities }) {
            try {
                val answer=call(p.id,"liveSearch",JsonArray(listOf(buildJsonObject { put("query",query.trim()) })))
                val rows=if(answer is JsonArray)answer else answer.jsonObject.array("items")
                results+=rows.filterIsInstance<JsonObject>().filter { it.text("id").isNotBlank() && (it.text("ref").isNotBlank() || it.obj("stream").isNotEmpty()) }.map { row -> KinoChannel(p.id,row.text("id"),row.text("title"),row.text("categoryId"),row.text("ref").ifEmpty { null },row["stream"] as? JsonObject,row.text("logo").ifEmpty { null },row.number("number"),row.flag("adult")) }
            } catch(e: CancellationException) { throw e } catch(_: Exception) {
                if(fallbackCategories==null)fallbackCategories=categories()
                for(category in fallbackCategories.orEmpty().filter { it.pluginId==p.id }) {
                    var cursor: String?=null
                    val seen=mutableSetOf<String>()
                    for(pageIndex in 0..19) {
                        val page=channels(category,cursor)
                        results+=page.channels.filter { it.title.contains(query,true) }
                        cursor=page.next ?: break
                        if(!seen.add(cursor))break
                    }
                }
            }
        }
        return results.distinctBy { it.key }
    }
    private suspend fun playback(id: String, title: String, stream: JsonObject, live: Boolean, ref: String?): KinoPlayback {
        require(stream.text("url").isNotEmpty()) { "Este stream necesita un resolvedor externo o byteSource, aún no disponible" }
        require(network.permitted(plugin(id),settings(id),stream.text("url"),stream=true,live=live)) { "host_not_allowed: servidor de reproducción no declarado" }
        require(stream.obj("drm").isEmpty()) { "El puente DRM Kino aún no está conectado al reproductor" }
        val proxied=media.register(plugin(id),settings(id),stream,live) { url ->
            call(id,"sign",JsonArray(listOf(buildJsonObject { put("url",url); put("context",stream["signContext"] ?: JsonNull) }))).jsonObject
        }
        return KinoPlayback(id,title,proxied,live,ref)
    }
    override suspend fun play(channel: KinoChannel): KinoPlayback = if(channel.stream!=null) {
        try { playback(channel.pluginId,channel.title,channel.stream,true,channel.ref) }
        catch(e: Exception) { if(channel.ref!=null) resolve(channel.pluginId,channel.ref,channel.title,true) else throw e }
    } else resolve(channel.pluginId,channel.ref ?: error("Canal sin stream ni ref"),channel.title,true)
    override suspend fun resolve(id: String, ref: String, title: String, live: Boolean): KinoPlayback = playback(id,title,call(id,"resolve",JsonArray(listOf(JsonPrimitive(ref)))).jsonObject,live,ref)
    override suspend fun favorite(channel: KinoChannel) = lock.withLock { withContext(Dispatchers.IO) {
        starred.value=if(channel.key in starred.value)starred.value-channel.key else starred.value+channel.key
        saveFavorites()
    } }
    private fun saveFavorites() = atomicWrite(File(root,"favorites.json"),JsonArray(starred.value.map(::JsonPrimitive)).toString().toByteArray())
}
