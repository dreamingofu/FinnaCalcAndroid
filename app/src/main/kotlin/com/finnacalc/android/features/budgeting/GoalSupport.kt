//
// GoalSupport.kt
//
// Ports of iOS GoalProgress.swift (measurement + entitlement gate),
// GoalEmoji.swift (name-derived icon suggestions), GoalRing.swift (ring
// palette), and BudgetCategoryStyle.swift (per-category icon + tint +
// donut palette). Goal alert notifications land with the notification
// infrastructure in Phase 8.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// MARK: - Entitlements

/**
 * Plan gating, in one place (port of the iOS Entitlements enum). Until
 * billing goes live, a linked bank IS the paid-budgeting tier: spending and
 * income goals, and goal alerts, exist for connected users.
 */
object Entitlements {
    val paidBudgeting: Boolean get() = BankLedgerStore.shared.hasBank
}

// MARK: - Goal progress

object GoalProgress {

    /** The goal's current figure and target, resolved by kind. */
    fun measure(goal: SavingsGoal, ledger: BankLedgerStore): Pair<Double, Double> {
        return when (goal.kind) {
            GoalKind.Saving -> {
                // Unlinked, this is the original: money the user says they put
                // aside. Linked to accounts, it also follows their balance.
                if (goal.accountIDs.isEmpty()) return goal.currentAmount to goal.targetAmount
                val items = scopedItems(goal, ledger, goal.accountIDs.toSet())
                val inflow = items.filter { it.type == ItemType.Income }.sumOf { it.monthlyAmount }
                val outflow = items.filter { it.type == ItemType.Expense }.sumOf { it.monthlyAmount }
                (goal.currentAmount + inflow - outflow) to goal.targetAmount
            }
            GoalKind.Spending, GoalKind.Income -> {
                // Counted by hand, for someone who wants the goal without a bank.
                if (goal.manualOnly) return goal.currentAmount to goal.targetAmount
                val wanted = if (goal.kind == GoalKind.Spending) ItemType.Expense else ItemType.Income
                // Empty means every CONNECTED account.
                val picked = goal.accountIDs.ifEmpty { ledger.accounts.map { it.id } }.toSet()
                val total = scopedItems(goal, ledger, picked)
                    .filter { it.type == wanted }
                    .sumOf { it.monthlyAmount }
                total to goal.targetAmount
            }
        }
    }

    /** The ledger lines a goal's rule selects. */
    private fun scopedItems(goal: SavingsGoal, ledger: BankLedgerStore, accounts: Set<String>): List<BudgetItem> =
        ledger.items(goal.period ?: BudgetPeriod.Everything, accounts, goal.budgetType)
            .filter { goal.category == null || it.category == goal.category }

    /** 0…(can exceed 1). A spending goal past 1 is over its limit. */
    fun fraction(goal: SavingsGoal, ledger: BankLedgerStore): Double {
        val (current, target) = measure(goal, ledger)
        if (target <= 0) return 0.0
        return current / target
    }

    /**
     * One line describing what a derived goal is counting, for the row and
     * the editor. null for saving goals, which need no explanation.
     */
    fun ruleLabel(goal: SavingsGoal, ledger: BankLedgerStore): String? {
        if (goal.kind == GoalKind.Saving && goal.accountIDs.isEmpty()) return null
        val parts = mutableListOf<String>()
        goal.category?.let { parts.add(it) }
        when {
            goal.manualOnly -> parts.add("counted by hand")
            goal.accountIDs.isEmpty() -> parts.add("all accounts")
            goal.accountIDs.size == 1 ->
                parts.add(ledger.account(goal.accountIDs.first())?.displayName ?: "1 account")
            else -> parts.add("${goal.accountIDs.size} accounts")
        }
        goal.period?.let { parts.add(it.label) }
        return parts.joinToString(" · ")
    }

    /** The thresholds offered as one-tap alert chips. */
    val alertThresholds = listOf(50, 75, 90, 100)
}

// MARK: - Goal emoji

/**
 * Auto-suggests an emoji for a savings goal from its name (offline, curated
 * keyword map). Matching is whole-word (with a light plural fold) so
 * "carnival" doesn't become 🚗; multi-word keys match as phrases. Entries are
 * priority-ordered: specific objects first, generic money words last.
 */
object GoalEmoji {
    /** Shown when nothing matches — reads as "a goal", never as a bug. */
    const val FALLBACK = "🎯"

