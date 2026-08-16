//
// TradeTracker.kt
//
// Port of the catalog and stores from iOS Features/Investing/TradeTrackerView.swift —
// a directory of people whose trades users want to watch, split into
// Investors, Insiders, and Politicians, with a Following group first.
//
// IDENTITY ONLY, deliberately. The catalog says who each person is and never
// carries a figure. Trade feeds, performance, and holdings come from the
// backend later, and per the app rule nothing here shows a number until it is
// a real one. Politician trade DATA also has a legal question to settle before
// it ships (commercial use of congressional disclosures); listing who they are
// is fine, showing their trades needs that answer first.
//
// Deviation from iOS: iOS bundles pre-generated transparent portrait cutouts
// in its asset catalog. Those assets don't exist in this repo, so a person's
// freely licensed Wikimedia photo is loaded into a circular avatar instead,
// falling back to the tinted monogram when there is no free portrait. Both
// keep the org logo badge on the corner, and About already credits Wikimedia.
//

package com.finnacalc.android.features.investing

import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackerCategory(val raw: String, val title: String) {
    Investors("investors", "Investors"),
    Insiders("insiders", "Insiders"),
    Politicians("politicians", "Politicians"),
}

data class TrackedPerson(
    val id: String,
    val name: String,
    val org: String,
    /** One factual line on who they are or why their trades get watched. */
    val blurb: String,
    val category: TrackerCategory,
    /**
     * The org's ticker, and its website, when it has them ("" = none). Either
     * one resolves the corner logo badge; the ticker wins when both are set.
     */
    val logoSymbol: String,
    val logoDomain: String = "",
    /** A plain emoji badge, drawn by us (Trump gets the flag). */
    val emojiBadge: String = "",
    /** The freely licensed Wikimedia photo this person's avatar comes from. */
    val imageUrl: String = "",
    /**
     * SEC filer number, for people whose real filings we can read. Insiders
     * file Form 4 under their own CIK; investors file 13F under their firm's.
     * Empty means we have no verified filer, and the detail page says so
     * rather than showing an empty feed as if they never trade.
     */
    val cik: String = "",
) {
    /** Initials for the monogram fallback. */
    val monogram: String
        get() = name.split(" ").filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

/** Hand-picked, small on purpose: the point is people users recognise. */
object TrackerCatalog {
    val all: List<TrackedPerson> = listOf(
    TrackedPerson(
        id = "buffett",
        name = "Warren Buffett",
        org = "Berkshire Hathaway",
        blurb = "Chairman of Berkshire Hathaway. His quarterly filings are the most-watched portfolio in investing.",
        category = TrackerCategory.Investors,
        logoSymbol = "BRK-B",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Warren_Buffett_at_the_2015_SelectUSA_Investment_Summit_%28cropped%29.jpg/330px-Warren_Buffett_at_the_2015_SelectUSA_Investment_Summit_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "ackman",
        name = "Bill Ackman",
        org = "Pershing Square",
        blurb = "Runs Pershing Square, a concentrated fund known for big public positions.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "pershingsquareholdings.com",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/07/Valeant_Pharmaceuticals%27_Business_Model_%28headshot%29.jpg",
    ),
    TrackedPerson(
        id = "wood",
        name = "Cathie Wood",
        org = "ARK Invest",
        blurb = "Founder of ARK Invest, funds focused on high-growth technology bets.",
        category = TrackerCategory.Investors,
        logoSymbol = "ARKK",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/44/Cathie_Wood_ARK_Invest_Photo.jpg/330px-Cathie_Wood_ARK_Invest_Photo.jpg",
    ),
    TrackedPerson(
        id = "burry",
        name = "Michael Burry",
        org = "Scion Asset Management",
        blurb = "The Big Short investor. Runs a small, contrarian book at Scion.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "scionasset.com",
    ),
    TrackedPerson(
        id = "dalio",
        name = "Ray Dalio",
        org = "Bridgewater Associates",
        blurb = "Founded Bridgewater, one of the largest hedge funds in the world.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "bridgewater.com",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Web_Summit_2018_-_Forum_-_Day_2%2C_November_7_HM1_7481_%2844858045925%29.jpg/330px-Web_Summit_2018_-_Forum_-_Day_2%2C_November_7_HM1_7481_%2844858045925%29.jpg",
    ),
    TrackedPerson(
        id = "griffin",
        name = "Ken Griffin",
        org = "Citadel",
        blurb = "Founder and CEO of Citadel, among the most profitable funds ever.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "citadel.com",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5d/Kenneth_C._Griffin.jpg/330px-Kenneth_C._Griffin.jpg",
    ),
    TrackedPerson(
        id = "tepper",
        name = "David Tepper",
        org = "Appaloosa Management",
        blurb = "Distressed-debt specialist behind Appaloosa. Owns the Carolina Panthers.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/David_Tepper_01.jpg/330px-David_Tepper_01.jpg",
    ),
    TrackedPerson(
        id = "druckenmiller",
        name = "Stanley Druckenmiller",
        org = "Duquesne Family Office",
        blurb = "Ran money with George Soros, now invests his own through Duquesne.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
    ),
    TrackedPerson(
        id = "soros",
        name = "George Soros",
        org = "Soros Fund Management",
        blurb = "Famous for breaking the Bank of England. His family office still files quarterly.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/97/George_Soros%2C_Founder_and_Chairman_of_the_Open_Society_Foundations%2C_visits_the_EC_%283x4_cropped%29.jpg/330px-George_Soros%2C_Founder_and_Chairman_of_the_Open_Society_Foundations%2C_visits_the_EC_%283x4_cropped%29.jpg",
    ),
    TrackedPerson(
        id = "icahn",
        name = "Carl Icahn",
        org = "Icahn Enterprises",
        blurb = "Veteran activist investor who takes stakes and pushes for change.",
        category = TrackerCategory.Investors,
        logoSymbol = "IEP",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Carl_Icahn%2C_1980s.jpg/330px-Carl_Icahn%2C_1980s.jpg",
    ),
    TrackedPerson(
        id = "klarman",
        name = "Seth Klarman",
        org = "Baupost Group",
        blurb = "Value investor and author of Margin of Safety, runs Baupost.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "baupost.com",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Seth_Klarman_at_147th_Preakness_Stakes.jpg/330px-Seth_Klarman_at_147th_Preakness_Stakes.jpg",
    ),
    TrackedPerson(
        id = "gates",
        name = "Bill Gates",
        org = "Gates Foundation Trust",
        blurb = "Microsoft co-founder. The foundation trust's portfolio files publicly.",
        category = TrackerCategory.Investors,
        logoSymbol = "MSFT",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Bill_Gates_at_the_European_Commission_-_P067383-987995_%28cropped%29_5.jpg/330px-Bill_Gates_at_the_European_Commission_-_P067383-987995_%28cropped%29_5.jpg",
    ),
    TrackedPerson(
        id = "marks",
        name = "Howard Marks",
        org = "Oaktree Capital",
        blurb = "Co-founded Oaktree and writes the memos Wall Street actually reads.",
        category = TrackerCategory.Investors,
        logoSymbol = "",
        logoDomain = "oaktreecapital.com",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Howard_Marks_2.17.12_%28cropped%29.jpg/330px-Howard_Marks_2.17.12_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "cook",
        name = "Tim Cook",
        org = "Apple",
        blurb = "CEO of Apple. Insider filings show when leadership buys or sells.",
        category = TrackerCategory.Insiders,
        logoSymbol = "AAPL",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/Tim_Cook_March_2026_%28cropped_2%29.jpg/330px-Tim_Cook_March_2026_%28cropped_2%29.jpg",
        cik = "0001214156",
    ),
    TrackedPerson(
        id = "musk",
        name = "Elon Musk",
        org = "Tesla",
        blurb = "CEO of Tesla. His stock sales and awards are among the most-watched filings.",
        category = TrackerCategory.Insiders,
        logoSymbol = "TSLA",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Elon_Musk_-_54820081119_%28cropped%29.jpg/330px-Elon_Musk_-_54820081119_%28cropped%29.jpg",
        cik = "0001494730",
    ),
    TrackedPerson(
        id = "huang",
        name = "Jensen Huang",
        org = "NVIDIA",
        blurb = "Co-founder and CEO of NVIDIA, the center of the AI chip boom.",
        category = TrackerCategory.Insiders,
        logoSymbol = "NVDA",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/Jen-Hsun_Huang_2025.jpg/330px-Jen-Hsun_Huang_2025.jpg",
        cik = "0001197649",
    ),
    TrackedPerson(
        id = "nadella",
        name = "Satya Nadella",
        org = "Microsoft",
        blurb = "CEO of Microsoft since 2014.",
        category = TrackerCategory.Insiders,
        logoSymbol = "MSFT",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/MS-Exec-Nadella-Satya-2017-08-31-22_%28cropped%29.jpg/330px-MS-Exec-Nadella-Satya-2017-08-31-22_%28cropped%29.jpg",
        cik = "0001513142",
    ),
    TrackedPerson(
        id = "zuckerberg",
        name = "Mark Zuckerberg",
        org = "Meta",
        blurb = "Founder and CEO of Meta. Sells on a preset schedule worth tracking.",
        category = TrackerCategory.Insiders,
        logoSymbol = "META",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/F20250904AH-2824_%2854778373111%29_%283x4_cropped_on_Zuckerberg_following_the_rule_of_thirds%29.jpg/330px-F20250904AH-2824_%2854778373111%29_%283x4_cropped_on_Zuckerberg_following_the_rule_of_thirds%29.jpg",
        cik = "0001548760",
    ),
    TrackedPerson(
        id = "dimon",
        name = "Jamie Dimon",
        org = "JPMorgan Chase",
        blurb = "Longtime CEO of JPMorgan, the largest US bank. His rare sales make news.",
        category = TrackerCategory.Insiders,
        logoSymbol = "JPM",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Chancellor_Rachel_Reeves_meets_Jamie_Dimon_%2854838700663%29_%28cropped%29_%28cropped%29.jpg/330px-Chancellor_Rachel_Reeves_meets_Jamie_Dimon_%2854838700663%29_%28cropped%29_%28cropped%29.jpg",
        cik = "0001195345",
    ),
    TrackedPerson(
        id = "pichai",
        name = "Sundar Pichai",
        org = "Alphabet",
        blurb = "CEO of Alphabet and Google.",
        category = TrackerCategory.Insiders,
        logoSymbol = "GOOGL",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Sundar_Pichai_-_2023_%28cropped%29.jpg/330px-Sundar_Pichai_-_2023_%28cropped%29.jpg",
        cik = "0001534753",
    ),
    TrackedPerson(
        id = "jassy",
        name = "Andy Jassy",
        org = "Amazon",
        blurb = "CEO of Amazon, formerly built AWS.",
        category = TrackerCategory.Insiders,
        logoSymbol = "AMZN",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Andy_Jassy.jpg/330px-Andy_Jassy.jpg",
        cik = "0001374545",
    ),
    TrackedPerson(
        id = "bezos",
        name = "Jeff Bezos",
        org = "Amazon",
        blurb = "Founder and executive chair of Amazon. His planned sales move billions.",
        category = TrackerCategory.Insiders,
        logoSymbol = "AMZN",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/260202-D-PM193-2205_SECWAR_Arsenal_of_Freedom_Tour_-_Florida_%283x4_cropped_on_Bezos_and_rotated%29.jpg/330px-260202-D-PM193-2205_SECWAR_Arsenal_of_Freedom_Tour_-_Florida_%283x4_cropped_on_Bezos_and_rotated%29.jpg",
        cik = "0001043298",
    ),
    TrackedPerson(
        id = "su",
        name = "Lisa Su",
        org = "AMD",
        blurb = "CEO credited with AMD's turnaround into an AI chip contender.",
        category = TrackerCategory.Insiders,
        logoSymbol = "AMD",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/SXSW-2024-alih-OB7A0861-Lisa_Su_%28cropped_2%29.jpg/330px-SXSW-2024-alih-OB7A0861-Lisa_Su_%28cropped_2%29.jpg",
        cik = "0001405109",
    ),
    TrackedPerson(
        id = "pelosi",
        name = "Nancy Pelosi",
        org = "US House, California",
        blurb = "Former Speaker of the House. Her household's disclosures are the most-followed in Congress.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a5/Official_photo_of_Speaker_Nancy_Pelosi_in_2019.jpg/330px-Official_photo_of_Speaker_Nancy_Pelosi_in_2019.jpg",
    ),
    TrackedPerson(
        id = "tuberville",
        name = "Tommy Tuberville",
        org = "US Senate, Alabama",
        blurb = "Senator and former football coach with one of the most active trade records.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "senate.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Sen._Tommy_Tuberville_Official_Portrait%2C_118th_Congress_%28cropped%29.jpg/330px-Sen._Tommy_Tuberville_Official_Portrait%2C_118th_Congress_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "crenshaw",
        name = "Dan Crenshaw",
        org = "US House, Texas",
        blurb = "Texas representative whose disclosures draw regular attention.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Rep._Dan_Crenshaw%2C_official_portrait%2C_118th_Congress.jpg/330px-Rep._Dan_Crenshaw%2C_official_portrait%2C_118th_Congress.jpg",
    ),
    TrackedPerson(
        id = "khanna",
        name = "Ro Khanna",
        org = "US House, California",
        blurb = "Represents Silicon Valley. Family disclosures are frequent and detailed.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/16/Ro_Khanna%2C_official_portrait%2C_115th_Congress_%283x4%29.jpg/330px-Ro_Khanna%2C_official_portrait%2C_115th_Congress_%283x4%29.jpg",
    ),
    TrackedPerson(
        id = "greene",
        name = "Marjorie Taylor Greene",
        org = "US House, Georgia",
        blurb = "Georgia representative known for frequent stock purchases.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Marjorie_Taylor_Greene_%28cropped%29.jpg/330px-Marjorie_Taylor_Greene_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "gottheimer",
        name = "Josh Gottheimer",
        org = "US House, New Jersey",
        blurb = "New Jersey representative and one of the chamber's most active traders.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/58/Josh_Gottheimer%2C_official_portrait%2C_115th_Congress_%28cropped%29.jpg/330px-Josh_Gottheimer%2C_official_portrait%2C_115th_Congress_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "mccaul",
        name = "Michael McCaul",
        org = "US House, Texas",
        blurb = "Texas representative whose family files among the largest trade volumes.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "house.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Rep._Michael_McCaul%2C_official_portrait%2C_118th_Congress.jpg/330px-Rep._Michael_McCaul%2C_official_portrait%2C_118th_Congress.jpg",
    ),
    TrackedPerson(
        id = "scott",
        name = "Rick Scott",
        org = "US Senate, Florida",
        blurb = "Florida senator and former hospital executive with sizable holdings.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "senate.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Official_Portrait_of_Senator_Rick_Scott_%28R-FL%29.jpg/330px-Official_Portrait_of_Senator_Rick_Scott_%28R-FL%29.jpg",
    ),
    TrackedPerson(
        id = "trump",
        name = "Donald Trump",
        org = "White House",
        blurb = "President of the United States. His annual disclosures run to hundreds of holdings, including Trump Media.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        emojiBadge = "🇺🇸",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/16/Official_Presidential_Portrait_of_President_Donald_J._Trump_%282025%29.jpg/330px-Official_Presidential_Portrait_of_President_Donald_J._Trump_%282025%29.jpg",
    ),
    TrackedPerson(
        id = "bessent",
        name = "Scott Bessent",
        org = "US Treasury",
        blurb = "Treasury Secretary and former hedge fund manager who ran Key Square Group.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "home.treasury.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d6/Official_portrait_of_Treasury_Secretary_Scott_Bessent_%28borderless%29_%28cropped%29.jpg/330px-Official_portrait_of_Treasury_Secretary_Scott_Bessent_%28borderless%29_%28cropped%29.jpg",
    ),
    TrackedPerson(
        id = "lutnick",
        name = "Howard Lutnick",
        org = "US Commerce",
        blurb = "Commerce Secretary and former CEO of Cantor Fitzgerald.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "commerce.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/Howard_Lutnick_2025.jpg/330px-Howard_Lutnick_2025.jpg",
    ),
    TrackedPerson(
        id = "patel",
        name = "Kash Patel",
        org = "FBI",
        blurb = "FBI Director whose disclosed holdings regularly draw scrutiny.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "fbi.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/Kash_Patel%2C_official_FBI_portrait_%28cropped_2%29.jpg/330px-Kash_Patel%2C_official_FBI_portrait_%28cropped_2%29.jpg",
    ),
    TrackedPerson(
        id = "miller",
        name = "Stephen Miller",
        org = "White House",
        blurb = "Deputy chief of staff whose financial disclosures have drawn attention.",
        category = TrackerCategory.Politicians,
        logoSymbol = "",
        logoDomain = "whitehouse.gov",
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7d/Stephen_Miller_July_2025.jpg/330px-Stephen_Miller_July_2025.jpg",
    ),    )

    fun person(id: String): TrackedPerson? = all.firstOrNull { it.id == id }

    fun inCategory(category: TrackerCategory): List<TrackedPerson> =
        all.filter { it.category == category }
}

// MARK: - Follow store

/**
 * Followed person ids. Its own key, not the watchlist: symbols and people are
 * different lists with different screens.
 */
object TrackerFollowStore {
    private const val STORAGE_KEY = "finnacalc.tracker.following"

    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    private fun load(): List<String> = JsonPrefs.load<List<String>>(STORAGE_KEY) ?: emptyList()

    fun contains(id: String): Boolean = _ids.value.contains(id)

    fun toggle(id: String): Boolean {
        val now = !contains(id)
        _ids.value = if (now) _ids.value + id else _ids.value - id
        JsonPrefs.persist(_ids.value, STORAGE_KEY)
        return now
    }

    fun resetForTesting() {
        _ids.value = emptyList()
    }
}

/**
 * Who gets a trade alert. Same shape as the follow store, its own key, plus
 * the master switch's undo snapshot: captured once per run of master flicks
 * and cleared the moment any bell is touched individually, exactly like the
 * subscriptions reminders switch.
 */
object TrackerAlertStore {
    private const val STORAGE_KEY = "finnacalc.tracker.alerts"

    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    /** Alert states as they were before the master switch changed them. */
    var undo: Map<String, Boolean>? = null

    private fun load(): List<String> = JsonPrefs.load<List<String>>(STORAGE_KEY) ?: emptyList()

    fun contains(id: String): Boolean = _ids.value.contains(id)

    fun set(id: String, on: Boolean) {
        val next = if (on) (_ids.value + id).distinct() else _ids.value - id
        _ids.value = next
        JsonPrefs.persist(next, STORAGE_KEY)
    }

    fun toggle(id: String): Boolean {
        val now = !contains(id)
        set(id, now)
        // A hand-set bell is the new baseline.
        undo = null
        return now
    }

    fun resetForTesting() {
        _ids.value = emptyList()
        undo = null
    }
}
