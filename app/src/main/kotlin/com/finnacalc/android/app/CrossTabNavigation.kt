//
// CrossTabNavigation.kt
//
// Port of the iOS NotificationCenter cross-tab posts (.finnaSwitchTab /
// .finnaAskChat) onto a shared event bus. A cross-tab link (e.g. "Manage →",
// "Portfolio →") emits switchTab; a page that wants FinnaBot to look at
// something emits askChat. RootView collects both.
//

package com.finnacalc.android.app

import kotlinx.coroutines.flow.MutableSharedFlow

object CrossTabNavigation {
    /** Tab ids: "home", "budgeting", "investing", "taxes", "education". */
    val switchTab = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * A question for FinnaBot, typed into the shell's one conversation and not
     * sent, so the thread stays single and the user sees what's about to go.
     */
    val askChat = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
