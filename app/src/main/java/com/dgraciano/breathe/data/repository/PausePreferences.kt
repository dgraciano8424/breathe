package com.dgraciano.breathe.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.dgraciano.breathe.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val MAX_REMINDER_LENGTH = 140

fun normalizeReminder(value: String): String {
    require(value.length <= MAX_REMINDER_LENGTH) { "Keep your reminder to 140 characters." }
    require(value.none { it.isISOControl() && !it.isWhitespace() }) { "Remove hidden control characters from your reminder." }
    return value.trim().replace(Regex("\\s+"), " ")
}

/** An optional global reminder, kept separate from the choice database and exports. */
@Singleton
class PausePreferences internal constructor(
    private val preferences: SharedPreferences,
    private val dispatcher: CoroutineDispatcher
) {
    @Inject constructor(@ApplicationContext context: Context, @IoDispatcher dispatcher: CoroutineDispatcher) :
        this(context.getSharedPreferences("breathe_pause_preferences", Context.MODE_PRIVATE), dispatcher)

    private val current = MutableStateFlow(runCatching {
        normalizeReminder(preferences.getString(KEY, "").orEmpty())
    }.getOrDefault(""))
    val reminder = current.asStateFlow()
    private val writes = Mutex()

    // Checked IO commit keeps the screen and the persisted setting consistent.
    @SuppressLint("ApplySharedPref", "UseKtx")
    suspend fun saveReminder(value: String) {
        val next = normalizeReminder(value)
        withContext(NonCancellable) {
            writes.withLock {
                val previous = current.value
                withContext(dispatcher) {
                    if (!preferences.edit().putString(KEY, next).commit()) {
                        preferences.edit().putString(KEY, previous).commit()
                        throw IOException("Could not save your reminder")
                    }
                }
                current.value = next
            }
        }
    }

    private companion object { const val KEY = "reminder" }
}