    private val map: List<Pair<List<String>, String>> = listOf(
        listOf("car", "truck", "vehicle", "auto", "tesla", "mustang", "jeep") to "🚗",
        listOf("motorcycle", "motorbike", "harley") to "🏍️",
        listOf("ev", "charger") to "⚡",
        listOf("house", "home", "condo", "apartment", "property", "mortgage", "downpayment", "down payment") to "🏠",
        listOf("renovation", "remodel", "reno", "repair", "improvement") to "🔨",
        listOf("furniture", "couch", "sofa", "mattress", "bed") to "🛋️",
        listOf("wedding", "engagement", "ring", "marriage", "bridal") to "💍",
        listOf("honeymoon") to "🏝️",
        listOf("baby", "newborn", "nursery", "maternity", "diaper") to "👶",
        listOf("vacation", "trip", "travel", "getaway", "flight", "disney", "cruise", "europe") to "✈️",
        listOf("beach", "resort") to "🏖️",
        listOf("phone", "iphone", "android", "smartphone", "pixel") to "📱",
        listOf("laptop", "computer", "macbook", "desktop", "pc") to "💻",
        listOf("camera", "lens", "gopro") to "📷",
        listOf("tv", "television") to "📺",
        listOf("console", "playstation", "xbox", "gaming", "nintendo", "gamer") to "🎮",
        listOf("bike", "bicycle", "cycle") to "🚲",
        listOf("boat", "yacht", "sailboat", "kayak") to "⛵",
        listOf("college", "school", "tuition", "education", "degree", "university", "student", "course") to "🎓",
        listOf("book", "library") to "📚",
        listOf("emergency", "rainy", "rainy day", "safety net", "cushion") to "🛟",
        listOf("retirement", "retire", "pension", "nest egg", "401k", "ira", "roth") to "🌴",
        listOf("business", "startup", "company", "venture", "llc", "shop") to "💼",
        listOf("gym", "fitness", "workout", "weights", "peloton", "muscle") to "🏋️",
        listOf("dog", "puppy") to "🐕",
        listOf("cat", "kitten") to "🐈",
        listOf("pet", "vet") to "🐾",
        listOf("medical", "surgery", "hospital", "dental", "dentist", "braces", "health") to "🏥",
        listOf("debt", "loan", "credit card", "payoff", "creditcard") to "💳",
        listOf("moving", "move", "relocation", "relocate") to "📦",
        listOf("christmas", "xmas", "holiday") to "🎄",
        listOf("gift", "present", "birthday", "party") to "🎁",
        listOf("guitar", "piano", "music", "instrument", "drum") to "🎸",
        listOf("watch", "rolex", "jewelry", "jewellery") to "⌚",
        listOf("shoe", "sneaker") to "👟",
        listOf("clothes", "clothing", "wardrobe", "outfit") to "👗",
        listOf("concert", "festival", "ticket", "tickets", "show") to "🎟️",
        listOf("camp", "tent", "hiking", "hike", "outdoor") to "🏕️",
        listOf("ski", "snowboard") to "⛷️",
        listOf("garden", "plant", "landscaping") to "🪴",
        listOf("solar", "panel") to "☀️",
        listOf("coffee", "espresso") to "☕",
        listOf("freedom", "dream", "fun", "splurge", "treat") to "✨",
        listOf("savings", "save", "fund", "money", "cash", "wealth", "invest", "stash") to "💰",
    )

    /** A curated palette for the manual picker (deduped, roughly by theme). */
    val palette: List<String> = listOf(
        "🎯", "💰", "🏦", "✨", "🎁", "🎉",
        "🚗", "🏍️", "🚲", "⛵", "✈️", "🏝️",
        "🏠", "🔨", "🛋️", "📦", "🌴", "🏖️",
        "💍", "👶", "🐕", "🐈", "🐾", "🏥",
        "📱", "💻", "📷", "📺", "🎮", "⌚",
        "🎓", "📚", "🛟", "💼", "🏋️", "💳",
        "🎸", "👟", "👗", "🎟️", "🏕️", "⛷️",
        "🪴", "☀️", "☕", "⚡", "🍼", "🩺",
    )

    /** The emoji to display for a goal: the user's override, else the suggestion. */
    fun resolve(goal: SavingsGoal): String {
        val e = goal.emoji
        if (!e.isNullOrEmpty()) return e
        return suggest(goal.name)
    }

    /** Best emoji for a name, or the fallback when nothing matches. */
    fun suggest(name: String): String {
        val lower = name.lowercase()
        // Word set holds each token AND its de-pluralized form, so "cars"
        // matches "car" while "carnival" never becomes "car".
        val words = mutableSetOf<String>()
        for (token in lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }) {
            words.add(token)
            if (token.length > 3 && token.endsWith("s")) words.add(token.dropLast(1))
        }
        for ((keywords, emoji) in map) {
            for (kw in keywords) {
                if (kw.contains(" ")) {
                    if (lower.contains(kw)) return emoji
                } else if (words.contains(kw)) {
                    return emoji
                }
            }
        }
        return FALLBACK
    }
}

