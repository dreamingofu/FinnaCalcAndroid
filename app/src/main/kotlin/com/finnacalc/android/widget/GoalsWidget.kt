//
// GoalsWidget.kt
//
// Port of the iOS GoalsWidget extension as a Glance app widget: the savings
// goals closest to completion, with their progress bars.
//
// iOS reads a snapshot the app writes into a shared App Group container,
// because a widget extension is a separate process with no access to the app's
// UserDefaults. Android's widget runs in the host launcher's process for the
// same reason, so the same design applies: the app publishes a snapshot
// (GoalsSnapshotStore) whenever goals change, and the widget only ever reads
// that.
//
// Nothing is invented: with no goals saved, the widget says so and invites,
// rather than drawing an empty ring that looks like zero progress.
//

package com.finnacalc.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.finnacalc.android.MainActivity
import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.serialization.Serializable

// MARK: - Snapshot

@Serializable
data class GoalSnapshot(
    val id: String,
    val name: String,
    val current: Double,
    val target: Double,
) {
    val fraction: Double get() = if (target > 0) (current / target).coerceIn(0.0, 1.0) else 0.0
}

/**
 * What the widget reads. Written by the app whenever goals change, so the
 * widget never has to reach into the app's own stores.
 */
object GoalsSnapshotStore {
    private const val KEY = "finnacalc.widget.goals"

    fun publish(goals: List<GoalSnapshot>) {
        // Closest to completion first, capped — a widget has room for four.
        JsonPrefs.persist(goals.sortedByDescending { it.fraction }.take(4), KEY)
    }

    fun load(): List<GoalSnapshot> = JsonPrefs.load<List<GoalSnapshot>>(KEY) ?: emptyList()
}

// MARK: - Widget

class GoalsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // JsonPrefs is process-wide; the widget host may not have run the app's
        // Application class, so bind it here before reading.
        JsonPrefs.init(context)
        val goals = GoalsSnapshotStore.load()
        provideContent { GlanceTheme { WidgetBody(goals) } }
    }
}

class GoalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsWidget()
}

@Composable
private fun WidgetBody(goals: List<GoalSnapshot>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "Goals",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))

        if (goals.isEmpty()) {
            Text(
                "No goals yet — set one in FinnaCalc and its progress shows here.",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            return@Column
        }

        goals.forEach { goal ->
            GoalRow(goal)
            Spacer(GlanceModifier.height(8.dp))
        }
    }
}

@Composable
private fun GoalRow(goal: GoalSnapshot) {
    Column(GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                goal.name,
                maxLines = 1,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "${(goal.fraction * 100).toInt()}%",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        ProgressBar(goal.fraction)
    }
}

/**
 * A two-box bar rather than a real progress widget: Glance has no fractional
 * width modifier, so the filled portion is expressed as a weight split, which
 * every launcher renders identically.
 */
@Composable
private fun ProgressBar(fraction: Double) {
    val filled = fraction.coerceIn(0.0, 1.0).toFloat()
    Row(GlanceModifier.fillMaxWidth().height(6.dp)) {
        if (filled > 0f) {
            Spacer(
                GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(Color(0xFF0CA678))
            )
        }
        if (filled < 1f) {
            // The remainder, sized by the inverse weight.
            Spacer(
                GlanceModifier
                    .width(((1f - filled) * 120).dp)
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
            )
        }
    }
}
