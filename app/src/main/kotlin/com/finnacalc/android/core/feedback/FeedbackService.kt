//
// FeedbackService.kt
//
// Port of iOS Core/Feedback/FeedbackService.swift and the cooldown half of
// SessionFeedbackCoordinator.swift. Submits to /api/feedback, which emails the
// app owner, and tracks the 30-day quiet period for the automatic prompt.
//
// Deviation from iOS: the coordinator there runs a foreground timer that
// accumulates ~5 minutes of active use before requesting the prompt. Here the
// same accounting is driven by the shell's lifecycle observer (see
// SessionFeedback.onResumed / onPaused), because Android has no ScenePhase.
//

package com.finnacalc.android.core.feedback

import android.content.Context
import android.content.pm.PackageManager
import com.finnacalc.android.core.networking.ApiClient
import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(
    val message: String,
    val rating: Int? = null,
    val email: String? = null,
    val userId: String? = null,
    val appVersion: String? = null,
    val source: String,
)

object FeedbackService {
    suspend fun submit(request: FeedbackRequest) {
        ApiClient.shared.postData("/api/feedback", ApiClient.shared.json.encodeToString(request))
    }

    /** "1.0 (1)" — set once from the app shell, since Android needs a Context. */
    var appVersion: String? = null
        private set

    fun readVersion(context: Context) {
        appVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else
                @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName} ($code)"
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

/**
 * The automatic prompt: ~5 minutes of foreground use asks once, then goes
 * quiet for 30 days. The cooldown is stamped when the prompt is SHOWN (or a
 * submission succeeds), not when it's requested, so a prompt that never
 * appeared never burns the quiet period.
 */
object SessionFeedback {
    private const val THRESHOLD_SECONDS = 5 * 60L
    private const val COOLDOWN_DAYS = 30L
    private const val LAST_PROMPT_KEY = "finnacalc.feedback.lastPrompt"

    private val _showPrompt = MutableStateFlow(false)
    val showPrompt: StateFlow<Boolean> = _showPrompt.asStateFlow()

    private var accumulatedSeconds = 0L
    private var activeSince: Long? = null

    fun onResumed(nowMillis: Long) {
        if (activeSince == null) activeSince = nowMillis
    }

    fun onPaused(nowMillis: Long) {
        activeSince?.let { accumulatedSeconds += (nowMillis - it) / 1000 }
        activeSince = null
    }

    /** Called on a timer while the app is in the foreground. */
    fun tick(nowMillis: Long) {
        val active = activeSince?.let { (nowMillis - it) / 1000 } ?: 0
        if (accumulatedSeconds + active < THRESHOLD_SECONDS) return
        if (!isEligible(nowMillis)) return
        _showPrompt.value = true
    }

    fun dismiss() {
        _showPrompt.value = false
    }

    fun isEligible(nowMillis: Long): Boolean {
        val last = JsonPrefs.load<Long>(LAST_PROMPT_KEY) ?: return true
        return nowMillis - last >= COOLDOWN_DAYS * 24 * 60 * 60 * 1000
    }

    fun stampCooldown(nowMillis: Long = System.currentTimeMillis()) {
        JsonPrefs.persist(nowMillis, LAST_PROMPT_KEY)
        _showPrompt.value = false
    }
}
