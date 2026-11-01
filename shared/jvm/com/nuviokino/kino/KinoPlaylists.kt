package com.nuviokino.kino

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource

internal object KinoPlaylists {
    fun m3u(text: String, plugin: String, options: JsonObject, base: String): List<KinoChannel> {
        val result=mutableListOf<KinoChannel>(); var attributes=emptyMap<String,String>(); var title=""; var extra=mutableMapOf<String,String>()
        val attr=Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
        val hidden=options.array("hideGroups").map { it.jsonPrimitive.content }.toSet()
        for(raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line=raw.trim()
            when {
                line.startsWith("#EXTINF:",true) -> { attributes=attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }; title=line.substringAfter(',',attributes["tvg-name"] ?: "Canal").trim(); extra=mutableMapOf() }
                line.startsWith("#EXTGRP:",true) -> attributes=attributes+("group-title" to line.substringAfter(':').trim())
                line.startsWith("#EXTVLCOPT:http-user-agent=",true) -> extra["User-Agent"]=line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-referrer=",true) -> extra["Referer"]=line.substringAfter('=')
                line.isNotEmpty() && !line.startsWith('#') -> {
                    val group=attributes["group-title"].orEmpty().ifEmpty { "Sin categoría" }
                    val url=runCatching { base.toHttpUrl().resolve(line.substringBefore('|'))?.toString() }.getOrNull()
                    if(url!=null && group !in hidden) {
                        val id=attributes["tvg-id"].orEmpty().ifEmpty { sha256(url.toByteArray()).take(20) }
                        val headers=JsonObject(options.obj("streamHeaders")+extra.mapValues { JsonPrimitive(it.value) })
                        result.add(KinoChannel(plugin,id,title.ifEmpty { attributes["tvg-name"] ?: id },group,if(options.flag("resolve"))url else null,if(options.flag("resolve"))null else buildJsonObject { put("url",url); put("headers",headers) },attributes["tvg-logo"],attributes["tvg-chno"]?.toLongOrNull() ?: 0,guideId=attributes["tvg-id"] ?: id))
                    }
                    title=""; attributes=emptyMap(); extra=mutableMapOf()
                }
            }
            require(result.size<=30_000) { "Lista M3U demasiado grande" }
        }
        return result.distinctBy { it.key+":"+it.category }
    }

    fun xmltv(xml: String): List<KinoProgramme> {
        val result=mutableListOf<KinoProgramme>()
        val factory=SAXParserFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        var channel=""; var start=0L; var end=0L; var title=""; var desc=""; var field=""; val content=StringBuilder()
        val parser=factory.newSAXParser()
        parser.parse(InputSource(StringReader(xml)),object: DefaultHandler() {
            override fun startElement(uri: String?,localName: String?,qName: String,attrs: Attributes) {
                field=qName; content.setLength(0)
                if(qName=="programme") { channel=attrs.getValue("channel").orEmpty(); start=date(attrs.getValue("start")); end=date(attrs.getValue("stop")); title=""; desc="" }
            }
            override fun characters(ch: CharArray,start: Int,length: Int) { if(field in setOf("title","desc")) { require(content.length+length<65536); content.append(ch,start,length) } }
            override fun endElement(uri: String?,localName: String?,qName: String) {
                if(qName=="title" && title.isEmpty())title=content.toString()
                if(qName=="desc" && desc.isEmpty())desc=content.toString()
                if(qName=="programme" && channel.isNotEmpty() && title.isNotEmpty() && end>start) { require(result.size<100_000); result.add(KinoProgramme(channel,title,start,end,desc)) }
                field=""; content.setLength(0)
            }
        })
        return result
    }
    private fun date(value: String?): Long = runCatching {
        val text=value?.trim() ?: return 0
        val normal=if(text.matches(Regex("[0-9]{14}"))) "$text +0000" else text
        SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT).apply { isLenient=false }.parse(normal)!!.time
    }.getOrDefault(0)
}
