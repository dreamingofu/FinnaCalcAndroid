/**
 * TaxStages.kt
 *
 * Ports of the tax-computation pipeline stages from iOS Features/Taxes/Engine:
 * Adjustments, DeductionCompare, RegularTax, QualifiedDivCapGain, Amt, Qbi,
 * ObbbaDeductions.
 */

package com.finnacalc.android.features.taxes.engine

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

// MARK: - Age helpers

/** Parses an ISO date (yyyy-MM-dd or full timestamp); null when unparseable. */
internal fun parseTaxDate(s: String): LocalDate? {
    if (s.isEmpty()) return null
    return runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}

/** HSA catch-up: 55+ during 2025 means born on or before 1970-12-31. */
private fun isAge55For2025(dateOfBirth: String): Boolean {
    val dob = parseTaxDate(dateOfBirth) ?: return false
    return !dob.isAfter(LocalDate.of(1970, 12, 31))
}

/** IRA catch-up: 50+ during 2025 means born on or before 1975-12-31. */
fun isAge50For2025(dateOfBirth: String): Boolean {
    val dob = parseTaxDate(dateOfBirth) ?: return false
    return !dob.isAfter(LocalDate.of(1975, 12, 31))
}

/** For TY2025, a person is treated as 65+ if born before January 2, 1961. */
fun isConsidered65For2025(dateOfBirth: String): Boolean {
    val dob = parseTaxDate(dateOfBirth) ?: return false
    return !dob.isAfter(LocalDate.of(1961, 1, 1))
}

/** Age at the end of 2025, or null when there's no usable DOB. */
fun ageAtEndOf2025(dateOfBirth: String): Double? {
    val dob = parseTaxDate(dateOfBirth) ?: return null
    return (2025 - dob.year).toDouble()
}

// MARK: - Above-the-line adjustments

fun educatorDeduction(r: TaxReturn2025): Double =
    min(maxOf(0.0, r.adjustments.educatorExpenses), EDUCATOR_EXPENSE_2025.perEducator)

fun hsaDeduction(r: TaxReturn2025): Double {
    val cov = r.adjustments.hsaCoverage
    if (cov == HsaCoverage.None) return 0.0
    var limit = if (cov == HsaCoverage.Family) HSA_2025.family else HSA_2025.selfOnly
    if (isAge55For2025(r.taxpayer.dateOfBirth)) limit += HSA_2025.catchUp
    return min(maxOf(0.0, r.adjustments.hsaContribution), limit)
}

fun seHealthDeduction(
    r: TaxReturn2025,
    totalNetSe: Double,
    seTaxDeduction: Double,
    sepContribution: Double,
): Double {
    val ceiling = maxOf(0.0, totalNetSe - seTaxDeduction - maxOf(0.0, sepContribution))
    return min(maxOf(0.0, r.adjustments.selfEmployedHealthInsurance), ceiling)
}

/**
 * Traditional IRA deduction with the 2025 MAGI phaseout. Only phases out if
 * the contributor is an active workplace-plan participant (or, for MFJ, the
 * spouse is). A non-zero phased deduction is rounded UP to $10 and floored at
 * $200.
 */
fun iraDeduction(
    contribution: Double,
    magi: Double,
    status: FilingStatus,
    coveredByPlan: Boolean,
    spouseCoveredByPlan: Boolean,
    age50: Boolean,
): Double {
    val limit = if (age50) IRA_2025.contributionLimitAge50 else IRA_2025.contributionLimit
    val eligible = min(maxOf(0.0, contribution), limit)
    if (eligible <= 0) return 0.0

    // No applicable phaseout range means the full contribution is deductible.
    val range: PhaseoutRange = when {
        coveredByPlan -> when (status) {
            FilingStatus.Mfj, FilingStatus.Qss -> IRA_2025.phaseout.coveredMfj
            FilingStatus.Mfs -> IRA_2025.phaseout.coveredMfs
            else -> IRA_2025.phaseout.coveredSingleHoh
        }
        (status == FilingStatus.Mfj || status == FilingStatus.Qss) && spouseCoveredByPlan ->
            IRA_2025.phaseout.spouseCoveredMfj
        status == FilingStatus.Mfs && spouseCoveredByPlan -> IRA_2025.phaseout.coveredMfs
        else -> return eligible
    }

    if (magi <= range.start) return eligible
    if (magi >= range.end) return 0.0
    val ratio = (range.end - magi) / (range.end - range.start)
    var deduction = eligible * ratio
    deduction = ceil(deduction / IRA_2025.roundUpTo) * IRA_2025.roundUpTo
    if (deduction > 0 && deduction < IRA_2025.minPhasedDeduction) {
        deduction = IRA_2025.minPhasedDeduction
    }
    return min(deduction, eligible)
}

