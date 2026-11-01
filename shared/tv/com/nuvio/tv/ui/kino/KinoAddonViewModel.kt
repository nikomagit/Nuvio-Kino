package com.nuvio.tv.ui.kino

import androidx.lifecycle.ViewModel
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class KinoAddonViewModel @Inject constructor(private val addons: AddonRepository) : ViewModel() {
    suspend fun install(url: String): String { addons.addAddon(url); return "Addon instalado en Nuvio" }
}
