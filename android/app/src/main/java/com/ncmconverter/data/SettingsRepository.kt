package com.ncmconverter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val OUTPUT_TREE = stringPreferencesKey("output_tree")
        val OUTPUT_RELATIVE = stringPreferencesKey("output_relative")
        val NAMING = stringPreferencesKey("naming")
        val CONFLICT = stringPreferencesKey("conflict")
        val SAVE_COVER = booleanPreferencesKey("save_cover")
        val RECORD_HISTORY = booleanPreferencesKey("record_history")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            theme = preferences[Keys.THEME] ?: "System",
            outputTreeUri = preferences[Keys.OUTPUT_TREE],
            outputRelativePath = preferences[Keys.OUTPUT_RELATIVE] ?: "Music/NCM Converter",
            namingMode = preferences[Keys.NAMING] ?: "Metadata",
            conflictPolicy = preferences[Keys.CONFLICT] ?: "Rename",
            saveCover = preferences[Keys.SAVE_COVER] ?: false,
            recordHistory = preferences[Keys.RECORD_HISTORY] ?: true
        )
    }

    suspend fun updateTheme(value: String) = edit { it[Keys.THEME] = value }

    suspend fun updateOutputTree(uri: String?) = edit {
        if (uri == null) it.remove(Keys.OUTPUT_TREE) else it[Keys.OUTPUT_TREE] = uri
    }

    suspend fun updateNaming(value: String) = edit { it[Keys.NAMING] = value }

    suspend fun updateConflict(value: String) = edit { it[Keys.CONFLICT] = value }

    suspend fun updateSaveCover(value: Boolean) = edit { it[Keys.SAVE_COVER] = value }

    suspend fun updateRecordHistory(value: Boolean) = edit { it[Keys.RECORD_HISTORY] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