fun studentLoanInterestDeduction(paid: Double, magi: Double, status: FilingStatus): Double {
    if (status == FilingStatus.Mfs) return 0.0
    val eligible = min(maxOf(0.0, paid), STUDENT_LOAN_INTEREST_2025.maxDeduction)
    if (eligible <= 0) return 0.0
    val phaseout = STUDENT_LOAN_INTEREST_2025.phaseout[status] ?: return eligible
    if (magi <= phaseout.start) return eligible
    if (magi >= phaseout.end) return 0.0
    return eligible - eligible * ((magi - phaseout.start) / (phaseout.end - phaseout.start))
}

// MARK: - Regular tax

/** Tax from the rate schedule (exact, in cents) on a positive amount. */
fun bracketTax(amount: Double, status: FilingStatus): Double {
    if (amount <= 0) return 0.0
    var tax = 0.0
    for (b in ORDINARY_BRACKETS_2025.getValue(status)) {
        if (amount > b.min) {
            val upper = min(amount, b.max)
            tax += (upper - b.min) * b.rate
        }
    }
    return tax
}

/** The marginal rate that applies at a given taxable income. */
fun marginalRate(taxableIncome: Double, status: FilingStatus): Double {
    var rate = 0.0
    for (b in ORDINARY_BRACKETS_2025.getValue(status)) {
        if (taxableIncome > b.min) rate = b.rate
    }
    return rate
}

/**
 * The IRS Tax Table taxes the midpoint of a $50 bucket. Below $50 the table
 * uses irregular small rows: $0–5, $5–15, $15–25, $25–50.
 */
private fun taxTableBasis(ti: Double): Double = when {
    ti < 5 -> 2.5
    ti < 15 -> 10.0
    ti < 25 -> 20.0
    ti < 50 -> 37.5
    else -> floor(ti / 50) * 50 + 25
}

data class RegularTaxResult(val tax: Double, val usedTaxTable: Boolean, val marginalRate: Double)

/** Regular tax on ordinary taxable income (Tax Table vs Computation Worksheet). */
fun computeRegularTax(taxableIncome: Double, status: FilingStatus): RegularTaxResult {
    val ti = if (taxableIncome > 0) taxableIncome else 0.0
    val mr = marginalRate(ti, status)
    // Under $100,000 the IRS requires the Tax Table (bucket midpoints).
    if (ti < 100_000) {
        return RegularTaxResult(dollar(bracketTax(taxTableBasis(ti), status)), true, mr)
    }
    return RegularTaxResult(dollar(bracketTax(ti, status)), false, mr)
}

// MARK: - Deduction: standard vs itemized

/** Count the age-65/blind "boxes" that drive the additional standard deduction. */
private fun countAdditionalBoxes(r: TaxReturn2025): Double {
    var boxes = 0.0
    if (isConsidered65For2025(r.taxpayer.dateOfBirth)) boxes += 1
    if (r.taxpayer.blind) boxes += 1
    if (r.filingStatus == FilingStatus.Mfj || r.filingStatus == FilingStatus.Qss) {
        r.spouse?.let { spouse ->
            if (isConsidered65For2025(spouse.dateOfBirth)) boxes += 1
            if (spouse.blind) boxes += 1
        }
    }
    return boxes
}

fun computeStandardDeduction(r: TaxReturn2025, earnedIncome: Double): Double {
    val status = r.filingStatus
    val base = STANDARD_DEDUCTION_2025.getValue(status)
    val additionalPerBox = if (isMarriedStatus(status)) {
        ADDITIONAL_STD_DEDUCTION_2025.married
    } else {
        ADDITIONAL_STD_DEDUCTION_2025.unmarried
    }
    val additional = countAdditionalBoxes(r) * additionalPerBox
    var baseDeduction = base
    if (r.taxpayer.claimedAsDependentByAnother) {
        val limited = maxOf(
            DEPENDENT_STD_DEDUCTION_2025.floor,
            earnedIncome + DEPENDENT_STD_DEDUCTION_2025.earnedIncomeBump,
        )
        baseDeduction = min(base, limited)
    }
    return baseDeduction + additional
}

