//
// BudgetAdvisorReport.kt
//
// Port of the AI half of iOS Features/Budgeting/BudgetAdvisorView.swift: the
// snapshot payload sent to /api/budget-advisor, the seed prompt, the streamed
// report with its one silent retry, the follow-up conversation, and the
// snapshot-keyed cache that restores a report instead of re-running the model.
//
// The snapshot is the same shape and the same rounding as iOS (JS Math.round,
// half toward +∞), with the same deliberate sorting: these arrays come out of
// map groupings whose order would otherwise change per launch, and an
// order-shuffled snapshot misses the cache and re-runs the model with the
// budget untouched.
//
// dataNotes tells the model where the figures come from and what genuinely
// can't be seen, so a zero it can't explain doesn't become an assertion.
//

package com.finnacalc.android.features.budgeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finnacalc.android.core.networking.ApiClient
import com.finnacalc.android.core.util.JsonPrefs
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// MARK: - Snapshot payload (mirrors the web `snapshot` object)

@Serializable
data class AdvisorExpenseSlice(val category: String, val amount: Int, val pctOfIncome: Double?)

@Serializable
data class AdvisorIncomeSlice(val source: String, val amount: Int)

@Serializable
data class AdvisorGoal(
    val name: String,
    val target: Double,
    val saved: Double,
    val monthlyContribution: Double,
    val targetDate: String,
    val pctComplete: Int,
)

@Serializable
data class AdvisorCategoryChange(
    val category: String,
    val currentPerMonth: Int,
    val previousPerMonth: Int,
    val pctChange: Double,
    val comparedTo: String,
)

@Serializable
data class AdvisorLine(val name: String, val category: String, val type: String, val perMonth: Int)

@Serializable
data class AdvisorSnapshot(
    val budgetType: String,
    val monthlyIncome: Int,
    val monthlyExpenses: Int,
    val monthlyNet: Int,
    val savingsRatePct: Double,
    val expenseByCategory: List<AdvisorExpenseSlice>,
    val incomeByCategory: List<AdvisorIncomeSlice>,
    val savingsGoals: List<AdvisorGoal>,
    val totalSavedAcrossGoals: Int,
    /**
     * 0 when no goal is labeled as the emergency fund — see dataNotes, which
     * says whether that zero means "none" or "unknown".
     */
    val emergencyFundMonthsCovered: Double,
    /** Category swings over the window My Budget has open, largest first. */
    val categoryChanges: List<AdvisorCategoryChange>,
    /** The budget's individual lines, largest first, capped. */
    val lineItems: List<AdvisorLine>,
    /** Where these figures come from and what we genuinely cannot see. */
    val dataNotes: List<String>,
)

@Serializable
private data class AdvisorMessageDTO(val role: String, val content: String)

@Serializable
private data class AdvisorRequest(
    val snapshot: AdvisorSnapshot,
    val depth: String,
    val messages: List<AdvisorMessageDTO>,
)

// MARK: - Local types

enum class AdvisorDepth(val raw: String) { Quick("quick"), Deep("deep") }

enum class AdvisorRole(val raw: String) { User("user"), Assistant("assistant") }

data class AdvisorMessage(
    val id: String,
    val role: AdvisorRole,
    val content: String,
    /**
     * True for the prompt runAnalysis writes on the user's behalf. Those are
     * hidden from the transcript; anything the user actually typed is not.
     */
    val isGeneratedPrompt: Boolean = false,
)

// MARK: - Snapshot builder

/** JS Math.round (half toward +∞): floor(x + 0.5). */
private fun jsRound(x: Double): Double = floor(x + 0.5)

