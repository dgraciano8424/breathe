package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.InterventionEventDao
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.service.UsageAccessReader
import com.dgraciano.breathe.widget.WidgetRefresher
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a pause decision before attempting optional usage-based enrichment.
 *
 * The generated ID keeps a later estimate attached to the exact decision that was committed.
 */
@Singleton
class InterventionDecisionRepository @Inject constructor(
    private val dao: InterventionEventDao,
    private val usage: UsageAccessReader,
    private val widget: WidgetRefresher,
) : PauseDecisionSink {

    override suspend fun record(decision: PauseDecision): Result<Long> {
        val eventId = try {
            dao.insert(decision.toEvent())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        }

        widget.refresh()
        if (decision.outcome == DecisionOutcome.DECLINED) {
            enrichDecline(eventId, decision.packageName)
        }
        return Result.success(eventId)
    }

    private suspend fun enrichDecline(eventId: Long, packageName: String) {
        try {
            val minutesSaved = usage.averageSessionMinutes(packageName)
            dao.updateMinutesSaved(eventId, minutesSaved)
            widget.refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The initial decision is already durable; usage is optional enrichment only.
        }
    }

    private fun PauseDecision.toEvent() = InterventionEvent(
        packageName = packageName,
        appName = appName,
        outcome = outcome.name,
        reason = reason,
        minutesSaved = 0,
    )
}
