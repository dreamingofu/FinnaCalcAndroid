/**
 * CreditStages.kt
 *
 * Ports of the credit / other-tax / payment stages from iOS
 * Features/Taxes/Engine: ObbbaDeductions, ChildTaxCredit, Eitc, CareCredit,
 * EducationCredits, OtherCredits, AdditionalMedicare, Niit, Payments,
 * PremiumTaxCredit.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.ceil
import kotlin.math.min

// MARK: - OBBBA deductions (2025-2028)

fun computeSeniorDeduction(r: TaxReturn2025, magi: Double): Double {
    var people = 0.0
    if (isConsidered65For2025(r.taxpayer.dateOfBirth)) people += 1
    if (r.filingStatus == FilingStatus.Mfj || r.filingStatus == FilingStatus.Qss) {
        r.spouse?.let { if (isConsidered65For2025(it.dateOfBirth)) people += 1 }
    }
    if (people == 0.0) return 0.0
    val base = people * SENIOR_DEDUCTION_2025.perPerson
    val threshold = SENIOR_DEDUCTION_2025.threshold.getValue(r.filingStatus)
    val reduction = SENIOR_DEDUCTION_2025.phaseoutRate * maxOf(0.0, magi - threshold)
    return nonNeg(base - reduction)
}

fun computeTipsDeduction(r: TaxReturn2025, magi: Double): Double {
    // Married filers must file jointly to claim.
    if (r.filingStatus == FilingStatus.Mfs) return 0.0
    val eligible = min(maxOf(0.0, r.newDeductions.qualifiedTips), TIPS_DEDUCTION_2025.cap)
    if (eligible <= 0) return 0.0
    val threshold = if (r.filingStatus == FilingStatus.Mfj) {
        TIPS_DEDUCTION_2025.thresholdMfj
    } else {
        TIPS_DEDUCTION_2025.threshold
    }
    val reduction = TIPS_DEDUCTION_2025.phaseoutRatePerDollar * maxOf(0.0, magi - threshold)
    return nonNeg(eligible - reduction)
}

fun computeOvertimeDeduction(r: TaxReturn2025, magi: Double): Double {
    if (r.filingStatus == FilingStatus.Mfs) return 0.0
    val cap = if (r.filingStatus == FilingStatus.Mfj) {
        OVERTIME_DEDUCTION_2025.capMfj
    } else {
        OVERTIME_DEDUCTION_2025.cap
    }
    val eligible = min(maxOf(0.0, r.newDeductions.qualifiedOvertime), cap)
    if (eligible <= 0) return 0.0
    val threshold = if (r.filingStatus == FilingStatus.Mfj) {
        OVERTIME_DEDUCTION_2025.thresholdMfj
    } else {
        OVERTIME_DEDUCTION_2025.threshold
    }
    val reduction = OVERTIME_DEDUCTION_2025.phaseoutRatePerDollar * maxOf(0.0, magi - threshold)
    return nonNeg(eligible - reduction)
}

fun computeCarLoanInterestDeduction(r: TaxReturn2025, magi: Double): Double {
    val eligible = min(maxOf(0.0, r.newDeductions.carLoanInterest), CAR_LOAN_INTEREST_2025.cap)
    if (eligible <= 0) return 0.0
    val threshold = if (r.filingStatus == FilingStatus.Mfj) {
        CAR_LOAN_INTEREST_2025.thresholdMfj
    } else {
        CAR_LOAN_INTEREST_2025.threshold
    }
    val reduction = CAR_LOAN_INTEREST_2025.phaseoutRatePerDollar * maxOf(0.0, magi - threshold)
    return nonNeg(eligible - reduction)
}

// MARK: - Child Tax Credit (Schedule 8812)

data class ChildTaxCreditResult(
    val qualifyingChildren: Double,
    val otherDependents: Double,
    val tentativeCredit: Double,
    val creditAfterPhaseout: Double,
    val nonrefundable: Double,
    val additionalChildTaxCredit: Double,
)

fun computeChildTaxCredit(
    r: TaxReturn2025,
    magi: Double,
    taxAvailable: Double,
    earnedIncome: Double,
): ChildTaxCreditResult {
    val qualifyingChildren = r.dependents.count { it.qualifiesForCTC }.toDouble()
    val otherDependents = r.dependents.count { it.qualifiesForODC }.toDouble()
    val tentativeCredit =
        qualifyingChildren * CTC_2025.perChild + otherDependents * CTC_2025.perOtherDependent
    val threshold = CTC_PHASEOUT_THRESHOLD_2025.getValue(r.filingStatus)
    var creditAfterPhaseout = tentativeCredit
    if (magi > threshold) {
        // $50 per $1,000 OR FRACTION over the threshold — hence the ceiling.
        val steps = ceil((magi - threshold) / CTC_2025.phaseoutIncrement)
        creditAfterPhaseout = nonNeg(tentativeCredit - steps * CTC_2025.phaseoutPer1000)
    }
    val nonrefundable = min(creditAfterPhaseout, nonNeg(taxAvailable))
    val leftover = nonNeg(creditAfterPhaseout - nonrefundable)
    val refundableCap = qualifyingChildren * CTC_2025.refundableCapPerChild
    val earnedFormula = nonNeg((earnedIncome - CTC_2025.earnedIncomeThreshold) * CTC_2025.earnedIncomeRate)
    val additionalChildTaxCredit = min(leftover, min(refundableCap, earnedFormula))
    return ChildTaxCreditResult(
        qualifyingChildren = qualifyingChildren,
        otherDependents = otherDependents,
        tentativeCredit = tentativeCredit,
        creditAfterPhaseout = creditAfterPhaseout,
        nonrefundable = nonrefundable,
        additionalChildTaxCredit = additionalChildTaxCredit,
    )
}

// MARK: - EITC

private fun eitcAtIncome(income: Double, bracketIndex: Int, status: FilingStatus): Double {
    if (income <= 0) return 0.0
    val b = EITC_2025[bracketIndex]
    val threshold = if (status == FilingStatus.Mfj) b.phaseoutThresholdMfj else b.phaseoutThreshold
    val phaseIn = min(b.maxCredit, b.phaseInRate * income)
    if (income <= threshold) return phaseIn
    return maxOf(0.0, b.maxCredit - b.phaseoutRate * (income - threshold))
}

data class EitcResult(
    val credit: Double,
    val eligible: Boolean,
    val disqualReason: String? = null,
)

data class ComputeEitcParams(
    val r: TaxReturn2025,
    val earnedIncome: Double,
    val agi: Double,
    val investmentIncome: Double,
    val taxpayerAge: Double? = null,
)

fun computeEitc(params: ComputeEitcParams): EitcResult {
    val r = params.r
    val status = r.filingStatus
    if (status == FilingStatus.Mfs && !r.livedApartFromSpouse) {
        return EitcResult(0.0, false, "MFS filers must have lived apart from their spouse.")
    }
    if (params.investmentIncome > EITC_INVESTMENT_INCOME_LIMIT_2025) {
        return EitcResult(
            0.0, false,
            "Investment income over $${"%,.0f".format(EITC_INVESTMENT_INCOME_LIMIT_2025)} disqualifies the EITC.",
        )
    }
    if (params.earnedIncome <= 0) return EitcResult(0.0, false)

    val qualifyingChildren = r.dependents.count { it.qualifiesForEITC }
    val bracketIndex = min(qualifyingChildren, 3)
    val age = params.taxpayerAge
    if (bracketIndex == 0 && age != null &&
        (age < EITC_CHILDLESS_AGE.min || age >= EITC_CHILDLESS_AGE.maxExclusive)
    ) {
        return EitcResult(0.0, false, "Childless EITC requires age 25–64.")
    }

    val threshold = if (status == FilingStatus.Mfj) {
        EITC_2025[bracketIndex].phaseoutThresholdMfj
    } else {
        EITC_2025[bracketIndex].phaseoutThreshold
    }
    val byEarned = eitcAtIncome(params.earnedIncome, bracketIndex, status)
    // Above the threshold the credit is the LESSER of the earned-income and
    // AGI computations.
    val credit = if (params.agi <= threshold) {
        byEarned
    } else {
        min(byEarned, eitcAtIncome(params.agi, bracketIndex, status))
    }
    return EitcResult(dollar(credit), credit > 0)
}

// MARK: - Child & dependent care credit (Form 2441)

fun computeCareCredit(r: TaxReturn2025, agi: Double): Double {
    if (!r.credits.hasCareExpenses) return 0.0
    if (r.filingStatus == FilingStatus.Mfs && !r.livedApartFromSpouse) return 0.0
    val qualifyingPersons = r.dependents.count { it.qualifiesForCareCredit }
    if (qualifyingPersons == 0) return 0.0
    val cap = if (qualifyingPersons == 1) {
        CARE_CREDIT_2025.expenseCapOnePerson
    } else {
        CARE_CREDIT_2025.expenseCapTwoPlus
    }
    val care = r.credits.care
    val effectiveCap = maxOf(0.0, cap - maxOf(0.0, care.employerBenefits))
    val earnedLimit = if (r.filingStatus == FilingStatus.Mfj) {
        min(care.taxpayerEarnedIncome, care.spouseEarnedIncome)
    } else {
        care.taxpayerEarnedIncome
    }
    val eligible = min(min(maxOf(0.0, care.expenses), effectiveCap), earnedLimit)
    if (eligible <= 0) return 0.0
    var rate = CARE_CREDIT_2025.maxRate
    if (agi > CARE_CREDIT_2025.fullRateAgiCeiling) {
        val steps = ceil((agi - CARE_CREDIT_2025.fullRateAgiCeiling) / CARE_CREDIT_2025.rateStepIncome)
        rate = maxOf(CARE_CREDIT_2025.minRate, CARE_CREDIT_2025.maxRate - steps * CARE_CREDIT_2025.rateStep)
    }
    return dollar(eligible * rate)
}

// MARK: - Education credits (Form 8863)

data class EducationResult(val nonrefundable: Double, val refundable: Double)

fun computeEducationCredits(r: TaxReturn2025, magi: Double): EducationResult {
    if (!r.credits.hasEducationExpenses || r.filingStatus == FilingStatus.Mfs) {
        return EducationResult(0.0, 0.0)
    }
    val phase = EDUCATION_CREDITS_2025.phaseout.getValue(r.filingStatus)
    val factor = when {
        magi <= phase.start -> 1.0
        magi >= phase.end -> 0.0
        else -> (phase.end - magi) / (phase.end - phase.start)
    }
    if (factor <= 0) return EducationResult(0.0, 0.0)

    val a = EDUCATION_CREDITS_2025.aotc
    var aotc = 0.0
    var llcExpenses = 0.0
    for (s in r.credits.students) {
        val aotcEligible = s.aotcEligible && s.priorAotcYears < a.maxPriorYears && !s.felonyDrugConviction
        if (aotcEligible) {
            // 100% of the first $2,000 + 25% of the next $2,000.
            val first = min(s.qualifiedExpenses, a.firstTier)
            val second = min(maxOf(0.0, s.qualifiedExpenses - first), a.secondTier) * a.secondTierRate
            aotc += min(first + second, a.max)
        } else {
            llcExpenses += maxOf(0.0, s.qualifiedExpenses)
        }
    }
    val llc = min(
        min(llcExpenses, EDUCATION_CREDITS_2025.llc.expenseCap) * EDUCATION_CREDITS_2025.llc.rate,
        EDUCATION_CREDITS_2025.llc.max,
    )
    val aotcAfter = aotc * factor
    val llcAfter = llc * factor
    val refundable = dollar(aotcAfter * a.refundablePortion)
    val nonrefundable = dollar(aotcAfter * (1 - a.refundablePortion) + llcAfter)
    return EducationResult(nonrefundable, refundable)
}

// MARK: - Other credits

fun computeSaversCredit(r: TaxReturn2025, agi: Double): Double {
    if (r.credits.isFullTimeStudent || r.taxpayer.claimedAsDependentByAnother) return 0.0
    val contribution = maxOf(0.0, r.credits.retirementContributions)
    if (contribution <= 0) return 0.0
    val perPersonCap = SAVERS_CREDIT_2025.contributionCap
    val cap = if (r.filingStatus == FilingStatus.Mfj) perPersonCap * 2 else perPersonCap
    val eligible = min(contribution, cap)
    var rate = 0.0
    for (tier in SAVERS_CREDIT_2025.tiers[r.filingStatus] ?: emptyList()) {
        if (agi <= tier.agiCeiling) {
            rate = tier.rate
            break
        }
    }
    return dollar(eligible * rate)
}

fun computeCleanEnergyCredit(r: TaxReturn2025): Double =
    dollar(maxOf(0.0, r.credits.cleanEnergyCost) * CLEAN_ENERGY_2025.rate)

fun computeEvCredit(r: TaxReturn2025, magi: Double): Double {
    if (magi > (EV_CREDIT_2025.magiCap[r.filingStatus] ?: 0.0)) return 0.0
    return min(maxOf(0.0, r.credits.evCreditAmount), EV_CREDIT_2025.max)
}

fun computeForeignTaxCredit(r: TaxReturn2025): Double = maxOf(0.0, r.credits.foreignTaxPaid)

// MARK: - Other taxes

/** Form 8959: 0.9% on wages then SE earnings, sharing one threshold. */
fun computeAdditionalMedicareTax(
    medicareWages: Double,
    seNetEarnings: Double,
    status: FilingStatus,
): Double {
    val threshold = ADDITIONAL_MEDICARE_2025.thresholds.getValue(status)
    val rate = ADDITIONAL_MEDICARE_2025.rate
    val onWages = maxOf(0.0, medicareWages - threshold) * rate
    val remainingThreshold = maxOf(0.0, threshold - medicareWages)
    val onSe = maxOf(0.0, maxOf(0.0, seNetEarnings) - remainingThreshold) * rate
    return onWages + onSe
}

