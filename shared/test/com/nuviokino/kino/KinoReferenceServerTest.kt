package com.nuviokino.kino

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

class KinoReferenceServerTest {
    @Test fun installConfigureListGuideResolveAndFetchLiveMedia() = runBlocking {
        val references=System.getenv("KINO_RESEARCH_DIR")?.let(::File)
        assumeTrue(references?.isDirectory==true)
        val source=File(references,"kino-plugin-own-server")
        val port=ServerSocket(0).use { it.localPort }
        val root=Files.createTempDirectory("kino-reference-server").toFile()
        val server=ProcessBuilder("node",File(source,"server.mjs").absolutePath,"--port",port.toString(),"--user","kino-test","--password","test-only").directory(source).redirectErrorStream(true).redirectOutput(File(root,"server.log")).start()
        val repository=KinoJvmRepository(File(root,"app"),"tv")
        val client=OkHttpClient()
        try {
            var ready=false
            for(attempt in 0..49) { if(runCatching { java.net.Socket("127.0.0.1",port).close() }.isSuccess) { ready=true; break }; delay(100) }
            assertTrue("Reference server did not start",ready)
            val manifest=kinoJson.parseToJsonElement(File(source,"kino-plugin.json").readText()).jsonObject
            val bytes=ProcessBuilder("git","-C",source.absolutePath,"show","HEAD:plugin.js").start().inputStream.readBytes()
            val p=repository.inspectSnapshot("kinotvapp/kino-plugin-own-server","reference",manifest,bytes)
            repository.install(KinoStoreEntry(p.address,p.name))
            repository.saveSettings(p.id,buildJsonObject { put("server","http://127.0.0.1:$port"); put("user","kino-test"); put("password","test-only") })
            val home=repository.call(p.id,"home",JsonArray(listOf(JsonNull)))
            assertTrue("Official home is empty",home.jsonArray.isNotEmpty())
            val categories=repository.categories()
            assertTrue("Expected regular and playlist categories",categories.size>=3)
            val channels=categories.flatMap { repository.channels(it).channels }.distinctBy { it.key }
            assertTrue("Expected channels from all three formats",channels.size>=9)
            val now=System.currentTimeMillis()
            val guide=repository.guide(channels,now-3600000,now+86400000)
            assertTrue("Expected an XMLTV or plugin guide",guide.isNotEmpty())
            val channel=channels.first { it.ref!=null && !it.id.startsWith("lista") }
            val playback=repository.play(channel)
            assertTrue(playback.live)
            assertTrue(playback.stream.text("url").startsWith("http://127.0.0.1:"))
            val playlist=client.newCall(Request.Builder().url(playback.stream.text("url")).build()).execute().use { response -> assertTrue(response.isSuccessful); response.body!!.string() }
            assertTrue(playlist.startsWith("#EXTM3U"))
            val segment=playlist.lineSequence().first { it.isNotBlank() && !it.startsWith('#') }
            val media=client.newCall(Request.Builder().url(segment).build()).execute().use { response -> assertTrue(response.isSuccessful); response.body!!.bytes() }
            assertTrue("Expected MPEG-TS data",media.size>188 && media[0]==0x47.toByte())
            val decoded=System.getenv("KINO_MPV_LIBRARY")?.takeIf { File(it).isFile }?.let { nativeVideoProbe(it,playback.stream.text("url")) }
            val protected=repository.resolve(p.id,"protegido","Video protegido")
            val protectedList=client.newCall(Request.Builder().url(protected.stream.text("url")).build()).execute().use { response -> assertTrue("Signed HLS manifest",response.isSuccessful); response.body!!.string() }
            val protectedSegment=protectedList.lineSequence().first { it.isNotBlank() && !it.startsWith('#') }
            client.newCall(Request.Builder().url(protectedSegment).build()).execute().use { response -> assertTrue("Per-request signed HLS segment",response.isSuccessful); assertTrue(response.body!!.bytes().isNotEmpty()) }
            val restarted=KinoJvmRepository(File(root,"app"),"tv")
            assertEquals(p.id,restarted.plugins.value.single().id)
            repository.favorite(channel)
            assertTrue(channel.key in repository.favorites.value)
            val report=buildJsonObject { put("plugin",p.id); put("homeRows",home.jsonArray.size); put("categories",categories.size); put("channels",channels.size); put("guideEntries",guide.size); put("hlsPlaylistFetched",true); put("mpegTsBytes",media.size); put("requestSignedHlsManifestAndSegment",true); put("nativeVideoDecoded",decoded!=null); decoded?.let { put("decodedVideoWidth",it.first); put("playbackSeconds",it.second) }; put("reopenedInstallation",true); put("note","Transport and optional native decoding probe. This test does not assert visible audiovisual output on a device.") }
            File("verification/reference-server.json").apply { parentFile.mkdirs(); writeText(report.toString()) }
            repository.uninstall(p.id); assertTrue(repository.plugins.value.isEmpty())
        } finally { server.destroy(); server.waitFor(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); root.deleteRecursively() }
    }
}
