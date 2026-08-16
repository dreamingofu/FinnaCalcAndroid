package com.finnacalc.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.finnacalc.android.core.designsystem.DesignSystemGallery
import com.finnacalc.android.core.designsystem.FinnaTheme
import com.finnacalc.android.core.designsystem.Theme

/**
 * App entry point. Native Kotlin + Jetpack Compose port of FinnaCalcIOS.
 *
 * Phase 1: shows the design-system gallery for QA. The tab shell (RootView)
 * replaces this in Phase 2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinnaTheme {
                DesignSystemGallery(
                    Modifier
                        .fillMaxSize()
                        // Paint the page color under the system bars too, so
                        // the edge-to-edge gutters read as one surface.
                        .background(Theme.colors.background)
                        .safeDrawingPadding(),
                )
            }
        }
    }
}
