/**
 * TaxConstants.kt
 *
 * 2025 tax constants — single source of truth.
 *
 * Port of iOS Features/Taxes/Engine/TaxConstants.swift (itself a port of the
 * web tax-engine's constants). Same names, same numeric literals, same bracket
 * tables.
 *
 * MASTER SOURCES (Tax Year 2025, returns filed in 2026):
 *  - Rev. Proc. 2024-40 — annual inflation adjustments (brackets, cap-gain
 *    breakpoints, EITC table, AMT, student loan / educator phaseouts, etc.)
 *  - One Big Beautiful Bill Act (P.L. 119-21, July 2025) — standard deduction,
 *    CTC $2,200, SALT cap, and the new senior / tips / overtime / car-loan
 *    interest deductions for 2025.
 *  - 2025 Form 1040 and Instructions; Tax Rate Schedules; Tax Table.
 *  - Schedule 8812 (2025); Schedule SE / Form 8959 / Form 8960 (2025).
 *  - Social Security Administration — 2025 wage base ($176,100).
 *
 * RULE: no calculation module may contain a numeric tax literal. Every IRS
 * value lives here, annotated with its source, so accuracy is auditable in one
 * place.
 */

package com.finnacalc.android.features.taxes.engine

// MARK: - brackets

/** A 2025 ordinary-income bracket: [min, max) of TAXABLE income at `rate`. */
data class Bracket(
    /** Marginal rate as a decimal (0.22 = 22%). */
    val rate: Double,
    val min: Double,
    /** Exclusive upper bound; Infinity for the top bracket. */
    val max: Double,
)

private val SINGLE_BRACKETS = listOf(
    Bracket(0.10, 0.0, 11_925.0),
    Bracket(0.12, 11_925.0, 48_475.0),
    Bracket(0.22, 48_475.0, 103_350.0),
    Bracket(0.24, 103_350.0, 197_300.0),
    Bracket(0.32, 197_300.0, 250_525.0),
    Bracket(0.35, 250_525.0, 626_350.0),
    Bracket(0.37, 626_350.0, Double.POSITIVE_INFINITY),
)

private val MFJ_BRACKETS = listOf(
    Bracket(0.10, 0.0, 23_850.0),
    Bracket(0.12, 23_850.0, 96_950.0),
    Bracket(0.22, 96_950.0, 206_700.0),
    Bracket(0.24, 206_700.0, 394_600.0),
    Bracket(0.32, 394_600.0, 501_050.0),
    Bracket(0.35, 501_050.0, 751_600.0),
    Bracket(0.37, 751_600.0, Double.POSITIVE_INFINITY),
)

private val MFS_BRACKETS = listOf(
    Bracket(0.10, 0.0, 11_925.0),
    Bracket(0.12, 11_925.0, 48_475.0),
    Bracket(0.22, 48_475.0, 103_350.0),
    Bracket(0.24, 103_350.0, 197_300.0),
    Bracket(0.32, 197_300.0, 250_525.0),
    Bracket(0.35, 250_525.0, 375_800.0),
    Bracket(0.37, 375_800.0, Double.POSITIVE_INFINITY),
)

private val HOH_BRACKETS = listOf(
    Bracket(0.10, 0.0, 17_000.0),
    Bracket(0.12, 17_000.0, 64_850.0),
    Bracket(0.22, 64_850.0, 103_350.0),
    Bracket(0.24, 103_350.0, 197_300.0),
    Bracket(0.32, 197_300.0, 250_500.0),
    Bracket(0.35, 250_500.0, 626_350.0),
    Bracket(0.37, 626_350.0, Double.POSITIVE_INFINITY),
)

val ORDINARY_BRACKETS_2025: Map<FilingStatus, List<Bracket>> = mapOf(
    FilingStatus.Single to SINGLE_BRACKETS,
    FilingStatus.Mfj to MFJ_BRACKETS,
    // QSS uses the MFJ schedule.
    FilingStatus.Qss to MFJ_BRACKETS,
    FilingStatus.Mfs to MFS_BRACKETS,
    FilingStatus.Hoh to HOH_BRACKETS,
)

/** Taxable-income thresholds where the 0%→15% and 15%→20% preferential rates begin. */
data class CapGainBreakpoints(val zeroRateMax: Double, val fifteenRateMax: Double)

