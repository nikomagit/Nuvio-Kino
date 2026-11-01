package com.nuviokino.probe

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.util.Log
import com.nuviokino.kino.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val view=TextView(this).apply { text="Testing the Android QuickJS runtime…"; textSize=22f }
        setContentView(view)
        System.setProperty("nuviokino.kino.trace","true")
        Thread {
            val result=runCatching { runBlocking {
                val repository=KinoJvmRepository(File(cacheDir,"probe-${System.nanoTime()}"),"tv")
                val manifest=kinoJson.parseToJsonElement("""{"id":"android-probe","name":"Android probe","version":"1.0.0","apiVersion":3,"entry":"plugin.js","hosts":[],"capabilities":["home","resolve"]}""").jsonObject
                val source="""export async function home(){ return [{id:'ok',title:kino.device,items:[]}]; } export async function resolve(ref){return {url:ref};}""".toByteArray()
                repository.inspectSnapshot("test/android-probe","test",manifest,source)
                repository.install(KinoStoreEntry("test/android-probe","Android probe"))
                repository.call("android-probe","home").toString()
            } }
            val text=result.fold({"PASS: $it"},{"FAIL: ${it.stackTraceToString()}"})
            File(filesDir,"result.txt").writeText(text)
            Log.i("KinoProbe",text)
            runOnUiThread { view.text=text }
        }.start()
    }
}
