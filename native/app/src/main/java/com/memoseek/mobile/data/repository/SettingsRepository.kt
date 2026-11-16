package com.memoseek.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.memoseek.mobile.data.model.DefaultModel
import com.memoseek.mobile.ui.i18n.AppLanguage
import com.memoseek.mobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 本地设置。
 *
 * 用 DataStore 而不是 SharedPreferences：官方 Android 推荐，
 * 异步读写不阻塞主线程，且天然支持 Flow。
 */
class SettingsRepository(private val context: Context) {

    val apiKey: Flow<String> = context.settingsStore.data.map { it[KEY_API_KEY] ?: "" }

    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[KEY_THEME] = mode.name }
    }

    val modelId: Flow<String> = context.settingsStore.data.map {
        it[KEY_MODEL] ?: DefaultModel.id
    }

    suspend fun setModelId(id: String) {
        context.settingsStore.edit { it[KEY_MODEL] = id }
    }

    val language: Flow<AppLanguage> = context.settingsStore.data.map { prefs ->
        runCatching { AppLanguage.valueOf(prefs[KEY_LANG] ?: AppLanguage.System.name) }
            .getOrDefault(AppLanguage.System)
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.settingsStore.edit { it[KEY_LANG] = value.name }
    }

    suspend fun setApiKey(value: String) {
        context.settingsStore.edit { it[KEY_API_KEY] = value.trim() }
    }

    suspend fun clearApiKey() {
        context.settingsStore.edit { it.remove(KEY_API_KEY) }
    }

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_MODEL = stringPreferencesKey("model_id")
        val KEY_LANG = stringPreferencesKey("app_language")
    }
}