fun buildAdvisorSnapshot(store: BudgetStore): AdvisorSnapshot {
    val income = store.monthlyIncome
    val expenses = store.monthlyExpenses
    val net = store.monthlyNet
    // The advisor uses net/income, NOT the Savings-category rate.
    val savingsRate = if (income > 0) (net / income) * 100 else 0.0
    val ledger = BankLedgerStore.shared

    val items = store.currentItems
    val moves = BudgetFindings.categoryMoves(store)
    val knownEmergencyMonths = BudgetFindings.emergencyMonths(store)

    // Plain sentences about provenance and blind spots, for the model. It has
    // repeatedly needed telling that a zero can mean "we can't see it".
    val notes = mutableListOf<String>()
    notes.add(
        when {
            ledger.isReadingBank ->
                "Figures come from a linked bank account's transactions for the period the user has " +
                    "open, not a hand-typed plan."

            ledger.hasAdopted(store.budgetType) ->
                "The user has a linked bank, but the open budget is a hand-typed plan."

            else ->
                "Figures are a hand-typed budget. There is no linked bank, so real transactions, exact " +
                    "dates and month-to-month trends are not visible."
        }
    )
    if (knownEmergencyMonths == null) {
        notes.add(
            "Whether an emergency fund exists is unknown: no goal is labeled as one. Do not assume the " +
                "user lacks one; suggest labeling a goal so it can be tracked."
        )
    }
    if (moves.isEmpty()) {
        notes.add(
            "No month-over-month comparison is available, so do not claim spending rose or fell versus " +
                "earlier months."
        )
    }
    if (items.size > 40) {
        notes.add("lineItems lists the 40 largest lines of ${items.size}; the category totals cover the rest.")
    }
    val contribution = BudgetFindings.emergencyContribution(store)
    if (contribution > 0 && knownEmergencyMonths == null) {
        notes.add(
            "The budget has a line named for an emergency fund contributing about " +
                "$${jsRound(contribution).toInt()}/mo. The accumulated balance is not visible; do not " +
                "claim the user has no emergency fund."
        )
    }
    notes.add("All figures are normalised to per-month amounts.")
    // The app prints one disclaimer at the foot of the page. The model was
    // adding its own to the report AND to every follow-up reply, so a short
    // conversation ended up carrying four of them.
    notes.add(
        "The app already shows a standing 'AI-generated, not financial advice' notice. Do not add a " +
            "disclaimer, a 'consult a professional' line, or any similar closing caveat to your answers."
    )

    return AdvisorSnapshot(
        budgetType = store.budgetType.raw,
        monthlyIncome = jsRound(income).toInt(),
        monthlyExpenses = jsRound(expenses).toInt(),
        monthlyNet = jsRound(net).toInt(),
        savingsRatePct = jsRound(savingsRate * 10) / 10,
        expenseByCategory = store.expenseByCategory
            .sortedWith(compareByDescending<CategorySlice> { it.value }.thenBy { it.name })
            .map {
                AdvisorExpenseSlice(
                    category = it.name,
                    amount = jsRound(it.value).toInt(),
                    pctOfIncome = if (income > 0) jsRound((it.value / income) * 1000) / 10 else null,
                )
            },
        incomeByCategory = store.incomeByCategory
            .sortedWith(compareByDescending<CategorySlice> { it.value }.thenBy { it.name })
            .map { AdvisorIncomeSlice(it.name, jsRound(it.value).toInt()) },
        savingsGoals = store.currentGoals.map { g ->
            val measured = GoalProgress.measure(g, BankLedgerStore.shared).first
            AdvisorGoal(
                name = g.name,
                target = g.targetAmount,
                saved = measured,
                monthlyContribution = g.monthlyContribution,
                targetDate = g.targetDate,
                pctComplete = if (g.targetAmount > 0) jsRound((measured / g.targetAmount) * 100).toInt() else 0,
            )
        },
        totalSavedAcrossGoals = jsRound(BudgetFindings.measuredGoalTotal(store)).toInt(),
        emergencyFundMonthsCovered = jsRound((knownEmergencyMonths ?: 0.0) * 10) / 10,
        categoryChanges = moves.take(3).map {
            AdvisorCategoryChange(
                category = it.category,
                currentPerMonth = jsRound(it.current).toInt(),
                previousPerMonth = jsRound(it.previous).toInt(),
                pctChange = jsRound(it.change * 10) / 10,
                comparedTo = "${it.currentLabel} vs ${it.previousLabel}",
            )
        },
        // Up to 40 lines, largest first with a stable tiebreak. Enough for any
        // hand-typed budget; a bank view's long tail is summarised by the
        // category totals it already has, and the cap is disclosed in dataNotes.
        lineItems = items
            .sortedWith(compareByDescending<BudgetItem> { it.monthlyAmount }.thenBy { it.id })
            .take(40)
            .map {
                AdvisorLine(
                    name = it.subcategory.ifEmpty { it.category },
                    category = it.category,
                    type = it.type.raw,
                    perMonth = jsRound(it.monthlyAmount).toInt(),
                )
            },
        dataNotes = notes,
    )
}

// MARK: - Cache

object AdvisorCacheStore {
    @Serializable
    data class StoredMessage(
        val id: String,
        val role: String,
        val content: String,
        val isGeneratedPrompt: Boolean,
    )