/** Form 8960: 3.8% on the lesser of net investment income and MAGI over the threshold. */
fun computeNiit(netInvestmentIncome: Double, magi: Double, status: FilingStatus): Double {
    val threshold = NIIT_2025.thresholds.getValue(status)
    val base = min(maxOf(0.0, netInvestmentIncome), maxOf(0.0, magi - threshold))
    return base * NIIT_2025.rate
}

// MARK: - Payments

data class PaymentsResult(
    val withholding: Double,
    val estimatedPayments: Double,
    val total: Double,
)

fun computeWithholdingAndPayments(r: TaxReturn2025): PaymentsResult {
    val f = r.income.flags
    val w2Withholding = if (f.hasW2) sumBy(r.income.w2) { it.box2FederalWithholding } else 0.0
    val intWithholding = if (f.hasInterest) sumBy(r.income.f1099Int) { it.box4FederalWithholding } else 0.0
    val divWithholding = if (f.hasDividends) sumBy(r.income.f1099Div) { it.box4FederalWithholding } else 0.0
    val retirementWithholding = if (f.hasRetirementDistributions) {
        sumBy(r.income.f1099R) { it.box4FederalWithholding }
    } else 0.0
    val unemploymentWithholding = if (f.hasUnemployment) {
        sumBy(r.income.f1099G) { it.box4FederalWithholding }
    } else 0.0
    val ssaWithholding = if (f.hasSocialSecurity) {
        sumBy(r.income.f1099Ssa) { it.federalWithholding }
    } else 0.0
    val withholding = w2Withholding + intWithholding + divWithholding +
        retirementWithholding + unemploymentWithholding + ssaWithholding +
        r.payments.additionalWithholding
    val estimatedPayments = r.payments.estimatedPayments
    return PaymentsResult(withholding, estimatedPayments, withholding + estimatedPayments)
}

// MARK: - Premium tax credit

data class PtcResult(val netRefundable: Double, val repayment: Double)

fun computePremiumTaxCredit(r: TaxReturn2025): PtcResult {
    if (!r.credits.hasMarketplaceCoverage) return PtcResult(0.0, 0.0)
    val net = r.credits.premiumTaxCreditAllowed - r.credits.advancePremiumTaxCredit
    if (net >= 0) return PtcResult(dollar(net), 0.0)
    return PtcResult(0.0, dollar(-net))
}