val CAP_GAIN_BREAKPOINTS_2025: Map<FilingStatus, CapGainBreakpoints> = mapOf(
    FilingStatus.Single to CapGainBreakpoints(48_350.0, 533_400.0),
    FilingStatus.Mfj to CapGainBreakpoints(96_700.0, 600_050.0),
    FilingStatus.Qss to CapGainBreakpoints(96_700.0, 600_050.0),
    FilingStatus.Mfs to CapGainBreakpoints(48_350.0, 300_000.0),
    FilingStatus.Hoh to CapGainBreakpoints(64_750.0, 566_700.0),
)

// MARK: - standard deductions

val STANDARD_DEDUCTION_2025: Map<FilingStatus, Double> = mapOf(
    FilingStatus.Single to 15_750.0,
    FilingStatus.Mfj to 31_500.0,
    FilingStatus.Qss to 31_500.0,
    FilingStatus.Mfs to 15_750.0,
    FilingStatus.Hoh to 23_625.0,
)

/**
 * Additional standard deduction per "box" checked (age 65+ and/or blind).
 * Unmarried (single, HOH) get the larger amount; married statuses the smaller.
 */
data class AdditionalStdDeduction2025(val unmarried: Double, val married: Double)

val ADDITIONAL_STD_DEDUCTION_2025 = AdditionalStdDeduction2025(unmarried = 2_000.0, married = 1_600.0)

/** Dependent standard deduction floor and earned-income bump. */
data class DependentStdDeduction2025(val floor: Double, val earnedIncomeBump: Double)

val DEPENDENT_STD_DEDUCTION_2025 = DependentStdDeduction2025(floor = 1_350.0, earnedIncomeBump = 450.0)

fun isMarriedStatus(status: FilingStatus): Boolean =
    status == FilingStatus.Mfj || status == FilingStatus.Mfs || status == FilingStatus.Qss

// MARK: - child tax credit

data class Ctc2025(
    val perChild: Double,
    val perOtherDependent: Double,
    /** Maximum REFUNDABLE Additional CTC per qualifying child. */
    val refundableCapPerChild: Double,
    val earnedIncomeThreshold: Double,
    val earnedIncomeRate: Double,
    /** Credit drops $50 per $1,000 (or fraction) of MAGI over the threshold. */
    val phaseoutPer1000: Double,
    val phaseoutIncrement: Double,
)

val CTC_2025 = Ctc2025(
    perChild = 2_200.0,
    perOtherDependent = 500.0,
    refundableCapPerChild = 1_700.0,
    earnedIncomeThreshold = 2_500.0,
    earnedIncomeRate = 0.15,
    phaseoutPer1000 = 50.0,
    phaseoutIncrement = 1_000.0,
)

val CTC_PHASEOUT_THRESHOLD_2025: Map<FilingStatus, Double> = mapOf(
    FilingStatus.Single to 200_000.0,
    FilingStatus.Hoh to 200_000.0,
    FilingStatus.Mfs to 200_000.0,
    FilingStatus.Qss to 200_000.0,
    FilingStatus.Mfj to 400_000.0,
)

// MARK: - SE tax / Medicare / NIIT

data class SeTax2025(
    /** Schedule SE line 4a: 92.35%. */
    val netEarningsFactor: Double,
    val socialSecurityRate: Double,
    val medicareRate: Double,
    val socialSecurityWageBase: Double,
    val deductibleFraction: Double,
)

val SE_TAX_2025 = SeTax2025(
    netEarningsFactor = 0.9235,
    socialSecurityRate = 0.124,
    medicareRate = 0.029,
    socialSecurityWageBase = 176_100.0,
    deductibleFraction = 0.5,
)

data class AdditionalMedicare2025(val rate: Double, val thresholds: Map<FilingStatus, Double>)

val ADDITIONAL_MEDICARE_2025 = AdditionalMedicare2025(
    rate = 0.009,
    thresholds = mapOf(
        FilingStatus.Single to 200_000.0,
        FilingStatus.Hoh to 200_000.0,
        FilingStatus.Qss to 200_000.0,
        FilingStatus.Mfj to 250_000.0,
        FilingStatus.Mfs to 125_000.0,
    ),
)

data class Niit2025(val rate: Double, val thresholds: Map<FilingStatus, Double>)

val NIIT_2025 = Niit2025(
    rate = 0.038,
    thresholds = mapOf(
        FilingStatus.Single to 200_000.0,
        FilingStatus.Hoh to 200_000.0,
        FilingStatus.Qss to 250_000.0,
        FilingStatus.Mfj to 250_000.0,
        FilingStatus.Mfs to 125_000.0,
    ),
)