// MARK: - Goal ring palette

/**
 * The goal progress ring's colour choices. A goal stores a hex string;
 * null means the default, which stays the theme's positive green so it keeps
 * adapting to light/dark.
 */
object GoalRing {
    /** Swatches offered in the goal form, as RRGGBB text. */
    val palette: List<String> = listOf(
        "3B5BDB", "0CA678", "1098AD", "7048E8", "9C36B5", "E64980",
        "E03131", "E8590C", "F08C00", "74B816", "0B7285", "495057",
    )

    /** The stored choice as a Color; null for the default and anything unparseable. */
    fun color(hex: String?): Color? {
        if (hex == null || hex.length != 6) return null
        val value = hex.toLongOrNull(16) ?: return null
        return Color(0xFF000000L or value)
    }
}

// MARK: - Category style

/**
 * A symbol and a tint per budget category, so lists of spending read as
 * something other than a column of identical chips. One style per category:
 * two entertainment subscriptions should look like siblings.
 */
object BudgetCategoryStyle {

    data class Style(val icon: ImageVector, val tint: Color)

    /** Anything unrecognised gets the neutral pair. */
    fun style(category: String): Style =
        table[category] ?: Style(Icons.Default.Autorenew, Color(0xFF868E96))

    fun icon(category: String): ImageVector = style(category).icon
    fun tint(category: String): Color = style(category).tint

    /**
     * Slice colours for the budget donuts, in order — long enough for the
     * biggest category list (business expenses, 15) so a full chart never
     * repeats a colour. Deliberately not the per-category tints above.
     */
    val chartPalette: List<Color> = listOf(
        Color(0xFF3B5BDB), Color(0xFF0CA678), Color(0xFFE8590C),
        Color(0xFFE64980), Color(0xFFF08C00), Color(0xFF7048E8),
        Color(0xFF1098AD), Color(0xFF74B816), Color(0xFFD6336C),
        Color(0xFF6741D9), Color(0xFFE03131), Color(0xFF2F9E44),
        Color(0xFF9C36B5), Color(0xFF0B7285), Color(0xFF495057),
    )

    fun chartColor(index: Int): Color = chartPalette[index % chartPalette.size]

    private val table: Map<String, Style> = mapOf(
        // Personal
        "Housing" to Style(Icons.Default.Home, Color(0xFF3B5BDB)),
        "Utilities" to Style(Icons.Default.Bolt, Color(0xFF0CA678)),
        "Food" to Style(Icons.Default.Restaurant, Color(0xFFE8590C)),
        "Transportation" to Style(Icons.Default.DirectionsCar, Color(0xFF5F3DC4)),
        "Entertainment" to Style(Icons.Default.LiveTv, Color(0xFFE64980)),
        "Healthcare" to Style(Icons.Default.MedicalServices, Color(0xFFE03131)),
        "Insurance" to Style(Icons.Default.Shield, Color(0xFF1098AD)),
        "Debt Payments" to Style(Icons.Default.CreditCard, Color(0xFFF08C00)),
        "Savings" to Style(Icons.Default.Money, Color(0xFF2F9E44)),
        "Retirement" to Style(Icons.Default.HourglassEmpty, Color(0xFF9C36B5)),

        // Business
        "Cost of Goods Sold (COGS)" to Style(Icons.Default.Inventory2, Color(0xFFE8590C)),
        "Salaries/Wages" to Style(Icons.Default.People, Color(0xFF3B5BDB)),
        "Marketing & Advertising" to Style(Icons.Default.Campaign, Color(0xFFE64980)),
        "Rent/Lease" to Style(Icons.Default.Business, Color(0xFF5F3DC4)),
        "Software & Subscriptions" to Style(Icons.Default.Laptop, Color(0xFF7048E8)),
        "Supplies" to Style(Icons.Default.Inventory2, Color(0xFFF08C00)),
        "Repairs & Maintenance" to Style(Icons.Default.Build, Color(0xFF495057)),
        "Professional Fees" to Style(Icons.Default.Work, Color(0xFF9C36B5)),
        "Taxes" to Style(Icons.Default.Description, Color(0xFFE03131)),
        "Travel" to Style(Icons.Default.Flight, Color(0xFF2F9E44)),
        "Depreciation" to Style(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFF868E96)),
        "Loan Payments" to Style(Icons.Default.CreditCard, Color(0xFFF08C00)),
    )
}
