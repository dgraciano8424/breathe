package com.dgraciano.breathe

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.dgraciano.breathe.data.repository.ChoiceRecorder
import javax.inject.Inject

@HiltAndroidApp
class BreatheApp : Application() {
    @Inject lateinit var choiceRecorder: ChoiceRecorder

    override fun onCreate() {
        super.onCreate()
        choiceRecorder.retryPending()
    }
}