/** Annual capital loss deduction limit (Schedule D). Source: IRC §1211(b). */
val CAPITAL_LOSS_LIMIT_2025: Map<FilingStatus, Double> = mapOf(
    FilingStatus.Single to 3_000.0,
    FilingStatus.Mfj to 3_000.0,
    FilingStatus.Qss to 3_000.0,
    FilingStatus.Hoh to 3_000.0,
    FilingStatus.Mfs to 1_500.0,
)

/** Medical expense AGI floor for itemized deductions. Source: IRC §213(a). */
const val MEDICAL_AGI_FLOOR_2025: Double = 0.075

/**
 * SALT cap. Source: IRC §164(b)(6) as amended by OBBBA §70120: $40,000 for
 * 2025 ($20,000 MFS), phased DOWN by 30% of MAGI over $500,000 ($250,000
 * MFS), but never below the old $10,000/$5,000.
 */
data class SaltCap2025(
    val standard: Double,
    val mfs: Double,
    val phasedownThreshold: Double,
    val phasedownThresholdMfs: Double,
    val phasedownRate: Double,
    val floor: Double,
    val floorMfs: Double,
)

val SALT_CAP_2025 = SaltCap2025(
    standard = 40_000.0,
    mfs = 20_000.0,
    phasedownThreshold = 500_000.0,
    phasedownThresholdMfs = 250_000.0,
    phasedownRate = 0.3,
    floor = 10_000.0,
    floorMfs = 5_000.0,
)

/** The SALT cap that actually applies at a given MAGI (OBBBA phase-down). */
fun effectiveSaltCap(status: FilingStatus, magi: Double): Double {
    val c = SALT_CAP_2025
    val cap = if (status == FilingStatus.Mfs) c.mfs else c.standard
    val threshold = if (status == FilingStatus.Mfs) c.phasedownThresholdMfs else c.phasedownThreshold
    val floorAmount = if (status == FilingStatus.Mfs) c.floorMfs else c.floor
    val reduced = cap - c.phasedownRate * maxOf(0.0, magi - threshold)
    return maxOf(floorAmount, reduced)
}

/** Charitable AGI limits. Source: IRC §170(b). */
data class CharitableLimits2025(val cashPctOfAgi: Double, val nonCashPctOfAgi: Double)

val CHARITABLE_LIMITS_2025 = CharitableLimits2025(cashPctOfAgi = 0.6, nonCashPctOfAgi = 0.3)

/** Mortgage acquisition-debt limits. Source: IRC §163(h)(3). */
data class MortgageDebtLimit2025(
    val postDec2017: Double,
    val postDec2017Mfs: Double,
    val grandfathered: Double,
    val grandfatheredMfs: Double,
)

val MORTGAGE_DEBT_LIMIT_2025 = MortgageDebtLimit2025(
    postDec2017 = 750_000.0,
    postDec2017Mfs = 375_000.0,
    grandfathered = 1_000_000.0,
    grandfatheredMfs = 500_000.0,
)

/** A MAGI phaseout start/end range. */
data class PhaseoutRange(val start: Double, val end: Double)

/** Student loan interest deduction. Source: IRC §221. */
data class StudentLoanInterest2025(
    val maxDeduction: Double,
    val phaseout: Map<FilingStatus, PhaseoutRange>,
)

val STUDENT_LOAN_INTEREST_2025 = StudentLoanInterest2025(
    maxDeduction = 2_500.0,
    phaseout = mapOf(
        FilingStatus.Single to PhaseoutRange(85_000.0, 100_000.0),
        FilingStatus.Hoh to PhaseoutRange(85_000.0, 100_000.0),
        FilingStatus.Qss to PhaseoutRange(85_000.0, 100_000.0),
        FilingStatus.Mfj to PhaseoutRange(170_000.0, 200_000.0),
        // MFS cannot claim the student loan interest deduction.
        FilingStatus.Mfs to PhaseoutRange(0.0, 0.0),
    ),
)

/** Educator expense above-the-line deduction. Source: IRC §62(a)(2)(D). */
data class EducatorExpense2025(val perEducator: Double)

val EDUCATOR_EXPENSE_2025 = EducatorExpense2025(perEducator = 300.0)

/** Additional tax on early retirement distributions. Source: IRC §72(t). */
data class EarlyWithdrawalPenalty2025(
    val rate: Double,
    /** Box 7 codes meaning "early distribution, no known exception applies". */
    val earlyNoExceptionCodes: List<String>,
)

