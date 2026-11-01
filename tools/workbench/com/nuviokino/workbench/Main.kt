package com.nuviokino.workbench

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.nuviokino.kino.*
import java.io.File

/** Developer UI runner; the shipped clients use their own Nuvio player/navigation adapters. */
fun main(args: Array<String>) {
    KinoJvm.initialize(File("verification/local/workbench"),if("--tv" in args)"tv" else "desktop")
    application {
        Window(onCloseRequest=::exitApplication,title="Nuvio-Kino · UI workbench") {
            var playback by remember { mutableStateOf<KinoPlayback?>(null) }
            MaterialTheme(colorScheme=darkColorScheme()) {
                KinoHub(initialSection=if("--live" in args)"live"else"store",onBack=::exitApplication,onPlay={playback=it},onInstallStremio={"El workbench solo verifica la interfaz. La instalación Stremio se realiza en los clientes Nuvio-Kino."})
                playback?.let { item -> AlertDialog(onDismissRequest={playback=null},title={Text(item.title)},text={Text("Stream resuelto. Usa el cliente de Nuvio-Kino para reproducirlo.")},confirmButton={Button(onClick={playback=null}){Text("Cerrar")}}) }
            }
        }
    }
}