    @Serializable
    private data class Row(
        val signature: String,
        val messages: List<StoredMessage>,
        val depth: String,
    )

    private const val KEY = "finnacalc-advisor-cache"

    /**
     * A stable digest of the encoded snapshot. kotlinx.serialization writes
     * fields in declaration order, so equal snapshots encode to equal bytes.
     */
    fun signature(snapshot: AdvisorSnapshot): String {
        val encoded = runCatching { ApiClient.shared.json.encodeToString(snapshot) }.getOrNull() ?: return ""
        // FNV-1a, 64-bit: deterministic across launches, unlike hashCode.
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (byte in encoded.toByteArray()) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(hash)
    }

    fun save(signature: String, messages: List<StoredMessage>, depth: String) {
        JsonPrefs.persist(Row(signature, messages, depth), KEY)
    }

    fun load(signature: String): Pair<List<StoredMessage>, String>? {
        val row = JsonPrefs.load<Row>(KEY) ?: return null
        if (row.signature != signature || row.messages.isEmpty()) return null
        return row.messages to row.depth
    }
}

// MARK: - View model

class BudgetAdvisorViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<AdvisorMessage>>(emptyList())
    val messages: StateFlow<List<AdvisorMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _depth = MutableStateFlow(AdvisorDepth.Quick)
    val depth: StateFlow<AdvisorDepth> = _depth.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** The signature the loaded conversation belongs to. */
    private var loadedSignature: String? = null
    private var streamJob: Job? = null

    fun setInput(text: String) {
        _input.value = text
    }

    /** Anything the user actually typed; the generated prompt stays hidden. */
    val visibleMessages: List<AdvisorMessage>
        get() = _messages.value.filterNot { it.isGeneratedPrompt }

    /**
     * The report runs itself, once per budget. A cache keyed on the snapshot
     * restores the whole conversation when the budget hasn't changed, so
     * leaving the tab or coming back tomorrow shows the same report instantly
     * instead of a fresh model call; changing the budget starts over.
     */
    fun startIfNeeded(snapshot: AdvisorSnapshot) {
        val signature = AdvisorCacheStore.signature(snapshot)
        if (loadedSignature == signature) return
        loadedSignature = signature
        streamJob?.cancel()

        val cached = AdvisorCacheStore.load(signature)
        if (cached != null) {
            _messages.value = cached.first.map {
                AdvisorMessage(
                    id = it.id,
                    role = if (it.role == "user") AdvisorRole.User else AdvisorRole.Assistant,
                    content = it.content,
                    isGeneratedPrompt = it.isGeneratedPrompt,
                )
            }
            _depth.value = if (cached.second == "deep") AdvisorDepth.Deep else AdvisorDepth.Quick
            _isLoading.value = false
            _error.value = null
        } else {
            runAnalysis(AdvisorDepth.Quick, snapshot)
        }
    }

    /** The refresh action ignores the cache on purpose. */
    fun runAnalysis(d: AdvisorDepth, snapshot: AdvisorSnapshot) {
        if (_isLoading.value) return
        _depth.value = d
        val seed = AdvisorMessage(
            id = UUID.randomUUID().toString(),
            role = AdvisorRole.User,
            content = seedPrompt(d, snapshot.monthlyNet),
            isGeneratedPrompt = true,
        )
        _messages.value = listOf(seed)
        stream(listOf(seed), d, snapshot)
    }

    fun send(snapshot: AdvisorSnapshot) {
        val trimmed = _input.value.trim()
        if (trimmed.isEmpty() || _isLoading.value) return
        val next = _messages.value +
            AdvisorMessage(UUID.randomUUID().toString(), AdvisorRole.User, trimmed)
        _messages.value = next
        _input.value = ""
        stream(next, _depth.value, snapshot)
    }

    fun retry(snapshot: AdvisorSnapshot) {
        if (_isLoading.value) return
        stream(_messages.value, _depth.value, snapshot)
    }

    fun stop() {
        streamJob?.cancel()
        _isLoading.value = false
    }

    /**
     * What the report is asked to cover. The base ask, plus the one thing the
     * numbers say is worth spending the answer on: money with nowhere to be,
     * or a gap that has to close before anything else is worth discussing.
     * Written here rather than left to the model, which otherwise reports the
     * surplus back as a compliment and moves on.
     */
    private fun seedPrompt(d: AdvisorDepth, net: Int): String {
        var ask = if (d == AdvisorDepth.Deep) {
            "Give me a full, deep analysis of my budget with your best personalized recommendations."
        } else {
            "Give me a quick, concise summary of my budget with the top quick wins."
        }
        if (net > 0) {
            ask += " About $$net a month is left unassigned. Cover zero-based budgeting: what it means " +
                "to give every dollar a job before the month starts, and what specifically to do with " +
                "that $$net, split across the things my own numbers point to, my goals, any debt " +
                "payments, and the emergency cushion. Name amounts and where they go, and rank them. " +
                "Say plainly that it is my call, and do not present any split as the correct one."
        } else if (net < 0) {
            ask += " I am spending about $${abs(net)} a month more than I bring in. Start with closing " +
                "that gap, using my own categories and the largest, most movable lines, before anything else."
        }
        return ask
    }

    private fun stream(
        history: List<AdvisorMessage>,
        d: AdvisorDepth,
        snapshot: AdvisorSnapshot,
        attempt: Int = 0,
    ) {
        streamJob?.cancel()
        _isLoading.value = true
        _error.value = null
        val assistantId = UUID.randomUUID().toString()

        val body = ApiClient.shared.json.encodeToString(
            AdvisorRequest(
                snapshot = snapshot,
                depth = d.raw,
                messages = history.map { AdvisorMessageDTO(it.role.raw, it.content) },
            )
        )

        streamJob = viewModelScope.launch {
            var appended = false
            var acc = ""
            var failure: String? = null

            ApiClient.shared.postTextStream("/api/budget-advisor", body)
                .catch { e -> failure = e.message ?: "Something went wrong." }
                .collect { text ->
                    acc = text
                    if (!appended) {
                        _messages.value = _messages.value +
                            AdvisorMessage(assistantId, AdvisorRole.Assistant, acc)
                        appended = true
                    } else {
                        _messages.value = _messages.value.map {
                            if (it.id == assistantId) it.copy(content = acc) else it
                        }
                    }
                }

            val failed = failure
            if (failed != null) {
                _messages.value = _messages.value.filterNot { it.id == assistantId }
                handleStreamFailure(history, d, snapshot, attempt, failed)
                return@launch
            }
            if (acc.isBlank()) {
                _messages.value = _messages.value.filterNot { it.id == assistantId }
                handleStreamFailure(
                    history, d, snapshot, attempt,
                    "No response received. Please try again.",
                )
                return@launch
            }

            _isLoading.value = false
            // Persist whatever the conversation now is, so leaving and coming
            // back shows this instead of re-running the model.
            if (_error.value == null) saveCache(snapshot)
        }
    }

    /**
     * One silent retry, then honesty. Streams drop for transient reasons often
     * enough that surfacing every first failure taught the user the feature
     * "sometimes doesn't work"; and when a refresh of a fresh report fails for
     * good, the saved report comes back rather than an empty card, so a working
     * answer is never traded for an error message.
     */
    private suspend fun handleStreamFailure(
        history: List<AdvisorMessage>,
        d: AdvisorDepth,
        snapshot: AdvisorSnapshot,
        attempt: Int,
        message: String,
    ) {
        if (attempt == 0) {
            delay(800)
            stream(history, d, snapshot, attempt = 1)
            return
        }
        _isLoading.value = false

        // A fresh report run (not a typed follow-up, whose question must stay
        // on screen): fall back to the saved report for this same budget.
        val wasFreshReport = history.size == 1 && history.firstOrNull()?.isGeneratedPrompt == true
        val cached = if (wasFreshReport) AdvisorCacheStore.load(AdvisorCacheStore.signature(snapshot)) else null
        if (cached != null) {
            _messages.value = cached.first.map {
                AdvisorMessage(
                    id = it.id,
                    role = if (it.role == "user") AdvisorRole.User else AdvisorRole.Assistant,
                    content = it.content,
                    isGeneratedPrompt = it.isGeneratedPrompt,
                )
            }
            _depth.value = if (cached.second == "deep") AdvisorDepth.Deep else AdvisorDepth.Quick
            _error.value = null
            return
        }
        _error.value = message
    }

    private fun saveCache(snapshot: AdvisorSnapshot) {
        AdvisorCacheStore.save(
            signature = AdvisorCacheStore.signature(snapshot),
            messages = _messages.value.map {
                AdvisorCacheStore.StoredMessage(it.id, it.role.raw, it.content, it.isGeneratedPrompt)
            },
            depth = _depth.value.raw,
        )
    }
}
