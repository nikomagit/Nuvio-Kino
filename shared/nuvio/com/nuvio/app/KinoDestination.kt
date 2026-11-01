package com.nuvio.app

import androidx.compose.runtime.Composable
import com.nuviokino.kino.*
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.PlayerRoute
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun KinoDestination(section: String, navigator: NuvioNavigator) {
    PlatformBackHandler(enabled=true) { navigator.popBackStack() }
    KinoHub(initialSection=section,onBack={navigator.popBackStack()},onInstallStremio={url -> AddonRepository.addAddon(url).toString()},onPlay={playback ->
        val stream=playback.stream
        val launch=PlayerLaunch(
            profileId=ProfileRepository.activeProfileId, title=playback.title,
            sourceUrl=stream.text("url"), sourceHeaders=stream.obj("headers").mapValues { it.value.jsonPrimitive.content },
            streamType=stream.text("mime").ifEmpty { null }, streamTitle=playback.title,
            providerName=KinoHost.repository?.plugins?.value?.firstOrNull { it.id==playback.pluginId }?.name ?: "Kino",
            providerAddonId="kino:${playback.pluginId}", contentType=if(playback.live)"channel" else "movie",
            parentMetaId=if(playback.live)"" else "kino:${playback.pluginId}:${playback.ref}",
            parentMetaType=if(playback.live)"channel" else "movie", kinoPlayback=playback,
        )
        val id=PlayerLaunchStore.put(launch)
        navigator.navigate(PlayerRoute(launchId=id,title=playback.title))
    })
}
