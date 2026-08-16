//
// EducationContent.kt
//
// Port of the content model and relevance search from iOS
// Features/Education/EducationView.swift (itself a port of the web's
// lib/education-content.ts). Same topics, same catalog, same scoring — a
// query that ranks one way on the web ranks the same way here.
//

package com.finnacalc.android.features.education

/** A single education item — a video lesson or a reading resource. */
data class EduItem(val title: String, val url: String)

/** A flattened, searchable document. */
data class EduSearchDoc(
    val topic: String,
    val topicName: String,
    val type: Kind,
    val title: String,
    val url: String,
    val index: Int,
) {
    enum class Kind(val raw: String) { Video("video"), Article("article") }

    val id: String get() = "${type.raw}-$topic-$index"
}

object EducationContent {

    /** The ordered topic list used by both the grid and the hub. */
    val topics: List<Pair<String, String>> = listOf(
        "credit" to "Credit & Debt",
        "investing" to "Investing",
        "budgeting" to "Budgeting",
        "retirement" to "Retirement",
        "taxes" to "Taxes",
        "business" to "Business",
    )

    fun topicName(id: String): String = topics.firstOrNull { it.first == id }?.second ?: id

    /** YouTube lessons keyed by topic id. */
    val videoLessons: Map<String, List<EduItem>> = mapOf(
        "credit" to listOf(
            EduItem("What Is a Credit Score?", "https://www.youtube.com/watch?v=jwML94IOW0s"),
            EduItem("What Can Change Your Credit Score?", "https://www.youtube.com/watch?v=IZN5IT28iHo"),
            EduItem("Understanding Loans and Debt", "https://www.youtube.com/watch?v=E2dzSPOhUOI"),
            EduItem("Good Debt vs. Bad Debt", "https://www.youtube.com/watch?v=MFCdA2vGVh4"),
            EduItem("What Is APR and Why It Matters", "https://www.youtube.com/watch?v=MqqXTrEEZ7Y"),
            EduItem("Understanding Your FICO Score", "https://www.youtube.com/watch?v=8AtM1R9NmwM"),
        ),
        "investing" to listOf(
            EduItem("What Are Stocks?", "https://www.youtube.com/watch?v=98qfFzqDKR8"),
            EduItem("Bonds vs. Stocks: What's the Difference?", "https://www.youtube.com/watch?v=rs1md3e4aYU"),
            EduItem("Understanding Risk and Return", "https://www.youtube.com/watch?v=7mo167ohvJw"),
            EduItem("A Beginner's Guide to Investing", "https://www.youtube.com/watch?v=8_iWSsoiNXs"),
            EduItem("Index Funds vs. Mutual Funds vs. ETFs", "https://www.youtube.com/watch?v=ugBs333NhbI"),
        ),
        "retirement" to listOf(
            EduItem("What Is a 401(k)?", "https://www.youtube.com/watch?v=d8rNitoPZeo"),
            EduItem("An Introduction to Traditional IRAs", "https://www.youtube.com/watch?v=UV8kgqk_DAY"),
            EduItem("The Power of a Roth IRA", "https://www.youtube.com/watch?v=Xd8VXDqXtkE"),
            EduItem(
                "Managing Your 401(k) When You Change Jobs",
                "https://www.youtube.com/watch?v=PLZHTIrazF8",
            ),
        ),
        "budgeting" to listOf(
            EduItem("How to Budget Your Paycheck", "https://www.youtube.com/watch?v=5tQuez0kbOY"),
            EduItem(
                "How to Stop Living Paycheck to Paycheck",
                "https://www.youtube.com/watch?v=NSpMFtcXxcc",
            ),
            EduItem(
                "How to Manage Your Money (The 50/30/20 Rule)",
                "https://www.youtube.com/watch?v=HQzoZfc3GwQ",
            ),
            EduItem(
                "How to Manage Your Money (The 70/20/10 Rule)",
                "https://www.youtube.com/watch?v=HkNPZVu-jZM",
            ),
            EduItem("A Beginner's Guide to Paying Off Debt", "https://www.youtube.com/watch?v=_LdpjN2oDNo"),
        ),
        "taxes" to listOf(
            EduItem("What Are Taxes?", "https://www.youtube.com/watch?v=kdfk22Ck4nM"),
            EduItem("How Tax Brackets Work", "https://www.youtube.com/watch?v=AhgR3X--bbY"),
            EduItem("An Introduction to Tax Deductions", "https://www.youtube.com/watch?v=GypHy3gnG5E"),
            EduItem("Understanding Tax Credits", "https://www.youtube.com/watch?v=4gYvlMwvdnw"),
            EduItem("A Guide to Common Tax Forms (Part 1)", "https://www.youtube.com/watch?v=boklbFhF8l8"),
            EduItem("A Guide to Common Tax Forms (Part 2)", "https://www.youtube.com/watch?v=W1562KoBExA"),
        ),
    )

