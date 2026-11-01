package com.nuvio.tv.kino

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuviokino.kino.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KinoRuntimeAndroidTest {
    @Test fun installsAndExecutesEsModuleWithAndroidQuickJs() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"kino-probe-${UUID.randomUUID()}")
        try {
            val repository=KinoJvmRepository(directory,"tv")
            val manifest=kinoJson.parseToJsonElement("""{"id":"android-probe","name":"Android probe","version":"1.0.0","apiVersion":3,"entry":"plugin.js","hosts":[],"capabilities":["home","resolve"]}""").jsonObject
            val source="""
                export async function home() {
                  const n=Number(kino.storage.get('n') || 0)+1;
                  kino.storage.set('n',String(n));
                  return [{id:'probe',title:kino.device,items:[],n}];
                }
                export async function resolve(ref) { return {url:'https://example.org/' + ref}; }
            """.trimIndent().toByteArray()
            repository.inspectSnapshot("test/android-probe","test",manifest,source)
            repository.install(KinoStoreEntry("test/android-probe","Android probe"))
            assertEquals("android-probe",repository.plugins.value.single().id)
            val first=repository.call("android-probe","home").jsonArray.single().jsonObject
            assertEquals("tv",first.text("title")); assertEquals(1L,first.number("n"))
            assertEquals(2L,repository.call("android-probe","home").jsonArray.single().jsonObject.number("n"))
            assertEquals(1,KinoJvmRepository(directory,"tv").plugins.value.size)
        } finally { directory.deleteRecursively() }
    }
}