fun computeItemizedDeduction(r: TaxReturn2025, agi: Double): Double {
    val it = r.itemized
    val status = r.filingStatus
    val medical = nonNeg(it.medicalExpenses - agi * MEDICAL_AGI_FLOOR_2025)
    val saltRaw = it.stateLocalIncomeOrSalesTax + it.realEstateTaxes + it.personalPropertyTaxes
    val salt = min(saltRaw, effectiveSaltCap(status, agi))
    val mortgageLimit = if (it.mortgageAfterDec2017) {
        if (status == FilingStatus.Mfs) MORTGAGE_DEBT_LIMIT_2025.postDec2017Mfs
        else MORTGAGE_DEBT_LIMIT_2025.postDec2017
    } else {
        if (status == FilingStatus.Mfs) MORTGAGE_DEBT_LIMIT_2025.grandfatheredMfs
        else MORTGAGE_DEBT_LIMIT_2025.grandfathered
    }
    val mortgage = if (it.mortgageBalance > mortgageLimit) {
        nonNeg(it.mortgageInterest) * (mortgageLimit / it.mortgageBalance)
    } else {
        nonNeg(it.mortgageInterest)
    }
    val charitableCash = min(it.charitableCash, agi * CHARITABLE_LIMITS_2025.cashPctOfAgi)
    val charitableNonCash = min(it.charitableNonCash, agi * CHARITABLE_LIMITS_2025.nonCashPctOfAgi)
    val casualty = nonNeg(it.casualtyLosses)
    return medical + salt + mortgage + charitableCash + charitableNonCash + casualty
}

data class DeductionResult(
    val standard: Double,
    val itemized: Double,
    val used: DeductionUsed,
    val amount: Double,
    /** Federal tax saved by the chosen deduction vs the alternative (estimate). */
    val itemizedSavings: Double,
)

fun computeDeduction(r: TaxReturn2025, agi: Double, earnedIncome: Double): DeductionResult {
    val status = r.filingStatus
    val standard = computeStandardDeduction(r, earnedIncome)
    val itemized = computeItemizedDeduction(r, agi)
    val useItemized = r.forceItemize || itemized > standard
    val used = if (useItemized) DeductionUsed.Itemized else DeductionUsed.Standard
    val amount = if (used == DeductionUsed.Itemized) itemized else standard
    val taxStandard = computeRegularTax(nonNeg(agi - standard), status).tax
    val taxItemized = computeRegularTax(nonNeg(agi - itemized), status).tax
    val itemizedSavings = nonNeg(taxStandard - taxItemized)
    return DeductionResult(standard, itemized, used, amount, itemizedSavings)
}

// MARK: - Preferential rates (qualified dividends & LTCG)

data class PreferentialStackResult(
    val tax: Double,
    val amountAt0: Double,
    val amountAt15: Double,
    val amountAt20: Double,
)

/**
 * The 0/15/20% tax on `preferential` income stacked on top of `ordinaryBelow`.
 * Shared by the regular worksheet and the AMT computation (AMT uses the same
 * preferential capital-gains rates).
 */
fun preferentialStackTax(
    ordinaryBelow: Double,
    preferential: Double,
    status: FilingStatus,
): PreferentialStackResult {
    val breakpoints = CAP_GAIN_BREAKPOINTS_2025.getValue(status)
    val zeroRateMax = breakpoints.zeroRateMax
    val fifteenRateMax = breakpoints.fifteenRateMax
    val top = ordinaryBelow + preferential
    val amountAt0 = maxOf(0.0, min(top, zeroRateMax) - ordinaryBelow)
    val amountAt15 = maxOf(0.0, min(top, fifteenRateMax) - maxOf(ordinaryBelow, zeroRateMax))
    val amountAt20 = maxOf(0.0, top - maxOf(ordinaryBelow, fifteenRateMax))
    return PreferentialStackResult(
        tax = amountAt15 * 0.15 + amountAt20 * 0.2,
        amountAt0 = amountAt0,
        amountAt15 = amountAt15,
        amountAt20 = amountAt20,
    )
}

data class QualDivResult(
    val tax: Double,
    val preferentialIncome: Double,
    val amountAt0: Double,
    val amountAt15: Double,
    val amountAt20: Double,
)

