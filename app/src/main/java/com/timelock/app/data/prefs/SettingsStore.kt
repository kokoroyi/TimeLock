package com.timelock.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timelock.app.domain.model.AppSettings
import com.timelock.app.domain.model.ManagedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timelock_settings",
)

/**
 * 设置与受管列表的唯一归属者。
 *
 * 刻意不使用注解处理器（Room/KSP）：本应用的数据量很小，
 * 用 DataStore + JSON 足以支撑，同时避免了在 AGP 9 内置 Kotlin 下
 * 引入额外编译器插件的风险。仓储接口保持不变，日后需要时可平滑换成 Room。
 */
class SettingsStore(private val context: Context) {

    private val settingsKey = stringPreferencesKey("settings")
    private val managedAppsKey = stringPreferencesKey("managed_apps")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[settingsKey]
            ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
    }

    val managedApps: Flow<List<ManagedApp>> = context.settingsDataStore.data.map { prefs ->
        prefs[managedAppsKey]
            ?.let {
                runCatching { json.decodeFromString(ManagedAppListSerializer, it) }.getOrNull()
            }
            ?: emptyList()
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[settingsKey]
                ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
                ?: AppSettings()
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    suspend fun updateManagedApps(transform: (List<ManagedApp>) -> List<ManagedApp>) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[managedAppsKey]
                ?.let {
                    runCatching { json.decodeFromString(ManagedAppListSerializer, it) }.getOrNull()
                }
                ?: emptyList()
            prefs[managedAppsKey] = json.encodeToString(ManagedAppListSerializer, transform(current))
        }
    }
}

private val ManagedAppListSerializer =
    kotlinx.serialization.builtins.ListSerializer(ManagedApp.serializer())
