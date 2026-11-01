package com.nuviokino.kino

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class KinoCoreTest {
    private fun plugin(id: String = "test-source", server: Boolean = false) = KinoPlugin(buildJsonObject {
        put("id",id); put("name","Prueba"); put("version","1.0.0"); put("apiVersion",7); put("entry","plugin.js")
        put("hosts",JsonArray(emptyList())); put("capabilities",JsonArray(listOf(JsonPrimitive("home"),JsonPrimitive("resolve"))))
        if(server)put("settings",JsonArray(listOf(buildJsonObject { put("key","server"); put("type","url") })))
    },"example/test-source","0".repeat(40))

    @Test fun esModulesAndPersistentStorageStayIsolated() = runBlocking {
        val root=Files.createTempDirectory("kino-test").toFile()
        try {
            val runtime=KinoRuntime(root,KinoNetwork(),"tv")
            val code="""export const home = async () => { const n=Number(kino.storage.get('n')||0)+1; kino.storage.set('n',n); return {n,device:kino.device}; }; export async function resolve(ref) { return {url:ref}; }"""
            val args=JsonArray(emptyList()); val settings=JsonObject(emptyMap())
            assertEquals(1,runtime.call(plugin(),code,"home",args,settings).jsonObject.number("n").toInt())
            assertEquals(2,runtime.call(plugin(),code,"home",args,settings).jsonObject.number("n").toInt())
            assertEquals(1,runtime.call(plugin("other-source"),code,"home",args,settings).jsonObject.number("n").toInt())
            assertEquals("tv",runtime.call(plugin(),code,"home",args,settings).jsonObject.text("device"))
        } finally { root.deleteRecursively() }
    }
    @Test fun fetchUsesConfiguredServerAndRefusesUndeclaredRedirect() = runBlocking {
        val root=Files.createTempDirectory("kino-net-test").toFile(); val server=MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"channels\":2}").setHeader("Content-Type","application/json"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","http://localhost:9/private"))
            val runtime=KinoRuntime(root,KinoNetwork(),"phone")
            val config=buildJsonObject { put("server",server.url("/").toString()) }
            val code="""export async function home() { const r = await kino.fetch(kino.config.get('server')); return r.json(); }"""
            assertEquals(2,runtime.call(plugin(server=true),code,"home",JsonArray(emptyList()),config).jsonObject.number("channels").toInt())
            val failure=runCatching { runtime.call(plugin(server=true),code,"home",JsonArray(emptyList()),config) }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty(),failure?.message?.contains("host_not_allowed")==true)
        } finally { server.shutdown(); root.deleteRecursively() }
    }
    @Test fun m3uGroupsHeadersResolveAndStableIds() {
        val text="""#EXTM3U
            #EXTINF:-1 tvg-id="news-one" tvg-logo="https://img.example/logo.png" group-title="Noticias",Noticias Uno
            #EXTVLCOPT:http-user-agent=VLC
            https://cdn.example/news.m3u8
            #EXTINF:-1 group-title="Ocultos",Otro
            https://cdn.example/other.m3u8
        """.trimIndent()
        val options=buildJsonObject { put("hideGroups",JsonArray(listOf(JsonPrimitive("Ocultos")))); put("streamHeaders",buildJsonObject { put("Referer","https://example.org/") }) }
        val channels=KinoPlaylists.m3u(text,"test-source",options,"https://example.org/list.m3u")
        assertEquals(1,channels.size); assertEquals("news-one",channels[0].id); assertEquals("Noticias",channels[0].category)
        assertEquals("VLC",channels[0].stream!!.obj("headers").text("User-Agent"))
        val resolved=KinoPlaylists.m3u(text,"test-source",buildJsonObject { put("resolve",true) },"https://example.org/list.m3u")
        assertNull(resolved[0].stream); assertEquals("https://cdn.example/news.m3u8",resolved[0].ref)
    }
    @Test fun xmltvTimezoneAndExternalEntities() {
        val xml="""<tv><programme channel="news-one" start="20261101120000 -0300" stop="20261101130000 -0300"><title>Noticias &amp; tiempo</title><desc>Actualidad</desc></programme></tv>"""
        val rows=KinoPlaylists.xmltv(xml)
        assertEquals(1,rows.size); assertEquals(3600000L,rows[0].end-rows[0].start); assertEquals("Noticias & tiempo",rows[0].title)
        assertTrue(runCatching { KinoPlaylists.xmltv("""<!DOCTYPE tv [<!ENTITY x SYSTEM "file:///etc/passwd">]><tv><programme>&x;</programme></tv>""") }.isFailure)
    }
    @Test fun privateAddressesRequireExplicitSetting() {
        val network=KinoNetwork()
        assertFalse(network.permitted(plugin(),JsonObject(emptyMap()),"http://127.0.0.1/test"))
        val config=buildJsonObject { put("server","http://127.0.0.1:8096/media") }
        assertTrue(network.permitted(plugin(server=true),config,"http://127.0.0.1:8096/media/channels"))
        assertFalse(network.permitted(plugin(server=true),config,"http://127.0.0.1:8096/other"))
        assertFalse(network.permitted(plugin(server=true),config,"http://127.0.0.1:8097/media/channels"))
    }
    @Test fun hlsRewritesVariantsSegmentsKeysAndRenditions() {
        val text="""#EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXT-X-MEDIA:TYPE=AUDIO,URI="../audio/main.m3u8"
            #EXTINF:2,
            seg-1.ts?token=one
        """.trimIndent()
        val answer=KinoMediaProxy.rewriteHls(text,"https://cdn.example/video/live.m3u8") { "proxy("+it+")" }
        assertTrue(answer.contains("URI=\"proxy(https://cdn.example/video/key.bin)\""))
        assertTrue(answer.contains("proxy(https://cdn.example/audio/main.m3u8)"))
        assertTrue(answer.contains("proxy(https://cdn.example/video/seg-1.ts?token=one)"))
    }
    @Test fun dashKeepsPlayerSubstitutionTemplatesAndRoutesBaseUrls() {
        val text="""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet><Representation id="v1"><BaseURL>media/</BaseURL><SegmentTemplate media="v-${'$'}Number${'$'}.m4s" initialization="init.mp4" /></Representation></AdaptationSet></Period></MPD>"""
        val answer=KinoMediaProxy.rewriteDash(text,"https://cdn.example/live/main.mpd") { "http://127.0.0.1/base/"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())+"/" }
        assertTrue(answer.contains("v-${'$'}Number${'$'}.m4s"))
        assertFalse(answer.contains("<BaseURL>media/</BaseURL>"))
        assertTrue(answer.contains("http://127.0.0.1/base/"))
    }
}
