//
// TransactionCategorizer.kt
//
// Port of iOS Features/Budgeting/TransactionCategorizer.swift — turns a
// bank-statement description ("SQ *BLUE BOTTLE COFFEE", "ACH PAYROLL DEP")
// into one of the BudgetCategories options, so imported rows land in real
// categories instead of a single "Other" bucket.
//
// Matching is keyword-on-lowercased-description, first table hit wins, and
// the tables are ordered most-specific-first: "student loan" has to beat
// "loan", and a grocery store has to beat a generic "market". Anything
// unmatched falls back to the type's catch-all rather than guessing.
//

package com.finnacalc.android.features.budgeting

object TransactionCategorizer {

    /** Best category for a statement line; unmatched gets the catch-all. */
    fun category(description: String, type: ItemType, budgetType: BudgetType): String {
        val text = description.lowercase()
        val table = when {
            budgetType == BudgetType.Personal && type == ItemType.Expense -> personalExpense
            budgetType == BudgetType.Personal && type == ItemType.Income -> personalIncome
            budgetType == BudgetType.Business && type == ItemType.Expense -> businessExpense
            else -> businessIncome
        }
        for ((cat, keywords) in table) {
            if (keywords.any { text.contains(it) }) return cat
        }
        return fallback(type, budgetType)
    }

    /** The catch-all each list ends with — matches BudgetCategories. */
    fun fallback(type: ItemType, budgetType: BudgetType): String = when {
        budgetType == BudgetType.Personal -> "Other"
        type == ItemType.Income -> "Other Revenue"
        else -> "Other Operating Costs"
    }

    /**
     * Whether a category is a real option for this budget — imported
     * snapshots can carry categories from the other budget type, which would
     * otherwise show up as a phantom group in the list and the donut.
     */
    fun isValid(category: String, type: ItemType, budgetType: BudgetType): Boolean {
        val options = if (type == ItemType.Income) {
            BudgetCategories.income(budgetType)
        } else {
            BudgetCategories.expense(budgetType)
        }
        return options.contains(category)
    }

    // MARK: - Personal

    private val personalExpense: List<Pair<String, List<String>>> = listOf(
        // Before Debt Payments so "student loan" doesn't read as generic debt,
        // and before Housing so "mortgage" wins over a bank's name.
        "Savings" to listOf(
            "savings", "emergency fund", "ally bank", "marcus", "sofi save",
            "transfer to sav", "acorns", "betterment",
        ),
        "Retirement" to listOf(
            "401k", "401 k", "403b", "roth", " ira", "ira ", "retirement",
            "pension", "vanguard", "fidelity", "empower",
        ),
        "Debt Payments" to listOf(
            "student loan", "navient", "sallie mae", "nelnet", "loan payment",
            "car loan", "auto loan", "credit card payment", "card payment",
            "cc payment", "interest charge", "collections", "afterpay",
            "klarna", "affirm",
        ),
        "Housing" to listOf(
            "rent", "mortgage", "landlord", "hoa ", " hoa", "property mgmt",
            "property management", "apartment", "lease payment", "escrow",
            "zillow", "greystar",
        ),
        "Utilities" to listOf(
            "electric", "power co", "pg&e", "con ed", "coned", "duke energy",
            "national grid", "water bill", "water dept", "sewer", "trash",
            "waste management", "internet", "comcast", "xfinity", "spectrum",
            "verizon", "at&t", "att ", "t-mobile", "tmobile", "sprint",
            "utility", "utilities", "natural gas", "gas company",
        ),
        "Food" to listOf(
            "grocer", "supermarket", "safeway", "kroger", "trader joe", "whole foods",
            "aldi", "publix", "wegmans", "sprouts", "food lion", "h-e-b", "heb ",
            "restaurant", "cafe", "caffe", "coffee", "starbucks", "dunkin", "peet",
            "mcdonald", "chipotle", "subway", "panera", "wendy", "burger", "pizza",
            "taco", "sushi", "deli", "bakery", "doordash", "uber eats", "ubereats",
            "grubhub", "postmates", "instacart", "seamless", "diner", "bistro",
            "steakhouse", "food",
        ),
        "Transportation" to listOf(
            "uber", "lyft", "shell", "chevron", "exxon", "mobil", "texaco",
            "bp ", "bp#", "citgo", "sunoco", "arco", "wawa", "fuel", "gas station",
            "parking", "toll", "e-zpass", "ezpass", "transit", "metro card",
            // Transit agencies arrive as "MTA*NYCT PAYGO" — no space to anchor
            // on, so match the codes directly.
            "metrocard", "mta*", "mta ", "nyct", "paygo", "septa", "wmata",
            "bart ", "caltrain", "amtrak", "greyhound",
            "airline", "delta air", "united air", "southwest air", "jetblue",
            "american air", "car wash", "jiffy lube", "oil change", "autozone",
            "tire", "dmv", "zipcar", "hertz", "avis", "enterprise rent",
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hulu", "disney", "hbo", "max.com", "peacock",
            "paramount", "apple music", "apple tv", "youtube", "prime video",
            "audible", "kindle", "steam", "xbox", "playstation", "nintendo",
            "twitch", "patreon", "cinema", "movie", "amc ", "regal ", "theater",
            "theatre", "concert", "ticketmaster", "stubhub", "eventbrite",
            "bar ", "pub ", "brewery", "tavern", "liquor", "casino", "golf",
        ),
        "Healthcare" to listOf(
            "pharmacy", "cvs", "walgreens", "rite aid", "doctor", "physician",
            "dentist", "dental", "orthodont", "medical", "clinic", "hospital",
            "urgent care", "optometr", "vision center", "lenscrafters", "therapy",
            "therapist", "psychiatr", "labcorp", "quest diagnostics", "healthcare",
            "health center", "gym", "fitness", "planet fit", "equinox", "peloton",
        ),
        "Insurance" to listOf(
            "insurance", "geico", "progressive", "allstate", "state farm", "usaa",
            "aetna", "cigna", "blue cross", "blue shield", "unitedhealth", "humana",
            "kaiser", "lemonade", "policy premium", "premium payment",
        ),
    )

