package com.abk.kernel.miuix.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MiuixSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)

    val state = combine(
        prefs.uiStyle,
        prefs.miuixThemeColorArgb,
        prefs.miuixAccentColorArgb
    ) { uiStyle, miuixTheme, miuixAccent ->
        MiuixUiState(
            uiStyle = uiStyle,
            miuixThemeColorArgb = miuixTheme,
            miuixAccentColorArgb = miuixAccent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiuixUiState())

    fun setUiStyle(style: String) = viewModelScope.launch { prefs.setUiStyle(style) }
    fun setMiuixThemeColor(argb: Int) = viewModelScope.launch { prefs.setMiuixThemeColor(argb) }
    fun setMiuixAccentColor(argb: Int) = viewModelScope.launch { prefs.setMiuixAccentColor(argb) }
}

data class MiuixUiState(
    val uiStyle: String = "material",
    val miuixThemeColorArgb: Int? = null,
    val miuixAccentColorArgb: Int? = null
)
