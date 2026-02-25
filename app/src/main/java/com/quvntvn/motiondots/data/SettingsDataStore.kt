package com.example.motiondots.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE = "motiondots_settings"
private val Context.dataStore by preferencesDataStore(name = SETTINGS_DATASTORE)

enum class OverlayMode {
    CLASSIC_DOTS,
    EDGE_DOTS,
    HORIZON,
    DISABLED,
}

data class OverlaySettings(
    val intensity: Float = 5f,
    val opacity: Float = 0.16f,
    val dotCount: Int = 40,
    val selectedMode: OverlayMode = OverlayMode.CLASSIC_DOTS,
    val autoStartOverlay: Boolean = false,
    val isPremium: Boolean = false,
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val Intensity: Preferences.Key<Float> = floatPreferencesKey("intensity")
        val Opacity: Preferences.Key<Float> = floatPreferencesKey("opacity")
        val DotCount: Preferences.Key<Int> = intPreferencesKey("dot_count")
        val SelectedMode: Preferences.Key<String> = stringPreferencesKey("selected_mode")
        val AutoStartOverlay: Preferences.Key<Boolean> = booleanPreferencesKey("auto_start_overlay")
        val IsPremium: Preferences.Key<Boolean> = booleanPreferencesKey("is_premium")
    }

    val settingsFlow: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        OverlaySettings(
            intensity = (prefs[Keys.Intensity] ?: 5f).coerceIn(0f, 10f),
            opacity = (prefs[Keys.Opacity] ?: 0.16f).coerceIn(0f, 1f),
            dotCount = (prefs[Keys.DotCount] ?: 40).coerceIn(10, 100),
            selectedMode = prefs[Keys.SelectedMode]
                ?.let { runCatching { OverlayMode.valueOf(it) }.getOrDefault(OverlayMode.CLASSIC_DOTS) }
                ?: OverlayMode.CLASSIC_DOTS,
            autoStartOverlay = prefs[Keys.AutoStartOverlay] ?: false,
            isPremium = prefs[Keys.IsPremium] ?: false,
        )
    }

    suspend fun setIntensity(value: Float) {
        context.dataStore.edit { it[Keys.Intensity] = value.coerceIn(0f, 10f) }
    }

    suspend fun setOpacity(value: Float) {
        context.dataStore.edit { it[Keys.Opacity] = value.coerceIn(0f, 1f) }
    }

    suspend fun setDotCount(value: Int) {
        context.dataStore.edit { it[Keys.DotCount] = value.coerceIn(10, 100) }
    }

    suspend fun setSelectedMode(value: OverlayMode) {
        context.dataStore.edit { it[Keys.SelectedMode] = value.name }
    }

    suspend fun setAutoStartOverlay(value: Boolean) {
        context.dataStore.edit { it[Keys.AutoStartOverlay] = value }
    }

    suspend fun setIsPremium(value: Boolean) {
        context.dataStore.edit { it[Keys.IsPremium] = value }
    }
}
