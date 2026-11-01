package com.nuviokino.kino

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun KinoButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.onFocusChanged { focused=it.isFocused }.then(if(focused)Modifier.border(3.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(24.dp))else Modifier)) { Text(text) }
}

@Composable
fun KinoHub(
    initialSection: String = "store", onBack: () -> Unit,
    onPlay: (KinoPlayback) -> Unit,
    onInstallStremio: suspend (String) -> String,
) {
    MaterialTheme(colorScheme=if(KinoHost.device=="tv")darkColorScheme()else MaterialTheme.colorScheme) {
        KinoHubContent(initialSection,onBack,onPlay,onInstallStremio)
    }
}

@Composable
private fun KinoHubContent(
    initialSection: String, onBack: () -> Unit,
    onPlay: (KinoPlayback) -> Unit,
    onInstallStremio: suspend (String) -> String,
) {
    val repository=KinoHost.repository
    if(repository==null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Nuvio-Kino"); Text("Los complementos Kino requieren la distribución full."); KinoButton("Volver",onClick=onBack) }
        return
    }
    var section by rememberSaveable { mutableStateOf(initialSection) }
    val plugins by repository.plugins.collectAsState()
    var selected by remember { mutableStateOf<KinoPlugin?>(null) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=12.dp).statusBarsPadding().navigationBarsPadding()) {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { KinoButton(if(selected==null)"Volver" else "Cerrar plugin") { if(selected!=null)selected=null else onBack() } }
                item { KinoButton("Tienda de addons") { selected=null; section="store" } }
                item { KinoButton("TV en vivo") { selected=null; section="live" } }
                item { KinoButton("Mis plugins (${plugins.size})") { selected=null; section="plugins" } }
            }
            Spacer(Modifier.height(12.dp))
            when {
                selected!=null -> KinoPluginPage(repository,selected!!,onPlay,{ selected=null })
                section=="live" -> KinoLivePage(repository,onPlay)
                section=="plugins" -> LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    item { Text("Mis plugins",style=MaterialTheme.typography.headlineSmall) }
                    if(plugins.isEmpty()) item { Text("Instala una fuente desde la tienda para empezar.") }
                    items(plugins,key={it.id}) { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(p.name,style=MaterialTheme.typography.titleMedium); Text("${p.version} · API ${p.manifest.number("apiVersion")}"); KinoButton("Abrir y configurar") { selected=p } } } }
                }
                else -> KinoStorePage(repository,onInstallStremio) { selected=it }
            }
        }
    }
}

@Composable
private fun KinoStorePage(repository: KinoRepository, onStremio: suspend (String)->String, onInstalled: (KinoPlugin)->Unit) {
    var entries by remember { mutableStateOf<List<KinoStoreEntry>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var inspected by remember { mutableStateOf<Pair<KinoStoreEntry,KinoPlugin>?>(null) }
    val scope=rememberCoroutineScope()
    val installed by repository.plugins.collectAsState()
    LaunchedEffect(refresh) {
        busy=true
        try { entries=repository.store(); message="" } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() }
        finally { busy=false }
    }
    fun inspect(entry: KinoStoreEntry) {
        scope.launch { busy=true; message=""
            try { if(entry.kind=="stremio")message=onStremio(entry.address) else inspected=entry to repository.inspect(entry) }
            catch(e: CancellationException) { throw e } catch(e: Exception) { message="${entry.name}: ${e.message}" } finally { busy=false }
        }
    }
    Column {
        Text("Tienda de addons",style=MaterialTheme.typography.headlineSmall)
        Text("Catálogo de Kino, comunidad y complementos Stremio",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(query,{query=it},label={Text("Buscar complementos")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item { KinoButton(if(category.isEmpty())"✓ Todos" else "Todos") { category="" } }
            items(listOf("live","movies","series","subtitles","utilities","themes")) { c -> KinoButton(if(category==c)"✓ $c" else c) { category=c } }
            item { KinoButton("Actualizar",!busy) { refresh++ } }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.error)
        val filtered=entries.filter { (category.isEmpty() || category in it.categories) && (it.name+" "+it.description+" "+it.address).contains(query,true) }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            item { Text("${filtered.size} complementos") }
            if(query.contains('/') && entries.none { it.address==query }) item { KinoButton("Instalar desde GitHub: $query",!busy) { inspect(KinoStoreEntry(query,query)) } }
            items(filtered,key={it.address}) { entry ->
                val current=installed.firstOrNull { it.address==entry.address }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(entry.name,style=MaterialTheme.typography.titleMedium)
                    Text(entry.description)
                    Text(if(current!=null)"Instalado · ${current.version}" else if(entry.kind=="stremio")"Addon Stremio" else "Plugin Kino",style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        KinoButton(if(current!=null)"Buscar actualización" else "Instalar",!busy) { inspect(entry) }
                        if(current!=null)KinoButton("Abrir") { onInstalled(current) }
                    }
                } }
            }
        }
    }
    inspected?.let { (entry,plugin) ->
        AlertDialog(onDismissRequest={if(!busy)inspected=null},title={Text(plugin.name)},text={
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item { Text("${plugin.version} · API ${plugin.manifest.number("apiVersion")}"); Text(plugin.manifest.text("description")); Text("Capacidades: ${plugin.capabilities.joinToString()}") }
                item { Text("Servidores: ${plugin.manifest.array("hosts")}") }
                if(plugin.manifest.text("fetchHosts")=="any")item { Text("Puede conectarse a cualquier servidor público HTTPS.") }
                if(plugin.manifest.text("streamHosts")=="any" || plugin.manifest.text("liveStreamHosts")=="any")item { Text("Puede reproducir desde los servidores públicos de su lista.") }
                item { Text(if(plugin.manifest.obj("signature").isEmpty())"Sin firma de autor" else "Firma del autor verificada") }
                if(plugin.limitations.isNotEmpty())item { Text("Integraciones pendientes en este fork: ${plugin.limitations.joinToString()}. Su instalación no habilita estas funciones.",color=MaterialTheme.colorScheme.error) }
            }
        },confirmButton={ KinoButton(if(busy)"Instalando…" else "Instalar esta versión",!busy) {
            scope.launch { busy=true
                try { val p=repository.install(entry); inspected=null; onInstalled(p) }
                catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty(); inspected=null } finally { busy=false }
            }
        } },dismissButton={KinoButton("Cancelar",!busy) { inspected=null }})
    }
}

