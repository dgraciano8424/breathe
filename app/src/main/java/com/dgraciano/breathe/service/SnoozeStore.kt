package com.dgraciano.breathe.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.dgraciano.breathe.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A local deadline, independent of app choices, history and permission grants. */
@Singleton
class SnoozeStore internal constructor(
    private val preferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Inject constructor(@ApplicationContext context: Context, @IoDispatcher dispatcher: CoroutineDispatcher) :
        this(context.getSharedPreferences("breathe_snooze", Context.MODE_PRIVATE), dispatcher)

    private val savedDeadline = MutableStateFlow(preferences.getLong(KEY, 0).coerceAtLeast(0))
    private val writes = Mutex()
    val currentDeadline: Long get() = savedDeadline.value.takeIf { it > now() } ?: 0
    fun refreshExpiry() {
        val until = savedDeadline.value
        if (until > 0 && until <= now()) savedDeadline.compareAndSet(until, 0)
    }
    fun isSnoozed(): Boolean { refreshExpiry(); return currentDeadline > 0 }

    // Timers only run while a screen or the accessibility service observes the deadline.
    // Event handling independently checks the clock, including after device sleep.
    @OptIn(ExperimentalCoroutinesApi::class)
    val deadline = savedDeadline.flatMapLatest { until ->
        flow {
            while (until > now()) {
                emit(until)
                delay((until - now()).coerceIn(1, 60_000))
            }
            emit(0L)
        }
    }.distinctUntilChanged()

    suspend fun snooze(minutes: Int) {
        require(minutes in listOf(15, 30, 60))
        save(now() + minutes * 60_000L)
    }
    suspend fun resume() = save(0)

    // Disk writes run on IO; an explicit success result is needed before updating status.
    @SuppressLint("ApplySharedPref", "UseKtx")
    private suspend fun save(until: Long) = withContext(NonCancellable) {
        writes.withLock {
            val previous = savedDeadline.value
            withContext(ioDispatcher) {
                if (!preferences.edit().putLong(KEY, until).commit()) {
                    // SharedPreferences may update its memory even when its disk write fails.
                    preferences.edit().putLong(KEY, previous).commit()
                    throw IOException("Could not save the snooze setting")
                }
            }
            savedDeadline.value = until
        }
    }

    private companion object { const val KEY = "until" }
}
