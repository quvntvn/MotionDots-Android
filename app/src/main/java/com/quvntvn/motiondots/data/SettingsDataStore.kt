package com.quvntvn.motiondots.data

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

enum class IntensityPreset { LOW, NORMAL, HIGH }
enum class OpacityPreset { SUBTLE, BALANCED, VISIBLE }
enum class DensityPreset { LIGHT, STANDARD, DENSE }
enum class SizePreset { SMALL, MEDIUM, LARGE }
enum class DotColor { WHITE, BLACK }

data class OverlaySettings(
    val intensityPreset: IntensityPreset = IntensityPreset.NORMAL,
    val opacityPreset: OpacityPreset = OpacityPreset.BALANCED,
    val densityPreset: DensityPreset = DensityPreset.STANDARD,
    val sizePreset: SizePreset = SizePreset.MEDIUM,
    val dotColor: DotColor = DotColor.WHITE,
    val selectedMode: OverlayMode = OverlayMode.CLASSIC_DOTS,
    val autoStartOverlay: Boolean = false,
    val isPremium: Boolean = false,
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val IntensityPreset: Preferences.Key<String> = stringPreferencesKey("intensity_preset")
        val OpacityPreset: Preferences.Key<String> = stringPreferencesKey("opacity_preset")
        val DensityPreset: Preferences.Key<String> = stringPreferencesKey("density_preset")
        val SizePreset: Preferences.Key<String> = stringPreferencesKey("size_preset")
        val DotColor: Preferences.Key<String> = stringPreferencesKey("dot_color")
        val SelectedMode: Preferences.Key<String> = stringPreferencesKey("selected_mode")
        val AutoStartOverlay: Preferences.Key<Boolean> = booleanPreferencesKey("auto_start_overlay")
        val IsPremium: Preferences.Key<Boolean> = booleanPreferencesKey("is_premium")
        val HasOnboarded: Preferences.Key<Boolean> = booleanPreferencesKey("has_onboarded")

        // Legacy keys kept for migration fallback.
        val LegacyIntensity: Preferences.Key<Float> = floatPreferencesKey("intensity")
        val LegacyOpacity: Preferences.Key<Float> = floatPreferencesKey("opacity")
        val LegacyDotCount: Preferences.Key<Int> = intPreferencesKey("dot_count")
    }

    val settingsFlow: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        OverlaySettings(
            intensityPreset = prefs[Keys.IntensityPreset]
                ?.let { runCatching { IntensityPreset.valueOf(it) }.getOrNull() }
                ?: legacyIntensityToPreset(prefs[Keys.LegacyIntensity]),
            opacityPreset = prefs[Keys.OpacityPreset]
                ?.let { runCatching { OpacityPreset.valueOf(it) }.getOrNull() }
                ?: legacyOpacityToPreset(prefs[Keys.LegacyOpacity]),
            densityPreset = prefs[Keys.DensityPreset]
                ?.let { runCatching { DensityPreset.valueOf(it) }.getOrNull() }
                ?: legacyDensityToPreset(prefs[Keys.LegacyDotCount]),
            sizePreset = prefs[Keys.SizePreset]
                ?.let { runCatching { SizePreset.valueOf(it) }.getOrNull() }
                ?: SizePreset.MEDIUM,
            dotColor = prefs[Keys.DotColor]
                ?.let { runCatching { DotColor.valueOf(it) }.getOrNull() }
                ?: DotColor.WHITE,
            selectedMode = prefs[Keys.SelectedMode]
                ?.let { runCatching { OverlayMode.valueOf(it) }.getOrDefault(OverlayMode.CLASSIC_DOTS) }
                ?: OverlayMode.CLASSIC_DOTS,
            autoStartOverlay = prefs[Keys.AutoStartOverlay] ?: false,
            isPremium = prefs[Keys.IsPremium] ?: false,
        )
    }

    val hasOnboardedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HasOnboarded] ?: false
    }

    suspend fun setIntensityPreset(value: IntensityPreset) {
        context.dataStore.edit { it[Keys.IntensityPreset] = value.name }
    }

    suspend fun setOpacityPreset(value: OpacityPreset) {
        context.dataStore.edit { it[Keys.OpacityPreset] = value.name }
    }

    suspend fun setDensityPreset(value: DensityPreset) {
        context.dataStore.edit { it[Keys.DensityPreset] = value.name }
    }

    suspend fun setSizePreset(value: SizePreset) {
        context.dataStore.edit { it[Keys.SizePreset] = value.name }
    }

    suspend fun setDotColor(value: DotColor) {
        context.dataStore.edit { it[Keys.DotColor] = value.name }
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

    suspend fun setHasOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.HasOnboarded] = value }
    }

    private fun legacyIntensityToPreset(value: Float?): IntensityPreset {
        val safeValue = (value ?: 5f).coerceIn(0f, 10f)
        return when {
            safeValue < 3.5f -> IntensityPreset.LOW
            safeValue < 7.5f -> IntensityPreset.NORMAL
            else -> IntensityPreset.HIGH
        }
    }

    private fun legacyOpacityToPreset(value: Float?): OpacityPreset {
        val safeValue = (value ?: 0.16f).coerceIn(0f, 1f)
        return when {
            safeValue < 0.22f -> OpacityPreset.SUBTLE
            safeValue < 0.38f -> OpacityPreset.BALANCED
            else -> OpacityPreset.VISIBLE
        }
    }

    private fun legacyDensityToPreset(value: Int?): DensityPreset {
        val safeValue = (value ?: 40).coerceIn(10, 100)
        return when {
            safeValue < 35 -> DensityPreset.LIGHT
            safeValue < 70 -> DensityPreset.STANDARD
            else -> DensityPreset.DENSE
        }
    }
}
