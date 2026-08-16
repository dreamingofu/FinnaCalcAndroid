//
// SubscriptionNotifier.kt
//
// Port of iOS Core/Notifications/SubscriptionNotifier.swift — local reminders
// before a recurring subscription charges: one day ahead for weekly and
// every-2-weeks charges, three for monthly and longer (ChargeCadence.leadDays).
//
// Two reminder sources merge, and the list itself is already built by
// SubscriptionReminders (ported in Phase 4c): detected bank subscriptions, and
// expenses the user tagged with a charge schedule. This is the scheduling half.
//
// No server or push is involved. Reminders are recomputed and rescheduled
// every time the app becomes active, so they always reflect the current
// budget — which is also what makes deleting a budget item reschedule
// correctly, the app's standing rule.
//
// Deviation from iOS: iOS uses UNCalendarNotificationTrigger; Android schedules
// a one-shot WorkManager job per reminder, tagged so the whole set can be
// cancelled and rebuilt in one call. WorkManager is deliberate over
// AlarmManager — a day-ahead reminder doesn't need exact-alarm permission, and
// asking for one to deliver a bill nudge would be the wrong trade.
//

package com.finnacalc.android.core.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.finnacalc.android.core.util.JsonPrefs
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** The shape the notifier needs, so it doesn't depend on the budgeting layer. */
data class PendingReminder(
    val id: String,
    val name: String,
    val nextCharge: LocalDateTime,
    val leadDays: Int,
    val remind: Boolean,
)

object SubscriptionNotifier {

    private const val IGNORED_KEY = "finnacalc.subReminders.ignored"
    private const val ENABLED_KEY = "finnacalc.subReminders.enabled"
    private const val WORK_TAG = "finnacalc.subReminder"
    const val ID_PREFIX = "finnacalc.subReminder."

    /** Reminders fire in the morning, not at midnight. */
    private val FIRE_TIME: LocalTime = LocalTime.of(9, 0)

    // MARK: Master switch + ignore list

    var enabled: Boolean
        get() = JsonPrefs.load<Boolean>(ENABLED_KEY) ?: true
        set(value) = JsonPrefs.persist(value, ENABLED_KEY)

    fun ignoredIds(): Set<String> = JsonPrefs.load<List<String>>(IGNORED_KEY)?.toSet() ?: emptySet()

    fun isIgnored(id: String): Boolean = ignoredIds().contains(id)

    fun setIgnored(id: String, ignored: Boolean) {
        val next = if (ignored) ignoredIds() + id else ignoredIds() - id
        JsonPrefs.persist(next.toList().sorted(), IGNORED_KEY)
    }

    // MARK: Scheduling

    /**
     * The reminders that should actually be scheduled, and when each fires.
     * Pure, so the whole rule is testable: the master switch, the per-item
     * `remind` flag, the ignore list, and dropping anything whose lead time
     * has already passed (a reminder for yesterday is noise, not a warning).
     */
    fun plan(
        reminders: List<PendingReminder>,
        now: LocalDateTime,
        enabled: Boolean = this.enabled,
        ignored: Set<String> = ignoredIds(),
    ): List<Pair<PendingReminder, LocalDateTime>> {
        if (!enabled) return emptyList()
        return reminders
            .filter { it.remind && !ignored.contains(it.id) }
            .mapNotNull { reminder ->
                val fire = reminder.nextCharge
                    .toLocalDate()
                    .minusDays(reminder.leadDays.toLong())
                    .atTime(FIRE_TIME)
                if (fire.isAfter(now)) reminder to fire else null
            }
    }

    /** "tomorrow" / "in 3 days" — the phrase the body ends with. */
    fun leadPhrase(days: Int): String = if (days == 1) "tomorrow" else "in $days days"

    /**
     * What the notification says. The NAME is the title: a notification list
     * shows titles, and "Upcoming charge" three times over told the user
     * nothing about which. Trimmed, because a merchant name arrives however
     * the bank spelled it.
     *
     * No amount. What a subscription costs varies (a plan change, a price
     * rise, tax), and "about $15.49" was a guess printed as a fact; the charge
     * date is the part that is actually known.
     */
    fun content(reminder: PendingReminder): Pair<String, String> =
        reminder.name.trim() to "You'll be charged for this ${leadPhrase(reminder.leadDays)}."

    /** Cancels every scheduled reminder and rebuilds from the current budget. */
    fun reconcile(context: Context, reminders: List<PendingReminder>, now: LocalDateTime = LocalDateTime.now()) {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(WORK_TAG)

        plan(reminders, now).forEach { (reminder, fire) ->
            val delay = Duration.between(now, fire)
            val (title, body) = content(reminder)
            val request = OneTimeWorkRequestBuilder<SubscriptionReminderWorker>()
                .setInitialDelay(delay)
                .addTag(WORK_TAG)
                .setInputData(
                    Data.Builder()
                        .putString(SubscriptionReminderWorker.KEY_ID, ID_PREFIX + reminder.id)
                        .putString(SubscriptionReminderWorker.KEY_TITLE, title)
                        .putString(SubscriptionReminderWorker.KEY_BODY, body)
                        .build()
                )
                .build()
            manager.enqueueUniqueWork(ID_PREFIX + reminder.id, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Posts one reminder when its delay elapses. */
class SubscriptionReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(KEY_BODY) ?: return Result.success()
        Notifier.post(id, title, body, NotificationChannelId.Bills, applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
