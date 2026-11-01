package com.nuviokino.kino

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files

/** Opt-in checks against the exact official sources recorded in docs/repositories.json. */
class KinoOfficialPluginsTest {
    @Test fun officialModulesLoadInQuickJs() = runBlocking {
        val references=System.getenv("KINO_RESEARCH_DIR")?.let(::File)
        assumeTrue("Set KINO_RESEARCH_DIR after fetching the locked reference repos", references?.isDirectory==true)
        val root=Files.createTempDirectory("kino-official").toFile()
        val results=mutableListOf<JsonObject>()
        try {
            val runtime=KinoRuntime(root,KinoNetwork(),"tv")
            val repository=KinoJvmRepository(File(root,"installed"),"tv")
            for(dir in references!!.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
                val manifestFile=listOf(File(dir,"kino-plugin.json"),File(dir,"plugin/kino-plugin.json")).firstOrNull { it.isFile } ?: continue
                val manifest=kinoJson.parseToJsonElement(manifestFile.readText()).jsonObject
                val source=File(manifestFile.parentFile,manifest.text("entry")); if(!source.isFile) continue
                val address="kinotvapp/${dir.name}"+if(manifestFile.parentFile.name=="plugin")"/plugin" else ""
                val plugin=KinoPlugin(manifest,address,"reference")
                // Git for Windows may convert the checkout to CRLF; signatures cover Git blob bytes.
                val relative=source.relativeTo(dir).invariantSeparatorsPath
                val process=ProcessBuilder("git","-C",dir.absolutePath,"show","HEAD:$relative").start()
                val sourceBytes=process.inputStream.readBytes()
                check(process.waitFor()==0)
                val attempt=runCatching {
                    repository.inspectSnapshot(address,"reference",manifest,sourceBytes)
                    repository.install(KinoStoreEntry(address,plugin.name))
                    runtime.call(plugin,sourceBytes.toString(Charsets.UTF_8),"__probe",JsonArray(emptyList()),JsonObject(emptyMap()))
                }
                results.add(buildJsonObject { put("id",plugin.id); put("address",address); put("version",plugin.version); put("apiVersion",manifest.number("apiVersion")); put("moduleLoaded",attempt.isSuccess); put("installed",attempt.isSuccess); put("exports",attempt.getOrNull() ?: JsonNull); put("error",attempt.exceptionOrNull()?.message ?: ""); put("hostIntegrationsPending",JsonArray(plugin.limitations.map(::JsonPrimitive))) })
            }
            val report=File("verification/official-modules.json"); report.parentFile.mkdirs(); report.writeText(Json { prettyPrint=true }.encodeToString(JsonArray.serializer(),JsonArray(results)))
            assertTrue("No official sources found",results.size>=20)
            val failures=results.filter { !it.flag("moduleLoaded") }
            assertTrue(failures.joinToString("\n") { it.text("id")+": "+it.text("error") },failures.isEmpty())
            assertEquals(results.size,KinoJvmRepository(File(root,"installed"),"tv").plugins.value.size)
        } finally { root.deleteRecursively() }
    }
}