val EARLY_WITHDRAWAL_PENALTY_2025 = EarlyWithdrawalPenalty2025(
    rate = 0.1,
    earlyNoExceptionCodes = listOf("1", "J", "S"),
)

// MARK: - social security

data class SsTaxability2025(val maxInclusionRate: Double, val firstTierRate: Double)

val SS_TAXABILITY_2025 = SsTaxability2025(maxInclusionRate = 0.85, firstTierRate = 0.5)

data class SsBaseAmounts(val base1: Double, val base2: Double)

/**
 * Base amounts (worksheet lines 8 and 11): below base1 nothing is taxable;
 * above base2 the 85% tier applies. MFS taxpayers who lived WITH their spouse
 * use $0/$0 (almost always 85% taxable).
 */
fun ssBaseAmounts(status: FilingStatus, livedApartFromSpouse: Boolean): SsBaseAmounts {
    if (status == FilingStatus.Mfj) return SsBaseAmounts(32_000.0, 44_000.0)
    if (status == FilingStatus.Mfs && !livedApartFromSpouse) return SsBaseAmounts(0.0, 0.0)
    // single, hoh, qss, and mfs-who-lived-apart-all-year
    return SsBaseAmounts(25_000.0, 34_000.0)
}

// MARK: - retirement

data class Ira2025Phaseout(
    val coveredSingleHoh: PhaseoutRange,
    val coveredMfj: PhaseoutRange,
    /** Contributor NOT covered, but spouse IS covered (MFJ). */
    val spouseCoveredMfj: PhaseoutRange,
    val coveredMfs: PhaseoutRange,
)

data class Ira2025(
    val contributionLimit: Double,
    val contributionLimitAge50: Double,
    val phaseout: Ira2025Phaseout,
    /** A non-zero phased-out deduction is at least $200, rounded up to $10. */
    val minPhasedDeduction: Double,
    val roundUpTo: Double,
)

val IRA_2025 = Ira2025(
    contributionLimit = 7_000.0,
    contributionLimitAge50 = 8_000.0,
    phaseout = Ira2025Phaseout(
        coveredSingleHoh = PhaseoutRange(79_000.0, 89_000.0),
        coveredMfj = PhaseoutRange(126_000.0, 146_000.0),
        spouseCoveredMfj = PhaseoutRange(236_000.0, 246_000.0),
        coveredMfs = PhaseoutRange(0.0, 10_000.0),
    ),
    minPhasedDeduction = 200.0,
    roundUpTo = 10.0,
)

data class Hsa2025(val selfOnly: Double, val family: Double, val catchUp: Double, val catchUpAge: Double)

val HSA_2025 = Hsa2025(selfOnly = 4_300.0, family = 8_550.0, catchUp = 1_000.0, catchUpAge = 55.0)

// MARK: - EITC

/** One EITC bracket (piecewise-linear phase-in / plateau / phase-out). */
data class EitcBracket(
    val earnedIncomeAmount: Double,
    val maxCredit: Double,
    val phaseInRate: Double,
    val phaseoutRate: Double,
    val phaseoutThreshold: Double,
    val phaseoutThresholdMfj: Double,
)

/** Indexed by number of qualifying children (0, 1, 2, 3 = "3 or more"). */
val EITC_2025: List<EitcBracket> = listOf(
    EitcBracket(8_490.0, 649.0, 0.0765, 0.0765, 10_620.0, 17_730.0),
    EitcBracket(12_730.0, 4_328.0, 0.34, 0.1598, 23_350.0, 30_470.0),
    EitcBracket(17_880.0, 7_152.0, 0.40, 0.2106, 23_350.0, 30_470.0),
    EitcBracket(17_880.0, 8_046.0, 0.45, 0.2106, 23_350.0, 30_470.0),
)

val EITC_INVESTMENT_INCOME_LIMIT_2025: Double = 11_950.0

data class EitcChildlessAge(val min: Double, val maxExclusive: Double)

val EITC_CHILDLESS_AGE = EitcChildlessAge(min = 25.0, maxExclusive = 65.0)

// MARK: - QBI

data class Qbi2025(
    val rate: Double,
    val threshold: Map<FilingStatus, Double>,
    val phaseInRange: Map<FilingStatus, Double>,
)

