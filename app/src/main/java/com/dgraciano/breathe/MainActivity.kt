package com.dgraciano.breathe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dgraciano.breathe.ui.nav.BreatheNavGraph
import com.dgraciano.breathe.ui.theme.BreatheTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BreatheTheme {
                BreatheNavGraph()
            }
        }
    }
}
