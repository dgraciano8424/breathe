package com.dgraciano.breathe.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dgraciano.breathe.ui.pause.DEFAULT_PAUSE_SECONDS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "breathe_settings")

/**
 * User preferences. The app previously had no preference storage of any kind — pause
 * length and breathing pace were compile-time constants and there was no way to turn
 * monitoring off.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val pauseSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAUSE_SECONDS] ?: DEFAULT_PAUSE_SECONDS
    }

    suspend fun setPauseSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_PAUSE_SECONDS] = seconds.coerceIn(MIN_PAUSE, MAX_PAUSE) }
    }

    companion object {
        private val KEY_PAUSE_SECONDS = intPreferencesKey("pause_seconds")
        const val MIN_PAUSE = 3
        const val MAX_PAUSE = 30
        val PAUSE_OPTIONS = listOf(3, 5, 8, 15, 30)
    }
}
