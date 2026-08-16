/**
 * Questions.kt
 *
 * Port of iOS Features/Taxes/Engine/Questions.swift — the interview data:
 * sections, the question bank, and the pure answers → TaxReturn2025 converter.
 * Same section ids, question ids, input types, options, dependsOn predicates,
 * and the same answer→field mapping.
 *
 * The coercion helpers mirror the originals exactly: only a number answer is
 * numeric, only boolean true is truthy, only a string answer is a string — so
 * a half-filled interview can never turn a blank into a figure.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// MARK: - Answers

sealed class AnswerValue {
    data class Num(val value: Double) : AnswerValue()
    data class Str(val value: String) : AnswerValue()
    data class Bool(val value: Boolean) : AnswerValue()
}

typealias Answers = Map<String, AnswerValue>

private fun n(a: Answers, id: String): Double = (a[id] as? AnswerValue.Num)?.value ?: 0.0

private fun b(a: Answers, id: String): Boolean = (a[id] as? AnswerValue.Bool)?.value == true

private fun intOf(a: Answers, id: String): Int = max(0, floor(n(a, id)).toInt())

private fun str(a: Answers, id: String): String? = (a[id] as? AnswerValue.Str)?.value

// MARK: - Sections

data class Section(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val dependsOn: ((Answers) -> Boolean)? = null,
)

private fun truthy(a: Answers, id: String): Boolean = b(a, id)

val SECTIONS: List<Section> = listOf(
    Section("about-you", "About you", "Filing status and a few basics.", "User"),
    Section("dependents", "Family & dependents", "Children and others you support.", "Users") { truthy(it, "ls_deps") },
    Section("income-job", "Job income", "Wages from your W-2.", "Briefcase") { truthy(it, "ls_job") },
    Section("income-self", "Self-employment", "Freelance or business income.", "Store") { truthy(it, "ls_self") },
    Section("income-investments", "Investments", "Interest, dividends, and sales.", "TrendingUp") { truthy(it, "ls_invest") },
    Section("income-retirement", "Retirement & Social Security", "Pensions, IRAs, and benefits.", "PiggyBank") { truthy(it, "ls_retire") },
    Section("income-other", "Other income", "Anything else taxable.", "Coins"),
    Section("adjustments", "Adjustments", "Above-the-line deductions.", "Sliders"),
    Section("deductions", "Deductions", "Standard vs. itemized.", "Receipt"),
    Section("credits", "Credits", "Care, education, savings, and energy.", "Gift") {
        truthy(it, "ls_care") || truthy(it, "ls_education") || truthy(it, "ls_energy") || truthy(it, "ls_savings")
    },
    Section("payments", "Payments", "Withholding and estimates.", "Wallet"),
)

// MARK: - Questions

enum class InputType { Dollar, Integer, Boolean, Select, Text }

data class QuestionOption(val value: String, val label: String)

data class Question(
    val id: String,
    val sectionId: String,
    val text: String,
    val helpText: String? = null,
    val inputType: InputType,
    val options: List<QuestionOption>? = null,
    val placeholder: String? = null,
    val allowNegative: Boolean = false,
    val dependsOn: ((Answers) -> Boolean)? = null,
)

private val STATE_OPTIONS: List<QuestionOption> =
    SUPPORTED_STATES.map { QuestionOption(it.code.name, it.name) }

private fun isMFS(a: Answers): Boolean = str(a, "q_filing") == "mfs"

private fun isMarried(a: Answers): Boolean {
    val f = str(a, "q_filing")
    return f == "mfj" || f == "qss"
}

val QUESTION_BANK: List<Question> = listOf(
    // ---- About you ----
    Question(
        "q_filing", "about-you", "What's your filing status?",
        helpText = "Your status sets your tax brackets and standard deduction.",
        inputType = InputType.Select,
        options = listOf(
            QuestionOption("single", "Single"),
            QuestionOption("mfj", "Married filing jointly"),
            QuestionOption("mfs", "Married filing separately"),
            QuestionOption("hoh", "Head of household"),
            QuestionOption("qss", "Qualifying surviving spouse"),
        ),
    ),
    Question(
        "q_lived_apart", "about-you", "Did you live apart from your spouse for the entire tax year?",
        helpText = "Affects how your Social Security benefits and some credits are treated.",
        inputType = InputType.Boolean, dependsOn = ::isMFS,
    ),
    Question("q_age", "about-you", "How old were you at the end of the tax year?", inputType = InputType.Integer, placeholder = "e.g. 40"),
    Question("q_blind", "about-you", "Are you legally blind?", helpText = "Adds to your standard deduction.", inputType = InputType.Boolean),
    Question("q_spouse_age", "about-you", "How old was your spouse at the end of the tax year?", inputType = InputType.Integer, dependsOn = ::isMarried),
    Question("q_spouse_blind", "about-you", "Is your spouse legally blind?", inputType = InputType.Boolean, dependsOn = ::isMarried),
    Question(
        "q_claimed_dependent", "about-you", "Can someone else claim you as a dependent?",
        helpText = "If yes, your standard deduction may be limited.", inputType = InputType.Boolean,
    ),
    Question(
        "q_state", "about-you", "Which state did you live in?",
        helpText = "Adds a state income tax estimate (top 15 states). Leave blank to skip.",
        inputType = InputType.Select, options = STATE_OPTIONS,
    ),

    // ---- Dependents ----
    Question(
        "q_qual_children", "dependents", "How many qualifying children under 17 did you support?",
        helpText = "Each can qualify for the $2,200 Child Tax Credit.",
        inputType = InputType.Integer, placeholder = "0",
    ),
    Question(
        "q_other_deps", "dependents", "How many other dependents did you support?",
        helpText = "Each can qualify for the $500 Credit for Other Dependents.",
        inputType = InputType.Integer, placeholder = "0",
    ),

    // ---- Job income ----
    Question("q_wages", "income-job", "Total wages (W-2 box 1)", inputType = InputType.Dollar),
    Question("q_withholding", "income-job", "Federal income tax withheld (W-2 box 2)", inputType = InputType.Dollar),
    Question(
        "q_tips", "income-job", "Tips reported at work (W-2 box 7)",
        helpText = "New for 2025: up to $25,000 of reported tips is deductible. It phases out at higher incomes.",
        inputType = InputType.Dollar, dependsOn = { !isMFS(it) },
    ),
    Question(
        "q_overtime", "income-job", "Overtime premium pay (the extra half-time portion)",
        helpText = "New for 2025: the overtime premium your employer reports is deductible, up to $12,500 " +
            "($25,000 filing jointly). Only the premium half counts, not the whole overtime check.",
        inputType = InputType.Dollar, dependsOn = { !isMFS(it) },
    ),

    // ---- Self-employment ----
    Question("q_se_profit", "income-self", "Net self-employment profit (after expenses)", inputType = InputType.Dollar, allowNegative = true),
    Question(
        "q_se_sstb", "income-self", "Is this a professional-service business (law, health, consulting, finance, etc.)?",
        helpText = "Specified service businesses lose the QBI deduction at higher incomes.",
        inputType = InputType.Boolean,
    ),
    Question("q_se_health", "income-self", "Self-employed health insurance premiums", inputType = InputType.Dollar),

    // ---- Investments ----
    Question("q_interest", "income-investments", "Taxable interest (1099-INT box 1)", inputType = InputType.Dollar),
    Question("q_tax_exempt", "income-investments", "Tax-exempt interest (1099-INT box 8)", inputType = InputType.Dollar),
    Question("q_ord_div", "income-investments", "Ordinary dividends (1099-DIV box 1a)", inputType = InputType.Dollar),
    Question(
        "q_qual_div", "income-investments", "Qualified dividends (1099-DIV box 1b)",
        helpText = "Taxed at lower capital-gains rates.", inputType = InputType.Dollar,
    ),
    Question("q_ltcg", "income-investments", "Long-term capital gain or loss", inputType = InputType.Dollar, allowNegative = true),
    Question("q_stcg", "income-investments", "Short-term capital gain or loss", inputType = InputType.Dollar, allowNegative = true),
    Question("q_capgain_dist", "income-investments", "Capital gain distributions (1099-DIV box 2a)", inputType = InputType.Dollar),

    // ---- Retirement ----
    Question("q_ss_benefits", "income-retirement", "Social Security benefits (1099-SSA box 5)", inputType = InputType.Dollar),
    Question("q_retire_taxable", "income-retirement", "Taxable pension/IRA distributions (1099-R box 2a)", inputType = InputType.Dollar),
    Question(
        "q_retire_early", "income-retirement", "Was any of that an early withdrawal (under 59½, no exception)?",
        helpText = "Early withdrawals usually add a 10% penalty.",
        inputType = InputType.Boolean, dependsOn = { n(it, "q_retire_taxable") > 0 },
    ),

    // ---- Other income ----
    Question("q_unemployment", "income-other", "Unemployment compensation (1099-G)", inputType = InputType.Dollar),
    Question("q_other_income", "income-other", "Other taxable income", inputType = InputType.Dollar),

    // ---- Adjustments ----
    Question("q_student_loan", "adjustments", "Student loan interest paid", inputType = InputType.Dollar),
    Question("q_educator", "adjustments", "Educator (K-12) classroom expenses", inputType = InputType.Dollar),
    Question(
        "q_hsa_coverage", "adjustments", "Did you have a high-deductible health plan (HSA)?",
        inputType = InputType.Select,
        options = listOf(
            QuestionOption("none", "No HSA"),
            QuestionOption("self-only", "Self-only coverage"),
            QuestionOption("family", "Family coverage"),
        ),
    ),
    Question(
        "q_hsa_contribution", "adjustments", "HSA contribution", inputType = InputType.Dollar,
        dependsOn = { val c = str(it, "q_hsa_coverage"); c == "self-only" || c == "family" },
    ),
    Question("q_ira_contribution", "adjustments", "Traditional IRA contribution", inputType = InputType.Dollar),
    Question(
        "q_ira_covered", "adjustments", "Are you covered by a workplace retirement plan?",
        inputType = InputType.Boolean, dependsOn = { n(it, "q_ira_contribution") > 0 },
    ),

    // ---- Deductions ----
    Question(
        "q_itemize", "deductions", "Do you want to enter itemized deductions?",
        helpText = "We'll automatically use whichever is larger, standard or itemized.",
        inputType = InputType.Boolean,
    ),
    Question("q_mortgage_interest", "deductions", "Home mortgage interest", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question("q_mortgage_balance", "deductions", "Mortgage balance", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question("q_salt", "deductions", "State & local income (or sales) tax", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question("q_property_tax", "deductions", "Property taxes", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question("q_charitable", "deductions", "Charitable contributions (cash)", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question("q_medical", "deductions", "Medical & dental expenses", inputType = InputType.Dollar, dependsOn = { b(it, "q_itemize") }),
    Question(
        "q_car_loan_interest", "deductions", "Interest paid on a loan for a new car",
        helpText = "New for 2025: up to $10,000 of interest on a loan (taken out after 2024) for a NEW " +
            "personal vehicle assembled in the US is deductible, even with the standard deduction. Used " +
            "cars and leases don't qualify.",
        inputType = InputType.Dollar,
    ),

    // ---- Credits ----
    Question("q_care_expenses", "credits", "Child/dependent care expenses paid", inputType = InputType.Dollar, dependsOn = { b(it, "ls_care") }),
    Question("q_care_children", "credits", "How many children under 13 were in care?", inputType = InputType.Integer, placeholder = "0", dependsOn = { b(it, "ls_care") }),
    Question("q_edu_expenses", "credits", "Qualified tuition & fees paid", inputType = InputType.Dollar, dependsOn = { b(it, "ls_education") }),
    Question(
        "q_edu_aotc", "credits", "Is the student in their first 4 years of an undergraduate degree?",
        helpText = "If yes, the more generous American Opportunity Credit applies.",
        inputType = InputType.Boolean, dependsOn = { b(it, "ls_education") },
    ),
    Question("q_savers_contrib", "credits", "Retirement contributions (for the Saver's Credit)", inputType = InputType.Dollar, dependsOn = { b(it, "ls_savings") }),
    Question("q_clean_energy", "credits", "Home clean-energy property cost (solar, etc.)", inputType = InputType.Dollar, dependsOn = { b(it, "ls_energy") }),
    Question("q_ev_credit", "credits", "Clean vehicle (EV) credit amount", inputType = InputType.Dollar, dependsOn = { b(it, "ls_energy") }),

    // ---- Payments ----
    Question("q_est_payments", "payments", "Estimated tax payments made", inputType = InputType.Dollar),
    Question("q_extra_withholding", "payments", "Other federal tax withheld (not on your W-2)", inputType = InputType.Dollar),
    Question(
        "q_state_withholding", "payments", "State income tax withheld (W-2 box 17)",
        inputType = InputType.Dollar,
        dependsOn = { str(it, "q_state")?.isNotEmpty() == true },
    ),
    Question("q_prior_tax", "payments", "Your 2024 total tax (for the underpayment check)", inputType = InputType.Dollar),
    Question("q_prior_agi", "payments", "Your 2024 AGI", inputType = InputType.Dollar),
)

// MARK: - Router

/** Sections whose gate the current answers satisfy, in order. */
fun visibleSections(a: Answers): List<Section> =
    SECTIONS.filter { it.dependsOn?.invoke(a) ?: true }