    /** Curated articles keyed by topic id. */
    val readingResources: Map<String, List<EduItem>> = mapOf(
        "credit" to listOf(
            EduItem(
                "An Introduction to Credit and Loans",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:loans-and-debt/xa6995ea67a8e9fdd:borrowing-money/a/loans-and-credit",
            ),
            EduItem(
                "How to Raise Your Credit Score",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:consumer-credit/xa6995ea67a8e9fdd:credit-score/a/" +
                    "how-do-i-raise-my-credit-score",
            ),
        ),
        "investing" to listOf(
            EduItem(
                "How to Invest with Confidence",
                "https://www.investopedia.com/articles/basics/11/3-s-simple-investing.asp",
            ),
            EduItem(
                "How and Where to Start Investing",
                "https://www.investopedia.com/terms/i/investment.asp",
            ),
        ),
        "retirement" to listOf(
            EduItem(
                "How to Invest for Retirement",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:investments-retirement/xa6995ea67a8e9fdd:investing/a/" +
                    "how-to-invest-in-your-retirement-account",
            ),
            EduItem(
                "Building a Strong Foundation for Retirement",
                "https://www.khanacademy.org/college-careers-more/personal-finance/" +
                    "pf-investment-vehicles-insurance-and-retirement/pf-ira-401ks/a/" +
                    "building-a-foundation-for-retirement",
            ),
            EduItem(
                "The Effect of Time on Your Retirement Savings",
                "https://www.khanacademy.org/college-careers-more/personal-finance/" +
                    "pf-investment-vehicles-insurance-and-retirement/pf-ira-401ks/a/" +
                    "the-effect-of-time-on-your-retirement-account",
            ),
            EduItem(
                "Pensions, 403(b)s, and SIMPLE IRAs Explained",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:investments-retirement/xa6995ea67a8e9fdd:saving-for-retirement/a/" +
                    "what-is-a-pension-403-b-simple-ira-and-others",
            ),
        ),
        "budgeting" to listOf(
            EduItem(
                "What Is a Budget?",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:budgeting-and-saving/xa6995ea67a8e9fdd:budgeting/a/what-is-a-budget",
            ),
            EduItem(
                "A Step-by-Step Guide to Creating a Budget",
                "https://www.khanacademy.org/college-careers-more/personal-finance/" +
                    "pf-saving-and-budgeting/tips-for-tracking-and-saving-money/a/creating-a-budget",
            ),
            EduItem(
                "How to Balance Your Budget",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:budgeting-and-saving/xa6995ea67a8e9fdd:budgeting/a/" +
                    "balancing-your-budget",
            ),
            EduItem(
                "Understanding Budgeting Constraints and Decisions",
                "https://www.khanacademy.org/economics-finance-domain/microeconomics/" +
                    "choices-opp-cost-tutorial/utility-maximization-with-indifference-curves/a/" +
                    "how-individuals-make-choices-based-on-their-budget-constraint-cnx",
            ),
        ),
        "taxes" to listOf(
            EduItem(
                "An Overview of Common Tax Forms",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:taxes-and-tax-forms/xa6995ea67a8e9fdd:tax-forms/a/tax-forms",
            ),
            EduItem(
                "Your Guide to Key Tax Terms",
                "https://www.khanacademy.org/math/grade-7-math-tx/" +
                    "xa876d090ec748f45:number-and-operations/xa876d090ec748f45:income-tax-withholding/a/" +
                    "your-guide-to-key-tax-terms-brought-to-you-by-better-money-habits",
            ),
            EduItem(
                "Understanding the Taxes You Pay",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:taxes-and-tax-forms/xa6995ea67a8e9fdd:what-are-taxes/a/" +
                    "understanding-the-taxes-you-pay",
            ),
            EduItem(
                "A Guide to Taxes for the Self-Employed",
                "https://www.khanacademy.org/college-careers-more/financial-literacy/" +
                    "xa6995ea67a8e9fdd:employment/xa6995ea67a8e9fdd:non-typical-pay-structures/a/" +
                    "tax-responsibilities-for-self-employed-individuals",
            ),
        ),
    )

