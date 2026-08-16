package com.finnacalc.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.finnacalc.android.app.RootView
import com.finnacalc.android.core.designsystem.AppearanceSetting
import com.finnacalc.android.core.designsystem.FinnaTheme
import kotlinx.coroutines.launch

/**
 * App entry point. Native Kotlin + Jetpack Compose port of FinnaCalcIOS.
 * The app-scoped singletons (auth, appearance) live on [FinnaApp]; this
 * activity hosts the shell and forwards OAuth deep links to the auth client.
 */
class MainActivity : ComponentActivity() {
    private val app get() = application as FinnaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A cold start from the OAuth redirect delivers the link here.
        app.auth.handleDeeplink(intent)
        setContent {
            val appearance by app.appearanceStore.setting.collectAsState(AppearanceSetting.System)
            val scope = rememberCoroutineScope()
            FinnaTheme(darkTheme = appearance.resolvesToDark()) {
                RootView(
                    auth = app.auth,
                    appearance = appearance,
                    onAppearanceChange = { scope.launch { app.appearanceStore.set(it) } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A warm app receiving finnacalc://auth-callback lands here.
        app.auth.handleDeeplink(intent)
    }
}