/** The visible questions of one section, in order. */
fun questionsFor(sectionId: String, a: Answers): List<Question> =
    QUESTION_BANK.filter { it.sectionId == sectionId && (it.dependsOn?.invoke(a) ?: true) }

/** Every visible question across every visible section — the interview order. */
fun visibleQuestions(a: Answers): List<Question> =
    visibleSections(a).flatMap { questionsFor(it.id, a) }

/** Percent of the visible sections the user has already been through. */
fun getProgress(visitedSectionIds: List<String>, a: Answers): Int {
    val visible = visibleSections(a)
    if (visible.isEmpty()) return 0
    val visited = visible.count { visitedSectionIds.contains(it.id) }
    return (visited.toDouble() / visible.size * 100).roundToInt()
}

// MARK: - buildReturn

/** An age answer becomes a mid-year DOB; blank stays blank. */
private fun dobFromAge(age: Double): String {
    if (age <= 0) return ""
    return "${2025 - age.toInt()}-06-15"
}

/** Pure converter: interview answers → canonical TaxReturn2025. */
fun buildReturn(a: Answers): TaxReturn2025 {
    val filingStatus = str(a, "q_filing")
        ?.let { raw -> FilingStatus.entries.firstOrNull { it.raw == raw } }
        ?: FilingStatus.Single

    val taxpayer = TaxpayerInfo(
        dateOfBirth = dobFromAge(n(a, "q_age")),
        blind = b(a, "q_blind"),
        claimedAsDependentByAnother = b(a, "q_claimed_dependent"),
    )
    val spouse = if (filingStatus == FilingStatus.Mfj || filingStatus == FilingStatus.Qss) {
        taxpayer.copy(dateOfBirth = dobFromAge(n(a, "q_spouse_age")), blind = b(a, "q_spouse_blind"))
    } else null

    // ---- Dependents ----
    val dependents = mutableListOf<Dependent>()
    repeat(intOf(a, "q_qual_children")) { i ->
        dependents.add(
            Dependent(
                id = "qc-$i", firstName = "Child", dateOfBirth = "2018-01-01",
                relationshipType = DependentRelationshipType.Child, relationship = "child",
                qualifiesForCTC = true, qualifiesForEITC = true,
            )
        )
    }
    repeat(intOf(a, "q_other_deps")) { i ->
        dependents.add(
            Dependent(
                id = "od-$i", firstName = "Dependent", dateOfBirth = "1990-01-01",
                relationshipType = DependentRelationshipType.Relative, relationship = "relative",
                qualifiesForODC = true,
            )
        )
    }

    // ---- Income ----
    val flags = IncomeFlags(
        hasW2 = n(a, "q_wages") > 0 || n(a, "q_withholding") > 0,
        hasInterest = n(a, "q_interest") > 0 || n(a, "q_tax_exempt") > 0,
        hasDividends = n(a, "q_ord_div") > 0 || n(a, "q_qual_div") > 0 || n(a, "q_capgain_dist") > 0,
        hasCapitalGains = n(a, "q_ltcg") != 0.0 || n(a, "q_stcg") != 0.0,
        hasRetirementDistributions = n(a, "q_retire_taxable") > 0,
        hasSocialSecurity = n(a, "q_ss_benefits") > 0,
        hasSelfEmployment = n(a, "q_se_profit") != 0.0,
        hasUnemployment = n(a, "q_unemployment") > 0,
        hasOtherIncome = n(a, "q_other_income") > 0,
    )

    val wages = n(a, "q_wages")
    val w2 = if (flags.hasW2) {
        listOf(
            W2(
                id = "w2-0", owner = IncomeOwner.Taxpayer, employerName = "Employer",
                box1Wages = wages, box2FederalWithholding = n(a, "q_withholding"),
                box3SsWages = wages, box5MedicareWages = wages,
            )
        )
    } else emptyList()

    val scheduleC = if (flags.hasSelfEmployment) {
        listOf(
            ScheduleCBusiness(
                id = "c-0", owner = IncomeOwner.Taxpayer, businessName = "Self-employment",
                description = "Business", grossReceipts = n(a, "q_se_profit"), isSSTB = b(a, "q_se_sstb"),
            )
        )
    } else emptyList()

    val f1099Int = if (flags.hasInterest) {
        listOf(
            Form1099Int(
                id = "int-0", payer = "Bank",
                box1Interest = n(a, "q_interest"), box8TaxExemptInterest = n(a, "q_tax_exempt"),
            )
        )
    } else emptyList()

    val ordDiv = n(a, "q_ord_div")
    val qualDiv = n(a, "q_qual_div")
    val f1099Div = if (flags.hasDividends) {
        listOf(
            Form1099Div(
                id = "div-0", payer = "Brokerage",
                // Qualified dividends are a subset of ordinary, so the larger
                // of the two is what box 1a must carry.
                box1aOrdinaryDividends = maxOf(ordDiv, qualDiv),
                box1bQualifiedDividends = qualDiv,
                box2aCapitalGainDistributions = n(a, "q_capgain_dist"),
            )
        )
    } else emptyList()

    val ltcg = n(a, "q_ltcg")
    val stcg = n(a, "q_stcg")
    val f1099B = buildList {
        if (ltcg != 0.0) {
            add(
                CapitalTransaction(
                    id = "b-lt", description = "Long-term",
                    proceeds = if (ltcg > 0) ltcg else 0.0,
                    costBasis = if (ltcg < 0) -ltcg else 0.0,
                    longTerm = true,
                )
            )
        }
        if (stcg != 0.0) {
            add(
                CapitalTransaction(
                    id = "b-st", description = "Short-term",
                    proceeds = if (stcg > 0) stcg else 0.0,
                    costBasis = if (stcg < 0) -stcg else 0.0,
                    longTerm = false,
                )
            )
        }
    }

    val f1099Ssa = if (flags.hasSocialSecurity) {
        listOf(Form1099Ssa(id = "ssa-0", box5NetBenefits = n(a, "q_ss_benefits")))
    } else emptyList()

    val f1099R = if (flags.hasRetirementDistributions) {
        listOf(
            Form1099R(
                id = "r-0", payer = "Plan",
                box1GrossDistribution = n(a, "q_retire_taxable"),
                box2aTaxableAmount = n(a, "q_retire_taxable"),
                // Code 1 = early, no known exception; 7 = normal.
                box7DistributionCode = if (b(a, "q_retire_early")) "1" else "7",
            )
        )
    } else emptyList()

    val f1099G = if (flags.hasUnemployment) {
        listOf(Form1099G(id = "g-0", payer = "State", box1Unemployment = n(a, "q_unemployment")))
    } else emptyList()

    // ---- Credits: care ----
    val careChildren = intOf(a, "q_care_children")
    var hasCareExpenses = false
    var care = CareCreditInput()
    if (n(a, "q_care_expenses") > 0 && careChildren > 0) {
        hasCareExpenses = true
        val earned = wages + maxOf(0.0, n(a, "q_se_profit"))
        care = CareCreditInput(
            expenses = n(a, "q_care_expenses"),
            taxpayerEarnedIncome = earned,
            spouseEarnedIncome = earned,
        )
        // Mark existing CTC children first, then add care-only children for
        // any remainder so the count the user gave is honoured exactly.
        var marked = 0
        for (idx in dependents.indices) {
            if (marked >= careChildren) break
            if (dependents[idx].qualifiesForCTC) {
                dependents[idx] = dependents[idx].copy(qualifiesForCareCredit = true)
                marked += 1
            }
        }
        for (i in marked until careChildren) {
            dependents.add(
                Dependent(
                    id = "care-$i", firstName = "Child", dateOfBirth = "2020-01-01",
                    relationshipType = DependentRelationshipType.Child, relationship = "child",
                    qualifiesForCareCredit = true,
                )
            )
        }
    }

    val students = if (n(a, "q_edu_expenses") > 0) {
        listOf(
            EducationStudent(
                id = "s0", name = "Student",
                qualifiedExpenses = n(a, "q_edu_expenses"),
                aotcEligible = b(a, "q_edu_aotc"),
            )
        )
    } else emptyList()

    val hsaRaw = str(a, "q_hsa_coverage")
    val hsaCoverage = when (hsaRaw) {
        "self-only" -> HsaCoverage.SelfOnly
        "family" -> HsaCoverage.Family
        else -> HsaCoverage.None
    }

    val stateCode = str(a, "q_state")?.takeIf { it.isNotEmpty() }
        ?.let { code -> StateCode.entries.firstOrNull { it.name == code } }

    return TaxReturn2025(
        taxpayer = taxpayer,
        spouse = spouse,
        filingStatus = filingStatus,
        dependents = dependents,
        residency = Residency(
            state = stateCode,
            stateWithholding = if (stateCode != null) n(a, "q_state_withholding") else 0.0,
        ),
        livedApartFromSpouse = b(a, "q_lived_apart"),
        income = IncomeData(
            w2 = w2,
            f1099Int = f1099Int,
            f1099Div = f1099Div,
            f1099B = f1099B,
            f1099R = f1099R,
            f1099Ssa = f1099Ssa,
            f1099G = f1099G,
            scheduleC = scheduleC,
            otherIncome = if (flags.hasOtherIncome) n(a, "q_other_income") else 0.0,
            flags = flags,
        ),
        adjustments = Adjustments(
            educatorExpenses = n(a, "q_educator"),
            hsaContribution = n(a, "q_hsa_contribution"),
            hsaCoverage = hsaCoverage,
            selfEmployedHealthInsurance = n(a, "q_se_health"),
            traditionalIraContribution = n(a, "q_ira_contribution"),
            coveredByWorkplacePlan = b(a, "q_ira_covered"),
            studentLoanInterest = n(a, "q_student_loan"),
        ),
        newDeductions = NewDeductions2025(
            qualifiedTips = n(a, "q_tips"),
            qualifiedOvertime = n(a, "q_overtime"),
            carLoanInterest = n(a, "q_car_loan_interest"),
        ),
        itemized = if (b(a, "q_itemize")) {
            ItemizedDeductions(
                medicalExpenses = n(a, "q_medical"),
                stateLocalIncomeOrSalesTax = n(a, "q_salt"),
                realEstateTaxes = n(a, "q_property_tax"),
                mortgageInterest = n(a, "q_mortgage_interest"),
                mortgageBalance = n(a, "q_mortgage_balance"),
                charitableCash = n(a, "q_charitable"),
            )
        } else ItemizedDeductions(),
        credits = CreditInputs(
            students = students,
            hasEducationExpenses = n(a, "q_edu_expenses") > 0,
            care = care,
            hasCareExpenses = hasCareExpenses,
            retirementContributions = n(a, "q_savers_contrib"),
            cleanEnergyCost = n(a, "q_clean_energy"),
            evCreditAmount = n(a, "q_ev_credit"),
        ),
        payments = Payments(
            additionalWithholding = n(a, "q_extra_withholding"),
            estimatedPayments = n(a, "q_est_payments"),
            priorYearTax = n(a, "q_prior_tax").takeIf { it > 0 },
            priorYearAgi = n(a, "q_prior_agi").takeIf { it > 0 },
        ),
    )
}

