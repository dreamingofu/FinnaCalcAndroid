//
// AppLifecycle.kt
//
// The Android stand-in for the iOS app's scenePhase observer: the work that
// has to happen whenever the app becomes active.
//
//   · Bill reminders are recomputed and rescheduled, so they always reflect
//     the current budget — which is what makes deleting a budget item
//     reschedule correctly, the app's standing rule.
//   · The feedback timer accumulates foreground time toward its ~5 minute
//     prompt (SessionFeedback).
//
// iOS gets ScenePhase from SwiftUI; Compose has no equivalent, so this hooks
// the activity's own lifecycle.
//

package com.finnacalc.android.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.finnacalc.android.core.feedback.SessionFeedback
import com.finnacalc.android.core.notifications.PendingReminder
import com.finnacalc.android.core.notifications.SubscriptionNotifier
import com.finnacalc.android.features.budgeting.BudgetStore
import com.finnacalc.android.features.budgeting.SubscriptionReminders

@Composable
fun AppLifecycleEffects(context: Context, budget: BudgetStore) {
    val owner = LocalLifecycleOwner.current
    val currentBudget by rememberUpdatedState(budget)

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    SessionFeedback.onResumed(System.currentTimeMillis())
                    reconcileReminders(context, currentBudget)
                }

                Lifecycle.Event.ON_PAUSE -> SessionFeedback.onPaused(System.currentTimeMillis())
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Rebuilds the whole reminder set from the budget as it stands right now. */
fun reconcileReminders(context: Context, budget: BudgetStore) {
    val pending = SubscriptionReminders.build(budget).map {
        PendingReminder(
            id = it.id,
            name = it.name,
            nextCharge = it.nextCharge,
            leadDays = it.cadence.leadDays,
            remind = it.remind,
        )
    }
    SubscriptionNotifier.reconcile(context, pending)
}
