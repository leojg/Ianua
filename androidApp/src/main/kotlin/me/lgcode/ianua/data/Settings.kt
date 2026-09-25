package me.lgcode.ianua.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** v0.1 has one setting: the on/off switch. */
class Settings(private val context: Context) {
    val enabled: Flow<Boolean> = context.dataStore.data.map { it[ENABLED] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
