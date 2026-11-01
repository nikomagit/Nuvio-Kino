package com.nuviokino.kino

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.FilterInputStream
import java.net.InetAddress
import java.net.Proxy
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A loopback transport used by all players. It checks every hop and preserves per-request headers. */
internal class KinoMediaProxy(private val network: KinoNetwork) : NanoHTTPD("127.0.0.1",0) {
    private data class Session(
        val plugin: KinoPlugin, val config: JsonObject, val stream: JsonObject, val live: Boolean,
        val sign: suspend (String)->JsonObject, val created: Long = System.currentTimeMillis(),
    )
    private val sessions=ConcurrentHashMap<String,Session>()
    init { start(SOCKET_READ_TIMEOUT,true) }

    fun register(plugin: KinoPlugin, config: JsonObject, stream: JsonObject, live: Boolean, sign: suspend (String)->JsonObject): JsonObject {
        sessions.entries.removeAll { System.currentTimeMillis()-it.value.created>12*60*60*1000L }
        require(sessions.size<256) { "Demasiadas sesiones de reproducción; reinicia la app" }
        val key=UUID.randomUUID().toString()
        sessions[key]=Session(plugin,config,stream,live,sign)
        val transformed=stream.toMutableMap()
        transformed["url"]=JsonPrimitive(local(key,stream.text("url")))
        transformed["headers"]=JsonObject(emptyMap())
        if(stream["subtitles"] is JsonArray) transformed["subtitles"]=JsonArray(stream.array("subtitles").map { sub ->
            val obj=sub as? JsonObject ?: return@map sub
            JsonObject(obj+("url" to JsonPrimitive(local(key,obj.text("url"))+"&kind=subtitle")))
        })
        transformed.remove("signing"); transformed.remove("signContext")
        return JsonObject(transformed)
    }
    private fun local(key: String,url: String)="http://127.0.0.1:$listeningPort/kino/$key?u="+Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray())

    override fun serve(request: IHTTPSession): Response {
        if(request.method !in setOf(Method.GET,Method.HEAD))return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,"text/plain","Método no permitido")
        val route=request.uri.removePrefix("/kino/").split('/',limit=4)
        val key=route[0]
        val session=sessions[key] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Sesión no disponible")
        val requested=runCatching {
            if(route.size>=3 && route[1]=="b") {
                val base=Base64.getUrlDecoder().decode(route[2]).toString(Charsets.UTF_8).toHttpUrl()
                base.resolve(route.getOrElse(3){""}+(request.queryParameterString?.let { "?$it" } ?: ""))!!
            } else Base64.getUrlDecoder().decode(request.parms["u"]).toString(Charsets.UTF_8).toHttpUrl()
        }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST,"text/plain","URL inválida")
        val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
            .dns(object: Dns { override fun lookup(hostname: String): List<InetAddress> = network.connectionAddresses(session.plugin,session.config,hostname) }).build()
        try {
            var url=requested
            repeat(11) { hop ->
                val isMedia=url.toString()==session.stream.text("url") || request.parms["kind"]!="subtitle"
                require(network.permitted(session.plugin,session.config,url.toString(),stream=isMedia,live=session.live)) { "host_not_allowed" }
                val builder=Request.Builder().url(url).method(if(request.method==Method.HEAD)"HEAD" else "GET",null)
                session.stream.obj("headers").forEach { (name,value) ->
                    if(hop==0 || name.lowercase() !in setOf("authorization","cookie","proxy-authorization"))builder.header(name,value.jsonPrimitive.content)
                }
                request.headers["range"]?.let { builder.header("Range",it) }
                if(session.stream.text("signing")=="request") {
                    val answer=runBlocking { session.sign(url.toString()) }
                    answer.obj("headers").forEach { (name,value) -> builder.header(name,value.jsonPrimitive.content) }
                }
                val upstream=client.newCall(builder.build()).execute()
                val location=upstream.header("Location")
                if(upstream.code in setOf(301,302,303,307,308) && location!=null) {
                    require(hop<10) { "Demasiadas redirecciones" }
                    url=url.resolve(location) ?: error("Redirección inválida"); upstream.close()
                } else {
                    val mime=upstream.header("Content-Type").orEmpty().substringBefore(';')
                    val hls=url.encodedPath.endsWith(".m3u8",true) || mime.lowercase() in setOf("application/vnd.apple.mpegurl","application/x-mpegurl","audio/mpegurl","audio/x-mpegurl")
                    val dash=url.encodedPath.endsWith(".mpd",true) || mime.equals("application/dash+xml",true)
                    if((hls || dash) && upstream.isSuccessful && request.method!=Method.HEAD) {
                        val body=upstream.body ?: error("Lista vacía")
                        val bytes=body.byteStream().use { input ->
                            val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
                            while(true) { val n=input.read(chunk); if(n<0)break; require(out.size()+n<=2_000_000){"Lista demasiado grande"}; out.write(chunk,0,n) }; out.toByteArray()
                        }
                        val finalUrl=url; upstream.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
                        val rewritten=if(hls)rewriteHls(bytes.toString(Charsets.UTF_8),finalUrl.toString()) { target -> local(key,target) }
                            else rewriteDash(bytes.toString(Charsets.UTF_8),finalUrl.toString()) { base -> "http://127.0.0.1:$listeningPort/kino/$key/b/"+Base64.getUrlEncoder().withoutPadding().encodeToString(base.toByteArray())+"/" }
                        return newFixedLengthResponse(Response.Status.OK,if(hls)"application/vnd.apple.mpegurl" else "application/dash+xml",rewritten)
                    }
                    val status=Response.Status.lookup(upstream.code) ?: Response.Status.SERVICE_UNAVAILABLE
                    val body=upstream.body
                    val input=object: FilterInputStream(body?.byteStream() ?: java.io.ByteArrayInputStream(ByteArray(0))) {
                        override fun close() { try { super.close() } finally { upstream.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() } }
                    }
                    val length=body?.contentLength() ?: 0L
                    val response=if(length>=0)newFixedLengthResponse(status,mime.ifEmpty { "application/octet-stream" },input,length)else newChunkedResponse(status,mime.ifEmpty { "application/octet-stream" },input)
                    for(header in listOf("Content-Range","Accept-Ranges","ETag","Last-Modified"))upstream.header(header)?.let { response.addHeader(header,it) }
                    return response
                }
            }
        } catch(_: Exception) { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE,"text/plain","No se pudo abrir la señal del canal")
    }

    companion object {
        internal fun rewriteDash(text: String, base: String, routeBase: (String)->String): String {
            val factory=javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware=true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
            val document=factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(text)))
            fun absolute(value: String, origin: String)=origin.toHttpUrl().resolve(value)?.toString() ?: error("URI DASH inválida")
            fun template(value: String, origin: String): String {
                val full=absolute(value,origin); val slash=full.substringBefore('?').lastIndexOf('/')
                return routeBase(full.substring(0,slash+1))+full.substring(slash+1)
            }
            fun visit(node: org.w3c.dom.Element, inherited: String) {
                val children=(0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? org.w3c.dom.Element }
                val bases=children.filter { it.localName=="BaseURL" || it.nodeName=="BaseURL" }
                val effective=bases.firstOrNull()?.textContent?.trim()?.let { absolute(it,inherited) } ?: inherited
                bases.forEach { it.textContent=routeBase(absolute(it.textContent.trim(),inherited)) }
                for(attr in listOf("media","initialization","sourceURL"))if(node.hasAttribute(attr))node.setAttribute(attr,template(node.getAttribute(attr),effective))
                children.filterNot { it in bases }.forEach { child ->
                    if(child.localName=="Location" || child.nodeName=="Location")child.textContent=template(child.textContent.trim(),effective)
                    else visit(child,effective)
                }
                if(node==document.documentElement && bases.isEmpty()) {
                    val baseNode=document.createElementNS(node.namespaceURI,"BaseURL")
                    baseNode.textContent=routeBase(base.toHttpUrl().resolve(".")!!.toString())
                    node.insertBefore(baseNode,node.firstChild)
                }
            }
            visit(document.documentElement,base)
            val writer=java.io.StringWriter()
            javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(document),javax.xml.transform.stream.StreamResult(writer))
            return writer.toString()
        }
        internal fun rewriteHls(text: String, base: String, route: (String)->String): String {
            val origin=base.toHttpUrl()
            fun rewrite(uri: String): String = origin.resolve(uri)?.let { route(it.toString()) } ?: error("URI HLS inválida")
            return text.lineSequence().joinToString("\n") { line ->
                if(line.trimStart().startsWith('#')) Regex("URI=\"([^\"]+)\"").replace(line.trim()) { match -> "URI=\"${rewrite(match.groupValues[1])}\"" }
                else if(line.isBlank())line else rewrite(line.trim())
            }
        }
    }
}
