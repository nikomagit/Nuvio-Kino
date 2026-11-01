package com.nuviokino.kino

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun KinoLivePage(repository: KinoRepository, onPlay: (KinoPlayback)->Unit) {
    val scope=rememberCoroutineScope()
    val plugins by repository.plugins.collectAsState()
    val favorites by repository.favorites.collectAsState()
    var categories by remember { mutableStateOf<List<KinoCategory>>(emptyList()) }
    var selected by remember { mutableStateOf<KinoCategory?>(null) }
    var channels by remember { mutableStateOf<List<KinoChannel>>(emptyList()) }
    var guide by remember { mutableStateOf<List<KinoProgramme>>(emptyList()) }
    var focused by remember { mutableStateOf<KinoChannel?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(plugins,refresh) {
        busy=true; message=""
        try { categories=repository.categories(); selected=categories.firstOrNull { it.id==selected?.id && it.pluginId==selected?.pluginId } ?: categories.firstOrNull() }
        catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false }
    }
    LaunchedEffect(selected,refresh) {
        val category=selected ?: return@LaunchedEffect
        busy=true; message=""; channels=emptyList(); guide=emptyList(); focused=null
        try {
            val page=repository.channels(category); channels=page.channels; next=page.next
            val now=kotlin.time.Clock.System.now().toEpochMilliseconds()
            guide=repository.guide(channels,now-3_600_000,now+86_400_000)
        } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("TV en vivo",style=MaterialTheme.typography.headlineSmall)
        Text("Canales, categorías, favoritos y programación")
        OutlinedTextField(query,{query=it},label={Text("Buscar canales en esta categoría")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item { KinoButton(if(onlyFavorites)"★ Favoritos" else "Todos los canales") { onlyFavorites=!onlyFavorites } }
            item { KinoButton("Buscar en todas las fuentes",!busy && query.trim().length>=2) {
                scope.launch { busy=true; message=""; try { channels=repository.searchChannels(query); next=null; message="Resultados de todas las fuentes"; val now=kotlin.time.Clock.System.now().toEpochMilliseconds(); guide=repository.guide(channels,now,now+86_400_000) } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false } }
            } }
            item { KinoButton("Actualizar",!busy) { refresh++ } }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
        if(categories.isEmpty() && !busy)Text("Instala y configura un plugin con la capacidad channels desde la tienda.")
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide=maxWidth>=720.dp || KinoHost.device=="tv"
            val showGuide=maxWidth>1050.dp
            @Composable fun CategoryList(modifier: Modifier) {
                LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(categories,key={it.pluginId+":"+it.id}) { category ->
                        val source=plugins.firstOrNull { it.id==category.pluginId }?.name ?: category.pluginId
                        KinoButton((if(selected==category)"✓ " else "")+category.title+" · "+source) { selected=category }
                    }
                }
            }
            @Composable fun ChannelList(modifier: Modifier) {
                val visible=channels.filter { !it.adult && (!onlyFavorites || it.key in favorites) && it.title.contains(query,true) }
                LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                    item { Text("${visible.size} canales",style=MaterialTheme.typography.titleMedium) }
                    items(visible,key={it.key}) { channel ->
                        val now=kotlin.time.Clock.System.now().toEpochMilliseconds()
                        val current=guide.firstOrNull { it.channelId==channel.guideId && it.pluginId==channel.pluginId && it.start<=now && it.end>now }
                        Card(Modifier.fillMaxWidth().onFocusChanged { if(it.hasFocus)focused=channel }) {
                            Column(Modifier.padding(12.dp)) {
                                Text((if(channel.number>0)"${channel.number}. " else "")+channel.title,style=MaterialTheme.typography.titleMedium)
                                Text(current?.title ?: "Programación no disponible")
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    KinoButton("Ver en vivo",!busy) { scope.launch { busy=true; message=""; try { onPlay(repository.play(channel)) } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false } } }
                                    KinoButton(if(channel.key in favorites)"★" else "☆") { scope.launch { repository.favorite(channel) } }
                                }
                            }
                        }
                    }
                    if(next!=null && selected!=null)item { KinoButton("Más canales",!busy) { scope.launch { busy=true; try { val page=repository.channels(selected!!,next); channels=(channels+page.channels).distinctBy { it.key }; next=page.next } catch(e: CancellationException) { throw e } catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false } } } }
                }
            }
            if(wide)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                CategoryList(Modifier.widthIn(min=180.dp,max=260.dp).fillMaxHeight())
                ChannelList(Modifier.weight(1f).fillMaxHeight())
                if(focused!=null && showGuide)Column(Modifier.width(240.dp)) { Text(focused!!.title,style=MaterialTheme.typography.titleLarge); guide.filter { it.channelId==focused!!.guideId && it.pluginId==focused!!.pluginId }.take(8).forEach { Text(it.title,Modifier.padding(vertical=6.dp)) } }
            } else Column {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(categories) { category -> KinoButton((if(selected==category)"✓ " else "")+category.title) { selected=category } } }
                ChannelList(Modifier.fillMaxSize())
            }
        }
    }
}
