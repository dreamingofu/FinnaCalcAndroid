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
    /**
     * A page the destination tab should open on arrival, set just before the
     * switch. The iOS analogue is BudgetingLaunch.pendingPage /
     * InvestingLaunch.pendingTab — a one-shot the tab reads and clears.
     */
    var pendingBudgetingPage: String? = null
    var pendingInvestingTab: String? = null

    /** Switch tabs, optionally asking the destination to open a given page. */
    fun request(tab: String, page: String? = null, tabName: String? = null) {
        if (page != null) pendingBudgetingPage = page
        if (tabName != null) pendingInvestingTab = tabName
        switchTab.tryEmit(tab)
    }

    /** Tab ids: "home", "budgeting", "investing", "taxes", "education". */
    val switchTab = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * A question for FinnaBot, typed into the shell's one conversation and not
     * sent, so the thread stays single and the user sees what's about to go.
     */
    val askChat = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