@Composable
private fun KinoPluginPage(repository: KinoRepository, plugin: KinoPlugin, onPlay: (KinoPlayback)->Unit, onUninstalled: ()->Unit) {
    val scope=rememberCoroutineScope()
    var config by remember(plugin.id) { mutableStateOf(JsonObject(emptyMap())) }
    var message by remember(plugin.id) { mutableStateOf("") }
    var status by remember(plugin.id) { mutableStateOf(JsonObject(emptyMap())) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable(plugin.id) { mutableStateOf("") }
    var result by remember(plugin.id) { mutableStateOf<JsonElement>(JsonNull) }
    var screenTree by remember(plugin.id) { mutableStateOf<JsonElement>(JsonNull) }
    var screenState by remember(plugin.id) { mutableStateOf<JsonElement>(JsonObject(emptyMap())) }
    var remove by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<JsonObject?>(null) }
    var browseRef by remember { mutableStateOf<String?>(null) }
    var cursor by remember { mutableStateOf<String?>(null) }
    fun operation(block: suspend ()->Unit) { scope.launch { busy=true; message=""; try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false } } }
    fun runAction(key: String) { operation {
        val answer=repository.action(plugin.id,key)
        config=repository.settings(plugin.id)
        if(answer is JsonObject)message=answer.text("message",answer.text("toast","Acción completada"))
        status=runCatching { repository.call(plugin.id,"settingsStatus").jsonObject }.getOrDefault(status)
    } }
    fun screenContext()=buildJsonObject { put("state",screenState); put("device",if(KinoHost.device=="tv")"tv" else "phone"); put("route","root"); put("lang","es-CL"); put("size",buildJsonObject { put("widthClass",if(KinoHost.device=="phone")"compact"else"expanded"); put("width",if(KinoHost.device=="phone")400 else 1280); put("height",720) }) }
    fun updateScreen() { operation {
        val opened=runCatching { repository.call(plugin.id,"screenEvent",JsonArray(listOf(buildJsonObject { put("name","open"); put("state",screenState); put("arg",JsonNull); put("route","root") }))) }.getOrNull()
        if(opened is JsonObject)screenState=opened["state"] ?: screenState
        screenTree=repository.call(plugin.id,"screen",JsonArray(listOf(screenContext())))
    } }
    LaunchedEffect(plugin.id) {
        config=repository.settings(plugin.id)
        status=runCatching { repository.call(plugin.id,"settingsStatus").jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item { Text(plugin.name,style=MaterialTheme.typography.headlineSmall); Text("${plugin.version} · ${plugin.address}") }
        if(plugin.limitations.isNotEmpty())item { Text("Funciones pendientes: ${plugin.limitations.joinToString()}",color=MaterialTheme.colorScheme.error) }
        if(busy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(message.isNotBlank())item { Text(message,color=MaterialTheme.colorScheme.error) }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if("home" in plugin.capabilities)item { KinoButton("Explorar",!busy) { operation { result=repository.call(plugin.id,"home",JsonArray(listOf(JsonNull))); browseRef=null; cursor=null; screenTree=JsonNull } } }
            if("screen" in plugin.capabilities)item { KinoButton("Pantalla del plugin",!busy) { updateScreen() } }
            item { KinoButton("Desinstalar",!busy) { remove=true } }
        } }
        items(plugin.settings,key={it.text("key")}) { setting ->
            val key=setting.text("key"); val label=setting.text("label",key); val type=setting.text("type")
            when(type) {
                "section","help" -> { Text(label,style=MaterialTheme.typography.titleMedium); if(setting.text("hint").isNotBlank())Text(setting.text("hint")) }
                "status" -> Text("$label: ${status.text(key,"—")}")
                "toggle" -> Row { Switch(checked=config.flag(key),onCheckedChange={config=JsonObject(config+(key to JsonPrimitive(it)))}); Text(label,Modifier.padding(12.dp)) }
                "select" -> Column {
                    Text(label)
                    LazyRow {
                        items(setting.array("options").filterIsInstance<JsonObject>()) { option ->
                            KinoButton((if(config.text(key)==option.text("value"))"✓ " else "")+option.text("label",option.text("value"))) {
                                config=JsonObject(config+(key to (option["value"] ?: JsonNull)))
                            }
                        }
                    }
                }
                "action" -> KinoButton(label,!busy) { if(setting.text("confirm").isNotBlank())pendingAction=setting else runAction(key) }
                "text","url","password" -> OutlinedTextField(config.text(key),{config=JsonObject(config+(key to JsonPrimitive(it)))},label={Text(label)},supportingText={Text(setting.text("hint"))},visualTransformation=if(type=="password")PasswordVisualTransformation()else VisualTransformation.None,singleLine=true,modifier=Modifier.fillMaxWidth())
                "list" -> {
                    var raw by remember(config[key]) { mutableStateOf((config[key] ?: JsonArray(emptyList())).toString()) }
                    OutlinedTextField(raw,{raw=it; runCatching { kinoJson.parseToJsonElement(it).jsonArray }.getOrNull()?.let { array -> config=JsonObject(config+(key to array)) }},label={Text("$label (JSON)")},modifier=Modifier.fillMaxWidth())
                }
                "link","qr" -> Text("$label: este mecanismo necesita integración adicional en Nuvio-Kino.")
            }
        }
        if(plugin.settings.any { it.text("type") in setOf("toggle","select","text","url","password","list") })item { KinoButton("Guardar configuración",!busy) { operation { repository.saveSettings(plugin.id,config); message="Configuración guardada" } } }
        if("search" in plugin.capabilities)item {
            OutlinedTextField(query,{query=it},label={Text("Buscar en ${plugin.name}")},modifier=Modifier.fillMaxWidth())
            KinoButton("Buscar",!busy && query.isNotBlank()) { operation { result=repository.call(plugin.id,"search",JsonArray(listOf(buildJsonObject { put("q",query); put("type","any"); put("cursor",JsonNull); put("season",0); put("episode",0); put("tmdbId",0); put("year",0); put("altTitles",JsonArray(emptyList())) }))); browseRef=null; cursor=null; screenTree=JsonNull } }
        }
        if(screenTree!=JsonNull)item { KinoScreenNode(screenTree) { action ->
            operation {
                val answer=repository.call(plugin.id,"screenEvent",JsonArray(listOf(buildJsonObject { put("name",action.text("event",action.text("name"))); put("arg",action["arg"] ?: JsonNull); put("state",screenState); put("route","root") })))
                if(answer is JsonObject) { screenState=answer["state"] ?: screenState; message=answer.text("toast"); if(answer.obj("config").isNotEmpty()) { config=JsonObject(config+answer.obj("config")); repository.saveSettings(plugin.id,config) } }
                screenTree=repository.call(plugin.id,"screen",JsonArray(listOf(screenContext())))
            }
        } }
        val rows=if(result is JsonArray)result as JsonArray else (result as? JsonObject)?.array("items") ?: JsonArray(emptyList())
        items(rows.filterIsInstance<JsonObject>()) { row ->
            if("items" in row) {
                Text(row.text("title"),style=MaterialTheme.typography.titleLarge)
                row.array("items").filterIsInstance<JsonObject>().forEach { item -> KinoMediaRow(item,!busy) { operation {
                    if(item.text("kind") in setOf("series","music","podcast") && "episodes" in plugin.capabilities)result=repository.call(plugin.id,"episodes",JsonArray(listOf(JsonPrimitive(item.text("ref")))))
                    else onPlay(repository.resolve(plugin.id,item.text("ref"),item.text("title"),item.text("kind")=="live"))
                } } }
                if(row.text("ref").isNotBlank() && "browse" in plugin.capabilities)KinoButton("Ver más",!busy) { operation { browseRef=row.text("ref"); result=repository.call(plugin.id,"browse",JsonArray(listOf(JsonPrimitive(row.text("ref")),JsonNull))) } }
            } else KinoMediaRow(row,!busy) { operation {
                if(row.text("kind")=="series")result=repository.call(plugin.id,"episodes",JsonArray(listOf(JsonPrimitive(row.text("ref"))))) else onPlay(repository.resolve(plugin.id,row.text("ref"),row.text("title"),row.text("kind")=="live"))
            } }
        }
        items((result as? JsonObject)?.array("episodes")?.filterIsInstance<JsonObject>().orEmpty()) { episode -> KinoMediaRow(episode,!busy) { operation { onPlay(repository.resolve(plugin.id,episode.text("ref"),episode.text("title","Episodio ${episode.number("number")}"))) } } }
        val next=(result as? JsonObject)?.text("next").orEmpty()
        if(next.isNotEmpty() && browseRef!=null)item { KinoButton("Siguiente página",!busy) { operation { cursor=next; result=repository.call(plugin.id,"browse",JsonArray(listOf(JsonPrimitive(browseRef!!),JsonPrimitive(next)))) } } }
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("Desinstalar ${plugin.name}")},text={Text("Se borrarán su configuración y sus datos locales.")},confirmButton={KinoButton("Desinstalar") { operation { repository.uninstall(plugin.id); onUninstalled() } }},dismissButton={KinoButton("Cancelar") { remove=false }})
    pendingAction?.let { setting -> AlertDialog(onDismissRequest={pendingAction=null},title={Text(setting.text("label"))},text={Text(setting.text("confirm"))},confirmButton={KinoButton("Continuar") { pendingAction=null; runAction(setting.text("key")) }},dismissButton={KinoButton("Cancelar") { pendingAction=null }}) }
}