/** The Qualified Dividends and Capital Gain Tax Worksheet. */
fun computeQualifiedDivCapGainTax(
    taxableIncome: Double,
    qualifiedDividends: Double,
    netCapitalGain: Double,
    status: FilingStatus,
): QualDivResult {
    val ti = maxOf(0.0, taxableIncome)
    val preferential = maxOf(0.0, min(qualifiedDividends + netCapitalGain, ti))
    val ordinary = maxOf(0.0, ti - preferential)
    val stack = preferentialStackTax(ordinary, preferential, status)
    val ordinaryTax = computeRegularTax(ordinary, status).tax
    val stacked = ordinaryTax + stack.tax
    // The worksheet never charges more than taxing everything as ordinary.
    val allOrdinary = computeRegularTax(ti, status).tax
    return QualDivResult(
        tax = dollar(min(stacked, allOrdinary)),
        preferentialIncome = preferential,
        amountAt0 = stack.amountAt0,
        amountAt15 = stack.amountAt15,
        amountAt20 = stack.amountAt20,
    )
}

// MARK: - AMT

data class AmtResult(
    val amt: Double,
    val tentativeMinimumTax: Double,
    val amti: Double,
    val exemption: Double,
)

data class ComputeAmtParams(
    val taxableIncome: Double,
    val addBacks: Double,
    val preferentialIncome: Double,
    val regularTax: Double,
    val status: FilingStatus,
)

fun computeAmt(params: ComputeAmtParams): AmtResult {
    val amti = maxOf(0.0, params.taxableIncome + params.addBacks)
    val status = params.status
    val fullExemption = AMT_2025.exemption.getValue(status)
    val phaseStart = AMT_2025.exemptionPhaseoutThreshold.getValue(status)
    val exemption = if (amti > phaseStart) {
        maxOf(0.0, fullExemption - AMT_2025.exemptionPhaseoutRate * (amti - phaseStart))
    } else {
        fullExemption
    }
    val base = maxOf(0.0, amti - exemption)
    val pref = maxOf(0.0, min(params.preferentialIncome, base))
    val ordinaryBase = base - pref
    val bk = if (status == FilingStatus.Mfs) AMT_2025.rate28ThresholdMfs else AMT_2025.rate28Threshold
    val ordinaryTmt = if (ordinaryBase <= bk) {
        ordinaryBase * AMT_2025.lowRate
    } else {
        bk * AMT_2025.lowRate + (ordinaryBase - bk) * AMT_2025.highRate
    }
    val prefTmt = preferentialStackTax(ordinaryBase, pref, status).tax
    val tentativeMinimumTax = dollar(ordinaryTmt + prefTmt)
    return AmtResult(
        amt = maxOf(0.0, tentativeMinimumTax - params.regularTax),
        tentativeMinimumTax = tentativeMinimumTax,
        amti = amti,
        exemption = exemption,
    )
}

// MARK: - QBI (§199A)

data class QbiResult(val deduction: Double, val wageLimitMayApply: Boolean)

data class ComputeQbiDeductionParams(
    val qbiIncome: Double,
    val taxableIncomeBeforeQbi: Double,
    val netCapitalGain: Double,
    val isSSTB: Boolean,
    val status: FilingStatus,
)

fun computeQbiDeduction(params: ComputeQbiDeductionParams): QbiResult {
    val qbiIncome = params.qbiIncome
    if (qbiIncome <= 0) return QbiResult(0.0, false)
    val overallLimit = QBI_2025.rate * maxOf(0.0, params.taxableIncomeBeforeQbi - params.netCapitalGain)
    val threshold = QBI_2025.threshold.getValue(params.status)
    val range = QBI_2025.phaseInRange.getValue(params.status)

    if (params.taxableIncomeBeforeQbi <= threshold) {
        return QbiResult(min(QBI_2025.rate * qbiIncome, overallLimit), false)
    }
    val over = params.taxableIncomeBeforeQbi - threshold
    if (params.isSSTB) {
        if (over >= range) return QbiResult(0.0, false)
        val applicablePct = 1 - over / range
        return QbiResult(min(QBI_2025.rate * qbiIncome * applicablePct, overallLimit), false)
    }
    // Above the threshold the W-2 wage / UBIA limit can apply; we don't track
    // business W-2 wages, so the caller warns that the deduction may be high.
    return QbiResult(min(QBI_2025.rate * qbiIncome, overallLimit), true)
}