// MARK: - Life situations (the gates the sections depend on)

/** One "check all that apply" option on the Life Situations screen. */
data class LifeSituationOption(val id: String, val label: String, val icon: String)

val LIFE_SITUATIONS: List<LifeSituationOption> = listOf(
    LifeSituationOption("ls_job", "I earned wages from a job (W-2)", "Briefcase"),
    LifeSituationOption("ls_self", "I was self-employed or freelanced", "Store"),
    LifeSituationOption("ls_invest", "I had investments (interest, dividends, or sales)", "TrendingUp"),
    LifeSituationOption("ls_retire", "I received retirement income or Social Security", "PiggyBank"),
    LifeSituationOption("ls_deps", "I have children or other dependents", "Users"),
    LifeSituationOption("ls_itemize", "I owned a home or have large deductions", "Home"),
    LifeSituationOption("ls_education", "I paid for higher education", "GraduationCap"),
    LifeSituationOption("ls_care", "I paid for child or dependent care", "Baby"),
    LifeSituationOption("ls_savings", "I contributed to an IRA, HSA, or retirement plan", "Landmark"),
    LifeSituationOption("ls_energy", "I bought an EV or made home energy upgrades", "Zap"),
)

/**
 * Heuristic "you might also qualify for…" nudges based on answers + result.
 * Port of computeSuggestions in TaxInsightViews.swift — same conditions,
 * same order, same copy.
 */