@Composable private fun KinoMediaRow(item: JsonObject, enabled: Boolean, onClick: ()->Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) { Column(Modifier.padding(12.dp)) { Text(item.text("title","Sin título"),style=MaterialTheme.typography.titleMedium); if(item.text("overview").isNotBlank())Text(item.text("overview"),maxLines=3); KinoButton(if(item.text("kind")=="live")"Ver en vivo" else if(item.text("kind")=="series")"Episodios" else "Reproducir",enabled,onClick) } }
}

// Basic declarative screen components. Unsupported components remain visible and explicit.
@Composable
private fun KinoScreenNode(element: JsonElement, depth: Int = 0, onAction: (JsonObject)->Unit) {
    if(depth>24)return
    if(element is JsonArray) { Column { element.forEach { KinoScreenNode(it,depth+1,onAction) } }; return }
    val node=element as? JsonObject ?: return
    val type=node.text("type").lowercase()
    val child=node["child"] ?: node["body"] ?: node["children"] ?: node["items"] ?: node["content"]
    Column(Modifier.fillMaxWidth().padding(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        when(type) {
            "text","title","heading","paragraph" -> Text(node.text("text",node.text("title")))
            "button","filledbutton","outlinedbutton","textbutton","listtile" -> {
                val action=node["onTap"] ?: node["onPressed"] ?: node["action"]
                val event=when(action) { is JsonPrimitive -> buildJsonObject { put("event",action.content); node["arg"]?.let { put("arg",it) } }; is JsonObject -> action; else -> JsonObject(emptyMap()) }
                KinoButton(node.text("label",node.text("title",node.text("text","Abrir"))),event.isNotEmpty()) { onAction(event) }
            }
            "scaffold","column","row","card","container","padding","safearea","listview","wrap","center","expanded","sizedbox","appbar","divider","spacer","" -> { if(node.text("title").isNotBlank())Text(node.text("title")) }
            else -> Text("Componente Kino pendiente: ${node.text("type")}",color=MaterialTheme.colorScheme.error)
        }
        child?.let { KinoScreenNode(it,depth+1,onAction) }
    }
}
