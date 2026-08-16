/**
 * IncomeStages.kt
 *
 * Ports of the income-side pipeline stages from iOS Features/Taxes/Engine:
 * GrossIncome, ScheduleC, ScheduleSE, CapitalGains, SocialSecurity.
 * Each is a pure function over TaxReturn2025 — same order of operations and
 * same formulas as the Swift/TS originals.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.abs
import kotlin.math.min

// MARK: - Gross income

data class GrossIncomeResult(
    val wages: Double,
    val taxableInterest: Double,
    val taxExemptInterest: Double,
    val ordinaryDividends: Double,
    val qualifiedDividends: Double,
    val unemployment: Double,
    val retirementDistributions: Double,
    val otherIncome: Double,
    val ordinaryTotal: Double,
    val wageEarnedIncome: Double,
)

fun computeGrossIncome(r: TaxReturn2025): GrossIncomeResult {
    val f = r.income.flags
    val wages = if (f.hasW2) sumBy(r.income.w2) { it.box1Wages } else 0.0
    val taxableInterest = if (f.hasInterest) {
        sumBy(r.income.f1099Int) { it.box1Interest + it.box3UsTreasuryInterest }
    } else 0.0
    val taxExemptInterest = if (f.hasInterest) {
        sumBy(r.income.f1099Int) { it.box8TaxExemptInterest }
    } else 0.0
    val ordinaryDividends = if (f.hasDividends) {
        sumBy(r.income.f1099Div) { it.box1aOrdinaryDividends }
    } else 0.0
    val qualifiedDividends = if (f.hasDividends) {
        sumBy(r.income.f1099Div) { it.box1bQualifiedDividends }
    } else 0.0
    val unemployment = if (f.hasUnemployment) sumBy(r.income.f1099G) { it.box1Unemployment } else 0.0
    val retirementDistributions = if (f.hasRetirementDistributions) {
        sumBy(r.income.f1099R) { it.box2aTaxableAmount }
    } else 0.0
    val otherIncome = if (f.hasOtherIncome) r.income.otherIncome else 0.0
    val ordinaryTotal =
        wages + taxableInterest + ordinaryDividends + retirementDistributions + unemployment + otherIncome
    return GrossIncomeResult(
        wages = wages,
        taxableInterest = taxableInterest,
        taxExemptInterest = taxExemptInterest,
        ordinaryDividends = ordinaryDividends,
        qualifiedDividends = qualifiedDividends,
        unemployment = unemployment,
        retirementDistributions = retirementDistributions,
        otherIncome = otherIncome,
        ordinaryTotal = ordinaryTotal,
        wageEarnedIncome = wages,
    )
}

// MARK: - Schedule C

data class ScheduleCNetByOwner(val taxpayer: Double, val spouse: Double)

data class ScheduleCResult(val netByOwner: ScheduleCNetByOwner, val totalNet: Double)

fun computeScheduleC(r: TaxReturn2025): ScheduleCResult {
    if (!r.income.flags.hasSelfEmployment) {
        return ScheduleCResult(ScheduleCNetByOwner(0.0, 0.0), 0.0)
    }
    var taxpayer = 0.0
    var spouse = 0.0
    for (c in r.income.scheduleC) {
        val expenses = c.expenses.values.fold(0.0) { a, b -> a + (if (b.isNaN()) 0.0 else b) }
        val net = c.grossReceipts - c.costOfGoodsSold - expenses - c.homeOfficeDeduction - c.vehicleExpense
        if (c.owner == IncomeOwner.Spouse) spouse += net else taxpayer += net
    }
    return ScheduleCResult(ScheduleCNetByOwner(taxpayer, spouse), taxpayer + spouse)
}

// MARK: - Schedule SE

data class SeTaxResult(val seTax: Double, val deduction: Double, val netEarnings: Double)

/**
 * SE tax per owner, coordinated with W-2 Social Security wages (the 12.4%
 * portion only applies up to the wage base across both).
 */
fun computeSelfEmploymentTax(
    netSeByOwner: Map<Owner, Double>,
    w2SsWagesByOwner: Map<Owner, Double>,
): SeTaxResult {
    var seTax = 0.0
    var netEarnings = 0.0
    for (owner in listOf(Owner.Taxpayer, Owner.Spouse)) {
        val net = netSeByOwner[owner] ?: 0.0
        if (net <= 0) continue
        val earnings = net * SE_TAX_2025.netEarningsFactor
        // Under $400 of net earnings, no SE tax is due.
        if (earnings < 400) continue
        netEarnings += earnings
        val ssWageRemaining = maxOf(
            0.0,
            SE_TAX_2025.socialSecurityWageBase - (w2SsWagesByOwner[owner] ?: 0.0),
        )
        val ssBase = min(earnings, ssWageRemaining)
        val ssPortion = ssBase * SE_TAX_2025.socialSecurityRate
        val medicarePortion = earnings * SE_TAX_2025.medicareRate
        seTax += ssPortion + medicarePortion
    }
    return SeTaxResult(
        seTax = seTax,
        deduction = seTax * SE_TAX_2025.deductibleFraction,
        netEarnings = netEarnings,
    )
}