fun computeSuggestions(a: Answers, result: TaxCalculationResult): List<String> {
    val out = mutableListOf<String>()
    val hasKids = ((a["q_qual_children"] as? AnswerValue.Num)?.value ?: 0.0) > 0
    fun notChecked(id: String) = !b(a, id)
    fun isTrue(id: String) = b(a, id)

    if (hasKids && notChecked("ls_care")) {
        out.add(
            "You have children. If you paid for daycare or after-school care, check “I paid for child " +
                "or dependent care” to claim the Child & Dependent Care Credit."
        )
    }
    if (hasKids && notChecked("ls_education")) {
        out.add(
            "Paying for college? Check “I paid for higher education”. The American Opportunity " +
                "Credit is worth up to $2,500 per student."
        )
    }
    if (isTrue("ls_self") && notChecked("ls_savings")) {
        out.add(
            "As a self-employed filer, contributing to a SEP-IRA or solo 401(k) can lower your taxable " +
                "income. Check “I contributed to an IRA, HSA, or retirement plan.”"
        )
    }
    if (notChecked("ls_savings") && result.agi > 0 && result.agi < 40_000) {
        out.add(
            "At your income, retirement contributions may earn the Saver’s Credit (up to 50% back). " +
                "Check “I contributed to an IRA, HSA, or retirement plan.”"
        )
    }
    if (result.deductionUsed == DeductionUsed.Standard && notChecked("ls_itemize")) {
        out.add(
            "We used the standard deduction. If you own a home or made large charitable gifts, check " +
                "“I owned a home or have large deductions” to compare itemizing."
        )
    }
    return out
}
