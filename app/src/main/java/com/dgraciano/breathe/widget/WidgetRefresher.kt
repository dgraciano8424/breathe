package com.dgraciano.breathe.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the widget's static refresh, so callers that record
 * interventions don't need a Context and can be tested without one.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun refresh() = PauseCountWidget.refresh(context)
}