    private val personalIncome: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf(
            "payroll", "salary", "paycheck", "direct dep", "dir dep", "wages",
            "adp ", "gusto", "paychex", "workday", "employer", "bi-weekly pay",
        ),
        "Freelance" to listOf(
            "freelance", "invoice", "upwork", "fiverr", "contract", "consulting",
            "1099", "contractor", "stripe payout", "paypal payout", "gig",
        ),
        "Investments" to listOf(
            "dividend", "capital gain", "interest earned", "interest paid",
            "brokerage", "coinbase", "robinhood", "e*trade", "etrade", "schwab",
            "vanguard", "fidelity", "webull", "treasury",
        ),
        "Gift" to listOf("gift", "birthday", "venmo from", "zelle from", "cash app from"),
    )

    // MARK: - Business

    private val businessExpense: List<Pair<String, List<String>>> = listOf(
        "Salaries/Wages" to listOf(
            "payroll", "salary", "wages", "adp ", "gusto", "paychex",
            "rippling", "justworks", "contractor pay",
        ),
        "Marketing & Advertising" to listOf(
            "google ads", "facebook ads", "meta ads", "advertis",
            "marketing", "seo ", "mailchimp", "klaviyo", "hubspot",
            "linkedin ads", "tiktok ads", "sponsorship", "campaign",
        ),
        "Software & Subscriptions" to listOf(
            "software", "saas", "aws", "amazon web", "azure",
            "google cloud", "gcp ", "digitalocean", "heroku",
            "slack", "notion", "figma", "adobe", "github", "gitlab",
            "atlassian", "zoom", "dropbox", "salesforce", "stripe fee",
            "subscription", "license", "hosting", "domain",
        ),
        "Professional Fees" to listOf(
            "legal", "attorney", "lawyer", "law firm", "accountant", "cpa ",
            "bookkeep", "audit", "notary", "consulting fee", "advisory",
        ),
        "Rent/Lease" to listOf("rent", "lease", "office space", "coworking", "wework", "sublease"),
        "Utilities" to listOf(
            "electric", "water bill", "internet", "comcast", "xfinity", "verizon",
            "at&t", "phone bill", "utility", "utilities", "natural gas",
        ),
        "Travel" to listOf(
            "airline", "flight", "delta air", "united air", "southwest air", "jetblue",
            "hotel", "marriott", "hilton", "hyatt", "airbnb", "travel", "uber", "lyft",
            "car rental", "hertz", "avis", "per diem",
        ),
        "Taxes" to listOf(
            "irs ", "tax payment", "franchise tax", "sales tax", "payroll tax",
            "estimated tax", "state tax", "dept of revenue",
        ),
        "Insurance" to listOf("insurance", "liability policy", "workers comp", "policy premium"),
        "Repairs & Maintenance" to listOf(
            "repair", "maintenance", "hvac", "plumb", "electrician",
            "janitorial", "cleaning service",
        ),
        "Cost of Goods Sold (COGS)" to listOf(
            "inventory", "wholesale", "supplier", "raw material",
            "materials", "freight", "shipping", "fulfillment",
            "manufactur", "packaging", "ups ", "fedex", "usps",
        ),
        "Supplies" to listOf("supplies", "staples", "office depot", "uline", "printer", "stationery"),
        "Loan Payments" to listOf(
            "loan payment", "sba loan", "line of credit", "interest charge",
            "term loan", "merchant advance",
        ),
    )

    private val businessIncome: List<Pair<String, List<String>>> = listOf(
        "Subscriptions" to listOf("subscription", "recurring", "membership", "mrr", "renewal"),
        "Service Revenue" to listOf("service", "consulting", "retainer", "labor", "installation"),
        "Sales Revenue" to listOf(
            "sale", "order", "shopify", "stripe", "square", "paypal", "etsy",
            "amazon", "ebay", "woocommerce", "pos deposit", "card settlement",
        ),
        "Interest Earned" to listOf("interest", "dividend", "treasury", "money market"),
        "Other Fees" to listOf("fee", "late charge", "surcharge", "penalty"),
    )
}