// MARK: - Capital gains

data class CapitalGainsResult(
    val netShortTerm: Double = 0.0,
    val netLongTerm: Double = 0.0,
    val totalNet: Double = 0.0,
    val includedInIncome: Double = 0.0,
    val preferentialLTCG: Double = 0.0,
    val allowedLoss: Double = 0.0,
    val carryoverShort: Double = 0.0,
    val carryoverLong: Double = 0.0,
)

fun computeCapitalGains(r: TaxReturn2025): CapitalGainsResult {
    val f = r.income.flags
    if (!f.hasCapitalGains && !f.hasDividends) return CapitalGainsResult()
    val transactions = if (f.hasCapitalGains) r.income.f1099B else emptyList()
    var st = 0.0
    var lt = 0.0
    for (t in transactions) {
        val gain = t.proceeds - t.costBasis + (t.washSaleAdjustment ?: 0.0)
        if (t.longTerm) lt += gain else st += gain
    }
    if (f.hasDividends) {
        lt += sumBy(r.income.f1099Div) { it.box2aCapitalGainDistributions }
    }
    if (f.hasCapitalGains) {
        st -= r.income.capitalLossCarryoverShort
        lt -= r.income.capitalLossCarryoverLong
    }
    val netShortTerm = st
    val netLongTerm = lt
    val totalNet = netShortTerm + netLongTerm

    if (totalNet >= 0) {
        val preferentialLTCG = if (netLongTerm > 0) min(netLongTerm, totalNet) else 0.0
        return CapitalGainsResult(
            netShortTerm = netShortTerm,
            netLongTerm = netLongTerm,
            totalNet = totalNet,
            includedInIncome = totalNet,
            preferentialLTCG = preferentialLTCG,
        )
    }

    val limit = CAPITAL_LOSS_LIMIT_2025.getValue(r.filingStatus)
    val allowedLoss = min(limit, abs(totalNet))
    var carryoverShort = 0.0
    var carryoverLong = 0.0
    var remainingAllowed = allowedLoss
    if (netShortTerm < 0) {
        val stLoss = -netShortTerm
        val ltGain = maxOf(0.0, netLongTerm)
        carryoverShort = maxOf(0.0, stLoss - ltGain - allowedLoss)
        val usedAgainstSt = min(allowedLoss, maxOf(0.0, stLoss - ltGain))
        remainingAllowed = allowedLoss - usedAgainstSt
    }
    if (netLongTerm < 0) {
        val ltLoss = -netLongTerm
        val stGain = maxOf(0.0, netShortTerm)
        carryoverLong = maxOf(0.0, ltLoss - stGain - remainingAllowed)
    }
    return CapitalGainsResult(
        netShortTerm = netShortTerm,
        netLongTerm = netLongTerm,
        totalNet = totalNet,
        includedInIncome = -allowedLoss,
        preferentialLTCG = 0.0,
        allowedLoss = allowedLoss,
        carryoverShort = carryoverShort,
        carryoverLong = carryoverLong,
    )
}

// MARK: - Social Security taxability

data class TaxableSocialSecurityParams(
    val benefits: Double,
    val otherIncome: Double,
    val taxExemptInterest: Double,
    val adjustmentsForProvisional: Double,
    val status: FilingStatus,
    val livedApartFromSpouse: Boolean,
)

/** The Social Security Benefits Worksheet: 0% / 50% / 85% inclusion tiers. */
fun computeTaxableSocialSecurity(params: TaxableSocialSecurityParams): Double {
    val benefits = params.benefits
    if (benefits <= 0) return 0.0
    val half = SS_TAXABILITY_2025.firstTierRate * benefits
    val provisional = params.otherIncome + params.taxExemptInterest + half - params.adjustmentsForProvisional
    val bases = ssBaseAmounts(params.status, params.livedApartFromSpouse)
    val base1 = bases.base1
    val base2 = bases.base2
    if (provisional <= base1) return 0.0
    if (provisional <= base2) {
        return min(SS_TAXABILITY_2025.firstTierRate * (provisional - base1), half)
    }
    val tier1 = min(half, SS_TAXABILITY_2025.firstTierRate * (base2 - base1))
    return min(
        SS_TAXABILITY_2025.maxInclusionRate * (provisional - base2) + tier1,
        SS_TAXABILITY_2025.maxInclusionRate * benefits,
    )
}
