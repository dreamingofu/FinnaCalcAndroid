//
// FinnaApp.kt
//
// Application entry — owns the app-scoped singletons that iOS creates as
// @StateObject in FinnaCalcIOSApp: the AuthManager (one instance for the whole
// app) and the appearance store. Wires the Supabase access token into the API
// client as a Bearer, exactly like the iOS app's launch task.
//

package com.finnacalc.android

import android.app.Application
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.AppearanceStore
import com.finnacalc.android.core.networking.ApiClient
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.features.budgeting.BudgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FinnaApp : Application() {
    /** App-lifetime scope for long-lived collectors (auth state, stores). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var auth: AuthManager
        private set
    lateinit var appearanceStore: AppearanceStore
        private set

    /**
     * Shared budget/goals store — one instance for the whole app so the Home
     * tab's Expenses & Goals cards and the Budgeting tab stay in sync
     * (mirrors the iOS @StateObject at the app root).
     */
    lateinit var budget: BudgetStore
        private set

    override fun onCreate() {
        super.onCreate()
        JsonPrefs.init(this)
        auth = AuthManager(scope = appScope)
        appearanceStore = AppearanceStore(this)
        budget = BudgetStore()
        // Forward the Supabase access token to the API client as a Bearer.
        ApiClient.shared.tokenProvider = { auth.accessToken() }
    }
}
