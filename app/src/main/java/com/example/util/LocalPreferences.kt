package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cbtPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "cbt_local_prefs")

class LocalPreferences(private val context: Context) {
    private val lastServerUrlKey = stringPreferencesKey("last_server_url")
    private val teacherPinKey = stringPreferencesKey("teacher_pin")

    val lastServerUrl: Flow<String> = context.cbtPrefsDataStore.data.map { prefs ->
        prefs[lastServerUrlKey] ?: ""
    }

    val teacherPin: Flow<String> = context.cbtPrefsDataStore.data.map { prefs ->
        prefs[teacherPinKey] ?: ""
    }

    suspend fun saveLastServerUrl(url: String) {
        context.cbtPrefsDataStore.edit { it[lastServerUrlKey] = url.trim() }
    }

    suspend fun saveTeacherPin(pin: String) {
        context.cbtPrefsDataStore.edit { it[teacherPinKey] = pin.trim() }
    }
}