val QBI_2025 = Qbi2025(
    rate = 0.2,
    threshold = mapOf(
        FilingStatus.Single to 197_300.0,
        FilingStatus.Hoh to 197_300.0,
        FilingStatus.Mfs to 197_300.0,
        FilingStatus.Qss to 197_300.0,
        FilingStatus.Mfj to 394_600.0,
    ),
    phaseInRange = mapOf(
        FilingStatus.Single to 50_000.0,
        FilingStatus.Hoh to 50_000.0,
        FilingStatus.Mfs to 50_000.0,
        FilingStatus.Qss to 50_000.0,
        FilingStatus.Mfj to 100_000.0,
    ),
)

// MARK: - AMT

data class Amt2025(
    val exemption: Map<FilingStatus, Double>,
    val exemptionPhaseoutThreshold: Map<FilingStatus, Double>,
    val exemptionPhaseoutRate: Double,
    val rate28Threshold: Double,
    val rate28ThresholdMfs: Double,
    val lowRate: Double,
    val highRate: Double,
)

val AMT_2025 = Amt2025(
    exemption = mapOf(
        FilingStatus.Single to 88_100.0,
        FilingStatus.Hoh to 88_100.0,
        FilingStatus.Mfj to 137_000.0,
        FilingStatus.Qss to 137_000.0,
        FilingStatus.Mfs to 68_500.0,
    ),
    exemptionPhaseoutThreshold = mapOf(
        FilingStatus.Single to 626_350.0,
        FilingStatus.Hoh to 626_350.0,
        FilingStatus.Mfs to 626_350.0,
        FilingStatus.Mfj to 1_252_700.0,
        FilingStatus.Qss to 1_252_700.0,
    ),
    exemptionPhaseoutRate = 0.25,
    rate28Threshold = 239_100.0,
    rate28ThresholdMfs = 119_550.0,
    lowRate = 0.26,
    highRate = 0.28,
)

// MARK: - credits

/** Child & Dependent Care Credit (Form 2441) — not inflation-indexed. */
data class CareCredit2025(
    val expenseCapOnePerson: Double,
    val expenseCapTwoPlus: Double,
    val maxRate: Double,
    val minRate: Double,
    val fullRateAgiCeiling: Double,
    val rateStepIncome: Double,
    val rateStep: Double,
)

val CARE_CREDIT_2025 = CareCredit2025(
    expenseCapOnePerson = 3_000.0,
    expenseCapTwoPlus = 6_000.0,
    maxRate = 0.35,
    minRate = 0.2,
    fullRateAgiCeiling = 15_000.0,
    rateStepIncome = 2_000.0,
    rateStep = 0.01,
)

data class EducationCreditsAotc(
    val firstTier: Double,
    val secondTier: Double,
    val secondTierRate: Double,
    val max: Double,
    val refundablePortion: Double,
    val maxPriorYears: Double,
)

data class EducationCreditsLlc(val rate: Double, val expenseCap: Double, val max: Double)

data class EducationCredits2025(
    val aotc: EducationCreditsAotc,
    val llc: EducationCreditsLlc,
    val phaseout: Map<FilingStatus, PhaseoutRange>,
)

val EDUCATION_CREDITS_2025 = EducationCredits2025(
    aotc = EducationCreditsAotc(
        firstTier = 2_000.0,
        secondTier = 2_000.0,
        secondTierRate = 0.25,
        max = 2_500.0,
        refundablePortion = 0.4,
        maxPriorYears = 4.0,
    ),
    llc = EducationCreditsLlc(rate = 0.2, expenseCap = 10_000.0, max = 2_000.0),
    phaseout = mapOf(
        FilingStatus.Single to PhaseoutRange(80_000.0, 90_000.0),
        FilingStatus.Hoh to PhaseoutRange(80_000.0, 90_000.0),
        FilingStatus.Qss to PhaseoutRange(80_000.0, 90_000.0),
        FilingStatus.Mfj to PhaseoutRange(160_000.0, 180_000.0),
        // MFS cannot claim education credits.
        FilingStatus.Mfs to PhaseoutRange(0.0, 0.0),
    ),
)

data class SaversCreditTier(val rate: Double, val agiCeiling: Double)

data class SaversCredit2025(
    /** Per person; $4,000 combined for MFJ. */
    val contributionCap: Double,
    val tiers: Map<FilingStatus, List<SaversCreditTier>>,
)

private val SAVERS_SINGLE_TIERS = listOf(
    SaversCreditTier(0.5, 23_750.0),
    SaversCreditTier(0.2, 26_000.0),
    SaversCreditTier(0.1, 39_500.0),
)