    /** Every video + article flattened for search: videos first, then articles. */
    val searchIndex: List<EduSearchDoc> = buildList {
        topics.forEach { (topic, _) ->
            videoLessons[topic].orEmpty().forEachIndexed { index, item ->
                add(EduSearchDoc(topic, topicName(topic), EduSearchDoc.Kind.Video, item.title, item.url, index))
            }
        }
        topics.forEach { (topic, _) ->
            readingResources[topic].orEmpty().forEachIndexed { index, item ->
                add(EduSearchDoc(topic, topicName(topic), EduSearchDoc.Kind.Article, item.title, item.url, index))
            }
        }
    }

    /** Total counts across every topic — feeds the search bar's live placeholder. */
    val totalVideoCount: Int = videoLessons.values.sumOf { it.size }
    val totalArticleCount: Int = readingResources.values.sumOf { it.size }

    // MARK: Relevance search

    private val stopWords = setOf(
        "how", "to", "what", "is", "are", "a", "an", "the", "and", "or", "of", "in", "for",
        "my", "do", "i", "on", "with", "you", "your", "vs", "me", "can", "should", "about",
        "best", "way", "ways", "tips", "guide", "explain", "explained",
    )

    private fun tokenize(s: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in s.lowercase()) {
            if (ch.code < 128 && (ch.isLetter() || ch.isDigit())) {
                current.append(ch)
            } else if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens.filter { it.length > 1 && it !in stopWords }
    }

    /** Mirrors the JS `stem` regex `(ings|ing|ies|ied|ed|es|s)$`. */
    private fun stem(t: String): String {
        for (suffix in listOf("ings", "ing", "ies", "ied", "ed", "es", "s")) {
            if (t.length > suffix.length && t.endsWith(suffix)) {
                return t.dropLast(suffix.length)
            }
        }
        return t
    }

    /**
     * Ranks education content against a free-text query (forgiving stem /
     * prefix / substring match). Returns an empty list when nothing is
     * reasonably related — a bad query gets no results, not bad results.
     */
    fun search(query: String): List<EduSearchDoc> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()

        val qTokens = tokenize(q).map { stem(it) }
        if (qTokens.isEmpty()) {
            // All-stopword query → plain substring match.
            return searchIndex.filter { "${it.title} ${it.topicName}".lowercase().contains(q) }
        }

        return searchIndex
            .map { doc ->
                val titleText = doc.title.lowercase()
                val fullText = "${doc.title} ${doc.topicName}".lowercase()
                val docTokens = tokenize(fullText).map { stem(it) }
                var score = 0.0

                if (titleText.contains(q)) score += 12
                else if (fullText.contains(q)) score += 8

                var matched = 0
                for (qt in qTokens) {
                    when {
                        docTokens.contains(qt) -> { score += 5; matched += 1 }
                        docTokens.any { it.startsWith(qt) || qt.startsWith(it) } -> { score += 3; matched += 1 }
                        fullText.contains(qt) -> { score += 1.5; matched += 1 }
                    }
                }
                if (matched == qTokens.size && qTokens.size > 1) score += 3

                doc to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(24)
            .map { it.first }
    }

    /** The YouTube id inside a watch URL, for thumbnails and playback. */
    fun youTubeId(url: String): String? =
        url.substringAfter("watch?v=", "").substringBefore('&').takeIf { it.isNotEmpty() }

    fun thumbnailUrl(url: String): String? =
        youTubeId(url)?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
}
