package com.dgraciano.breathe.data.repository

import android.util.Log
import com.dgraciano.breathe.data.db.PendingChoiceDao
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.PendingChoice
import com.dgraciano.breathe.di.ApplicationScope
import com.dgraciano.breathe.di.IoDispatcher
import com.dgraciano.breathe.service.SessionTimeHelper
import com.dgraciano.breathe.widget.WidgetRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Durable inbox: enqueue before navigation, deliver in one Room transaction. */
@Singleton
class ChoiceRecorder @Inject constructor(
    private val dao: PendingChoiceDao,
    private val sessionTimeHelper: SessionTimeHelper,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope appScope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val signals = Channel<Unit>(Channel.CONFLATED)

    init {
        appScope.launch {
            for (signal in signals) {
                var retryDelay = 1_000L
                while (true) {
                    try {
                        drain()
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e("ChoiceRecorder", "History delivery will retry", error)
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(60_000)
                    }
                }
            }
        }
    }

    suspend fun enqueue(choice: PendingChoice) {
        withContext(NonCancellable + io) { dao.enqueue(choice) }
        retryPending()
    }

    /** Process startup also covers starts by the accessibility service or widget. */
    fun retryPending() { signals.trySend(Unit) }

    private suspend fun drain() {
        while (true) {
            val pending = dao.next() ?: return
            val minutes = if (pending.outcome == InterventionEvent.OUTCOME_DECLINED) {
                try { sessionTimeHelper.getAvgSessionMinutes(pending.packageName) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { 20 }
            } else 0
            if (dao.deliver(pending.choiceId, minutes)) widgetRefresher.refresh()
        }
    }
}