val SAVERS_CREDIT_2025 = SaversCredit2025(
    contributionCap = 2_000.0,
    tiers = mapOf(
        FilingStatus.Single to SAVERS_SINGLE_TIERS,
        FilingStatus.Mfs to SAVERS_SINGLE_TIERS,
        FilingStatus.Qss to SAVERS_SINGLE_TIERS,
        FilingStatus.Hoh to listOf(
            SaversCreditTier(0.5, 35_625.0),
            SaversCreditTier(0.2, 39_000.0),
            SaversCreditTier(0.1, 59_250.0),
        ),
        FilingStatus.Mfj to listOf(
            SaversCreditTier(0.5, 47_500.0),
            SaversCreditTier(0.2, 52_000.0),
            SaversCreditTier(0.1, 79_000.0),
        ),
    ),
)

/** Residential Clean Energy Credit (Form 5695) — 30% of qualified property cost. */
data class CleanEnergy2025(val rate: Double)

val CLEAN_ENERGY_2025 = CleanEnergy2025(rate = 0.3)

// MARK: - OBBBA deductions new for 2025 (P.L. 119-21)

/**
 * Additional deduction for seniors (OBBBA §70103, 2025-2028): $6,000 per
 * person 65+, allowed whether or not the return itemizes. Phases out at 6% of
 * MAGI over the threshold.
 */
data class SeniorDeduction2025(
    val perPerson: Double,
    val phaseoutRate: Double,
    val threshold: Map<FilingStatus, Double>,
)

val SENIOR_DEDUCTION_2025 = SeniorDeduction2025(
    perPerson = 6_000.0,
    phaseoutRate = 0.06,
    threshold = mapOf(
        FilingStatus.Single to 75_000.0,
        FilingStatus.Hoh to 75_000.0,
        FilingStatus.Mfs to 75_000.0,
        FilingStatus.Qss to 75_000.0,
        FilingStatus.Mfj to 150_000.0,
    ),
)

/**
 * "No tax on tips" (OBBBA §70201): qualified tips up to $25,000, reduced $100
 * per $1,000 of MAGI over the threshold. MFS ineligible.
 */
data class TipsDeduction2025(
    val cap: Double,
    val phaseoutRatePerDollar: Double,
    val threshold: Double,
    val thresholdMfj: Double,
)

val TIPS_DEDUCTION_2025 = TipsDeduction2025(
    cap = 25_000.0,
    phaseoutRatePerDollar = 0.1,
    threshold = 150_000.0,
    thresholdMfj = 300_000.0,
)

/**
 * "No tax on overtime" (OBBBA §70202): the FLSA half-time premium portion of
 * overtime pay, up to $12,500 ($25,000 MFJ), reduced $100 per $1,000 of MAGI
 * over the threshold. MFS ineligible.
 */
data class OvertimeDeduction2025(
    val cap: Double,
    val capMfj: Double,
    val phaseoutRatePerDollar: Double,
    val threshold: Double,
    val thresholdMfj: Double,
)

val OVERTIME_DEDUCTION_2025 = OvertimeDeduction2025(
    cap = 12_500.0,
    capMfj = 25_000.0,
    phaseoutRatePerDollar = 0.1,
    threshold = 150_000.0,
    thresholdMfj = 300_000.0,
)

/**
 * Car loan interest (OBBBA §70203): interest on a loan for a NEW personal-use
 * vehicle with final assembly in the US, up to $10,000, reduced $200 per
 * $1,000 of MAGI over the threshold.
 */
data class CarLoanInterest2025(
    val cap: Double,
    val phaseoutRatePerDollar: Double,
    val threshold: Double,
    val thresholdMfj: Double,
)

val CAR_LOAN_INTEREST_2025 = CarLoanInterest2025(
    cap = 10_000.0,
    phaseoutRatePerDollar = 0.2,
    threshold = 100_000.0,
    thresholdMfj = 200_000.0,
)

/** New Clean Vehicle Credit (Form 8936). */
data class EvCredit2025(val max: Double, val magiCap: Map<FilingStatus, Double>)

val EV_CREDIT_2025 = EvCredit2025(
    max = 7_500.0,
    magiCap = mapOf(
        FilingStatus.Single to 150_000.0,
        FilingStatus.Hoh to 225_000.0,
        FilingStatus.Mfs to 150_000.0,
        FilingStatus.Qss to 150_000.0,
        FilingStatus.Mfj to 300_000.0,
    ),
)
