package com.nufo.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Units { Metric, Imperial }
enum class ThemeMode { System, Light, Dark }
enum class Diet(val label: String) { None("None"), Vegetarian("Vegetarian"), Vegan("Vegan"), Keto("Keto"), LowSodium("Low-sodium") }

data class Settings(
    val onboarded: Boolean = false,
    val units: Units = Units.Metric,
    val theme: ThemeMode = ThemeMode.System,
    val diet: Diet = Diet.None,
    /** Allergen names as shown by OFF tags, e.g. "Milk", "Nuts". */
    val allergenAlerts: Set<String> = emptySet(),
    /** Look for a new version once a day, on opening. */
    val autoUpdates: Boolean = true,
)

private val Context.store by preferencesDataStore("nufo_settings")

class SettingsStore(private val context: Context) {
    private val onboarded = booleanPreferencesKey("onboarded")
    private val units = stringPreferencesKey("units")
    private val theme = stringPreferencesKey("theme")
    private val diet = stringPreferencesKey("diet")
    private val allergens = stringSetPreferencesKey("allergens")
    private val autoUpdates = booleanPreferencesKey("auto_updates")
    private val lastUpdateCheck = longPreferencesKey("last_update_check")

    val settings = context.store.data.map { p ->
        Settings(
            onboarded = p[onboarded] ?: false,
            units = p[units]?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.Metric,
            theme = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            diet = p[diet]?.let { runCatching { Diet.valueOf(it) }.getOrNull() } ?: Diet.None,
            allergenAlerts = p[allergens] ?: emptySet(),
            autoUpdates = p[autoUpdates] ?: true,
        )
    }

    suspend fun setOnboarded() = context.store.edit { it[onboarded] = true }
    suspend fun setUnits(v: Units) = context.store.edit { it[units] = v.name }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[theme] = v.name }
    suspend fun setDiet(v: Diet) = context.store.edit { it[diet] = v.name }
    suspend fun setAutoUpdates(v: Boolean) = context.store.edit { it[autoUpdates] = v }
    suspend fun lastUpdateCheck(): Long = context.store.data.first()[lastUpdateCheck] ?: 0L
    suspend fun markUpdateChecked(at: Long) = context.store.edit { it[lastUpdateCheck] = at }
    suspend fun toggleAllergen(name: String) = context.store.edit {
        val now = it[allergens] ?: emptySet()
        it[allergens] = if (name in now) now - name else now + name
    }
}