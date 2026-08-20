package com.tonic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tonic.core.ui.theme.TonicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TonicTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Edge-to-edge paints the theme's background under the system bars, but CONTENT must
                    // not sit under them - the practice screen's header row was rendering underneath the
                    // status-bar clock, visible in a live screenshot. One inset pad here covers every
                    // screen; the Surface behind it still fills the window, so the bars stay seamless.
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        TonicNavGraph()
                    }
                }
            }
        }
    }
}
