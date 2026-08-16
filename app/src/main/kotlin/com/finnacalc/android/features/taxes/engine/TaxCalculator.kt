/**
 * TaxCalculator.kt
 *
 * calculateFederalTax — the pure orchestrator.
 *
 * Port of iOS Features/Taxes/Engine/TaxCalculator.swift: the ordered IRS
 * computation pipeline assembled into a fully traced TaxCalculationResult. No
 * side effects, no I/O — deterministic output. Same order of operations, the
 * Social-Security ↔ IRA fixed-point loop, the credit ordering, the trace
 * lines, warnings, and audit flags.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

fun calculateFederalTax(r: TaxReturn2025): TaxCalculationResult {
    val warnings = mutableListOf<Warning>()
    val auditFlags = mutableListOf<AuditFlag>()
    val trace = mutableListOf<LineTrace>()
    fun line(id: String, label: String, formRef: String, amount: Double) {
        trace.add(LineTrace(id, label, formRef, amount))
    }

    // ---- 1. Schedule C → net SE profit per owner ----
    val schedC = computeScheduleC(r)

    // ---- 2. Schedule SE → SE tax + 50% deduction (coordinated with W-2 SS wages) ----
    val w2SsWagesByOwner = mapOf(
        Owner.Taxpayer to sumBy(r.income.w2.filter { it.owner == Owner.Taxpayer }) { it.box3SsWages },
        Owner.Spouse to sumBy(r.income.w2.filter { it.owner == Owner.Spouse }) { it.box3SsWages },
    )
    val netSeByOwner = mapOf(
        Owner.Taxpayer to schedC.netByOwner.taxpayer,
        Owner.Spouse to schedC.netByOwner.spouse,
    )
    val se = computeSelfEmploymentTax(netSeByOwner, w2SsWagesByOwner)
    val seTax = dollar(se.seTax)

    // ---- 3. Capital gains (Schedule D / 8949) ----
    val capGains = computeCapitalGains(r)

    // ---- 4. Schedule E (rental / royalty / passthrough) ----
    val scheduleENet = if (r.income.flags.hasRental) sumBy(r.income.scheduleE) { it.netIncome } else 0.0

    // ---- 5. Ordinary income components ----
    val gross = computeGrossIncome(r)

    // All income except Social Security (used by the SS worksheet and AGI).
    val otherIncomeNoSS =
        gross.ordinaryTotal + schedC.totalNet + capGains.includedInIncome + scheduleENet

    // ---- 6. Fixed (AGI-independent) above-the-line adjustments ----
    val educator = educatorDeduction(r)
    val hsa = hsaDeduction(r)
    val sep = min(
        maxOf(0.0, r.adjustments.sepSimpleContribution),
        nonNeg(schedC.totalNet - se.deduction),
    )
    val seHealth = seHealthDeduction(r, schedC.totalNet, se.deduction, sep)
    val fixedAdjustments = educator + hsa + se.deduction + seHealth + sep

    // ---- 7. Resolve Social Security taxability ↔ IRA deduction (fixed-point loop) ----
    // Both depend on AGI (which includes taxable SS), so iterate to a stable point.
    val ssBenefits = if (r.income.flags.hasSocialSecurity) {
        sumBy(r.income.f1099Ssa) { it.box5NetBenefits }
    } else 0.0
    val age50 = isAge50For2025(r.taxpayer.dateOfBirth)
    var taxableSS = 0.0
    var ira = 0.0
    repeat(6) {
        val newSS = computeTaxableSocialSecurity(
            TaxableSocialSecurityParams(
                benefits = ssBenefits,
                otherIncome = otherIncomeNoSS,
                taxExemptInterest = gross.taxExemptInterest,
                // SS provisional income subtracts Schedule 1 lines 11–20, 23,
                // 25 (incl. IRA, excl. student loan).
                adjustmentsForProvisional = fixedAdjustments + ira,
                status = r.filingStatus,
                livedApartFromSpouse = r.livedApartFromSpouse,
            )
        )
        // IRA MAGI = AGI computed without the IRA (and student loan) deduction.
        val iraMagi = otherIncomeNoSS + newSS - fixedAdjustments
        val newIra = iraDeduction(
            r.adjustments.traditionalIraContribution,
            iraMagi,
            r.filingStatus,
            r.adjustments.coveredByWorkplacePlan,
            r.adjustments.spouseCoveredByWorkplacePlan,
            age50,
        )
        val converged = abs(newSS - taxableSS) < 0.005 && abs(newIra - ira) < 0.005
        taxableSS = newSS
        ira = newIra
        if (converged) return@repeat
    }

    // ---- 8. Student loan interest (MAGI = AGI before this deduction) ----
    val studentLoanMagi = otherIncomeNoSS + taxableSS - (fixedAdjustments + ira)
    val studentLoan = studentLoanInterestDeduction(
        r.adjustments.studentLoanInterest, studentLoanMagi, r.filingStatus,
    )

    // ---- 9. Totals → AGI ----
    val totalAdjustments = dollar(fixedAdjustments + ira + studentLoan)
    val totalIncome = dollar(otherIncomeNoSS + taxableSS)
    line("totalIncome", "Total income", "Form 1040, line 9", totalIncome)
    line("adjustments", "Adjustments to income", "Schedule 1, line 25", totalAdjustments)
    val agi = dollar(totalIncome - totalAdjustments)
    line("agi", "Adjusted gross income", "Form 1040, line 11", agi)

    // ---- 10. MAGI variants (approximated as AGI; refined as rules land) ----
    val magi = MagiBreakdown(niit = agi, ira = agi, studentLoan = agi, ptc = agi, ctc = agi, aotc = agi)

    // ---- 11. Deduction: standard vs itemized ----
    val wageEarnedIncome = gross.wageEarnedIncome
    val seEarnedIncome = nonNeg(schedC.totalNet - se.deduction)
    val earnedIncome = wageEarnedIncome + seEarnedIncome
    val deduction = computeDeduction(r, agi, earnedIncome)
    val deductionAmount = dollar(deduction.amount)
    line(
        "deduction",
        if (deduction.used == DeductionUsed.Itemized) "Itemized deductions" else "Standard deduction",
        if (deduction.used == DeductionUsed.Itemized) "Schedule A" else "Form 1040, line 12",
        deductionAmount,
    )

    // ---- 11b. OBBBA deductions (2025-2028): allowed with standard OR itemized ----
    val seniorDeduction = dollar(computeSeniorDeduction(r, agi))
    val tipsDeduction = dollar(computeTipsDeduction(r, agi))
    val overtimeDeduction = dollar(computeOvertimeDeduction(r, agi))
    val carLoanDeduction = dollar(computeCarLoanInterestDeduction(r, agi))
    if (seniorDeduction > 0) line("seniorDeduction", "Additional deduction for seniors", "Schedule 1-A", seniorDeduction)
    if (tipsDeduction > 0) line("tipsDeduction", "Qualified tips deduction", "Schedule 1-A", tipsDeduction)
    if (overtimeDeduction > 0) line("overtimeDeduction", "Qualified overtime deduction", "Schedule 1-A", overtimeDeduction)
    if (carLoanDeduction > 0) line("carLoanDeduction", "Car loan interest deduction", "Schedule 1-A", carLoanDeduction)
    val obbbaDeductions = seniorDeduction + tipsDeduction + overtimeDeduction + carLoanDeduction

    // ---- 12. QBI deduction (§199A) ----
    val preferentialLTCG = capGains.preferentialLTCG
    val qualifiedDividends = gross.qualifiedDividends
    val netCapitalGainPreferential = qualifiedDividends + preferentialLTCG
    val taxableIncomeBeforeQbi = nonNeg(agi - deductionAmount - obbbaDeductions)
    val qbiIncome = nonNeg(schedC.totalNet - se.deduction - seHealth - sep)
    val isSSTB = r.income.scheduleC.any { it.isSSTB }
    val qbi = computeQbiDeduction(
        ComputeQbiDeductionParams(
            qbiIncome = qbiIncome,
            taxableIncomeBeforeQbi = taxableIncomeBeforeQbi,
            netCapitalGain = netCapitalGainPreferential,
            isSSTB = isSSTB,
            status = r.filingStatus,
        )
    )
    val qbiDeduction = dollar(qbi.deduction)
    if (qbiDeduction > 0) {
        line("qbi", "Qualified business income deduction", "Form 1040, line 13", qbiDeduction)
    }

    // ---- 13. Taxable income ----
    val taxableIncome = nonNeg(taxableIncomeBeforeQbi - qbiDeduction)
    line("taxableIncome", "Taxable income", "Form 1040, line 15", taxableIncome)

    // ---- 14. Regular tax: Qualified Div & Cap Gain Worksheet when preferential income exists ----
    val hasPreferential = qualifiedDividends > 0 || preferentialLTCG > 0
    val regularTax: Double
    var usedTaxTable = false
    var usedQualDivWorksheet = false
    var marginalRateDecimal: Double
    if (hasPreferential && taxableIncome > 0) {
        val qd = computeQualifiedDivCapGainTax(
            taxableIncome, qualifiedDividends, preferentialLTCG, r.filingStatus,
        )
        regularTax = qd.tax
        usedQualDivWorksheet = true
        marginalRateDecimal = computeRegularTax(taxableIncome, r.filingStatus).marginalRate
    } else {
        val reg = computeRegularTax(taxableIncome, r.filingStatus)
        regularTax = reg.tax
        usedTaxTable = reg.usedTaxTable
        marginalRateDecimal = reg.marginalRate
    }
    line("regularTax", "Tax", "Form 1040, line 16", regularTax)

    // ---- 15. AMT (Form 6251) ----
    val saltRaw = r.itemized.stateLocalIncomeOrSalesTax +
        r.itemized.realEstateTaxes + r.itemized.personalPropertyTaxes
    val saltCap = effectiveSaltCap(r.filingStatus, agi)
    val amtAddBacks = if (deduction.used == DeductionUsed.Itemized) min(saltRaw, saltCap) else deductionAmount
    val amtResult = computeAmt(
        ComputeAmtParams(
            taxableIncome = taxableIncome,
            addBacks = amtAddBacks,
            preferentialIncome = netCapitalGainPreferential,
            regularTax = regularTax,
            status = r.filingStatus,
        )
    )
    val amt = dollar(amtResult.amt)
    if (amt > 0) line("amt", "Alternative minimum tax", "Schedule 2, line 1", amt)
    val taxBeforeCredits = regularTax + amt

    // ---- 16. Nonrefundable credits (Schedule 3 first, then CTC per the 8812 limit) ----
    val nonrefundableCredits = mutableMapOf<String, Double>()
    var remainingTax = taxBeforeCredits
    fun applyCredit(key: String, label: String, formRef: String, amount: Double): Double {
        val used = min(dollar(amount), remainingTax)
        if (used > 0) {
            nonrefundableCredits[key] = used
            remainingTax -= used
            line(key, label, formRef, used)
        }
        return used
    }

    applyCredit("foreignTaxCredit", "Foreign tax credit", "Schedule 3, line 1", computeForeignTaxCredit(r))
    applyCredit("childDependentCare", "Child & dependent care credit", "Schedule 3, line 6f", computeCareCredit(r, agi))
    val education = computeEducationCredits(r, magi.aotc)
    applyCredit("education", "Education credits", "Schedule 3, line 3", education.nonrefundable)
    applyCredit("saversCredit", "Retirement savings (Saver's) credit", "Schedule 3, line 4", computeSaversCredit(r, agi))
    applyCredit("cleanEnergy", "Residential clean energy credit", "Schedule 3, line 5a", computeCleanEnergyCredit(r))
    applyCredit("evCredit", "Clean vehicle credit", "Schedule 3, line 6f", computeEvCredit(r, agi))

    // CTC / ODC limited to tax remaining after the Schedule 3 credits.
    val ctc = computeChildTaxCredit(r, magi.ctc, remainingTax, earnedIncome)
    applyCredit(
        "childTaxCredit",
        "Child Tax Credit / Credit for Other Dependents",
        "Form 1040, line 19",
        ctc.nonrefundable,
    )

    val totalNonrefundableCredits = nonrefundableCredits.values.sum()
    val taxAfterNonrefundable = nonNeg(taxBeforeCredits - totalNonrefundableCredits)

    // ---- 17. Other taxes (Schedule 2 Part II) ----
    val medicareWages = sumBy(r.income.w2) { it.box5MedicareWages }
    val additionalMedicareTax = dollar(
        computeAdditionalMedicareTax(medicareWages, se.netEarnings, r.filingStatus)
    )
    val netInvestmentIncome = gross.taxableInterest + gross.ordinaryDividends +
        nonNeg(capGains.includedInIncome) + nonNeg(scheduleENet)
    val niit = dollar(computeNiit(netInvestmentIncome, magi.niit, r.filingStatus))
    val ptc = computePremiumTaxCredit(r)
    // 10% additional tax on early retirement distributions (Form 5329).
    val earlyCodes = EARLY_WITHDRAWAL_PENALTY_2025.earlyNoExceptionCodes
    val earlyDistributions = if (r.income.flags.hasRetirementDistributions) {
        sumBy(r.income.f1099R.filter { it.box7DistributionCode in earlyCodes }) { it.box2aTaxableAmount }
    } else 0.0
    val earlyWithdrawalPenalty = dollar(earlyDistributions * EARLY_WITHDRAWAL_PENALTY_2025.rate)
    val otherTaxes = dollar(seTax + additionalMedicareTax + niit + ptc.repayment + earlyWithdrawalPenalty)
    if (seTax > 0) line("seTax", "Self-employment tax", "Schedule 2, line 4", seTax)
    if (additionalMedicareTax > 0) {
        line("addlMedicare", "Additional Medicare Tax", "Schedule 2, line 11", additionalMedicareTax)
    }
    if (niit > 0) line("niit", "Net investment income tax", "Schedule 2, line 12", niit)
    if (earlyWithdrawalPenalty > 0) {
        line("earlyWithdrawal", "Additional tax on early distributions", "Schedule 2, line 8", earlyWithdrawalPenalty)
    }
    if (ptc.repayment > 0) {
        line("aptcRepayment", "Excess advance premium tax credit repayment", "Schedule 2, line 2", ptc.repayment)
    }
    val totalTax = dollar(taxAfterNonrefundable + otherTaxes)
    line("totalTax", "Total tax", "Form 1040, line 24", totalTax)

    // ---- 18. Refundable credits ----
    val investmentIncome = gross.taxableInterest + gross.taxExemptInterest +
        gross.ordinaryDividends + nonNeg(capGains.includedInIncome)
    val eitcResult = computeEitc(
        ComputeEitcParams(
            r = r,
            earnedIncome = earnedIncome,
            agi = agi,
            investmentIncome = investmentIncome,
            taxpayerAge = ageAtEndOf2025(r.taxpayer.dateOfBirth),
        )
    )
    val eitc = eitcResult.credit
    val actc = dollar(ctc.additionalChildTaxCredit)
    val refundableCredits = mutableMapOf<String, Double>()
    if (eitc > 0) refundableCredits["earnedIncomeCredit"] = eitc
    if (actc > 0) refundableCredits["additionalChildTaxCredit"] = actc
    if (education.refundable > 0) refundableCredits["refundableAotc"] = education.refundable
    if (ptc.netRefundable > 0) refundableCredits["premiumTaxCredit"] = ptc.netRefundable
    val totalRefundableCredits = eitc + actc + education.refundable + ptc.netRefundable
    if (eitc > 0) line("eitc", "Earned income credit", "Form 1040, line 27", eitc)
    if (actc > 0) line("actc", "Additional Child Tax Credit", "Form 1040, line 28", actc)
    if (education.refundable > 0) {
        line("refundableAotc", "Refundable American Opportunity credit", "Form 1040, line 29", education.refundable)
    }

    // ---- 19. Payments + refund/owed ----
    val pay = computeWithholdingAndPayments(r)
    val totalPayments = dollar(pay.total + totalRefundableCredits)
    line("totalPayments", "Total payments", "Form 1040, line 33", totalPayments)
    val refundOrOwed = dollar(totalPayments - totalTax)
    val owes = refundOrOwed < 0
    line(
        if (owes) "amountOwed" else "refund",
        if (owes) "Amount you owe" else "Refund",
        if (owes) "Form 1040, line 37" else "Form 1040, line 34",
        abs(refundOrOwed),
    )

    // ---- 20. Rates ----
    val marginalRatePct = marginalRateDecimal * 100
    val effectiveRate = if (totalIncome > 0) (totalTax / totalIncome) * 100 else 0.0

    // ---- 20b. State income tax ----
    val stateWithholding = sumBy(r.income.w2) { it.box17StateWithholding } + r.residency.stateWithholding
    val stateResult = computeStateTax(
        StateInput(
            code = r.residency.state,
            federalAgi = agi,
            taxableSocialSecurity = taxableSS,
            retirementDistributions = gross.retirementDistributions,
            filingStatus = r.filingStatus,
            dependents = r.dependents.size.toDouble(),
            stateWithholding = stateWithholding,
            age65 = (ageAtEndOf2025(r.taxpayer.dateOfBirth) ?: 0.0) >= 65,
        )
    )
    if (stateResult != null && stateResult.hasIncomeTax && stateResult.supported) {
        line("stateTax", "${stateResult.name} state income tax", "State return", stateResult.tax)
    }

    // ---- 21. Warnings for not-yet-modeled refinements ----
    if (qbi.wageLimitMayApply) {
        warnings.add(
            Warning(
                "QBI_WAGE_LIMIT",
                "Your taxable income is above the QBI threshold, where the W-2 wage / property (UBIA) " +
                    "limit can reduce the 20% deduction. We don't track business W-2 wages, so your QBI " +
                    "deduction may be overstated.",
            )
        )
    }
    if (r.credits.hasMarketplaceCoverage) {
        warnings.add(
            Warning(
                "PTC_SIMPLIFIED",
                "Marketplace (ACA) premium tax credit is reconciled simply here; the income-based cap on " +
                    "repaying excess advance payments isn't modeled.",
            )
        )
    }
    eitcResult.disqualReason?.let { reason ->
        warnings.add(Warning("EITC_INELIGIBLE", "Earned Income Credit not applied: $reason"))
    }

    // ---- 22. Audit / data-quality flags ----
    if (r.income.flags.hasW2 && pay.withholding == 0.0 && gross.wages > 0) {
        auditFlags.add(
            AuditFlag(
                AuditSeverity.Warn,
                "You have W-2 wages but no federal tax was withheld. Double-check box 2 of your W-2(s).",
                "totalPayments",
            )
        )
    }
    if (owes && totalIncome > 0 && abs(refundOrOwed) > 0.1 * totalIncome) {
        auditFlags.add(
            AuditFlag(
                AuditSeverity.Info,
                "Your balance due is large relative to your income. Consider adjusting withholding or " +
                    "making estimated payments next year.",
                "amountOwed",
            )
        )
    }
    if (schedC.totalNet > 0 && se.deduction > 0) {
        auditFlags.add(
            AuditFlag(
                AuditSeverity.Info,
                "Self-employment tax of ${jsRound(seTax)} applies; half of it (${jsRound(se.deduction)}) " +
                    "is deducted above the line.",
                "seTax",
            )
        )
    }
    // Underpayment (Form 2210) safe-harbor check — flag only, no penalty added.
    if (owes && abs(refundOrOwed) >= 1_000) {
        val safeHarborCurrent = 0.9 * totalTax
        val priorYearTax = r.payments.priorYearTax
        val safeHarborPrior = if (priorYearTax != null) {
            (if ((r.payments.priorYearAgi ?: 0.0) > 150_000) 1.1 else 1.0) * priorYearTax
        } else {
            Double.POSITIVE_INFINITY
        }
        val requiredAnnualPayment = min(safeHarborCurrent, safeHarborPrior)
        if (pay.withholding < requiredAnnualPayment) {
            auditFlags.add(
                AuditFlag(
                    AuditSeverity.Warn,
                    "You may owe an underpayment penalty (Form 2210): too little was paid in during the " +
                        "year. Consider increasing withholding or making estimated payments.",
                    "amountOwed",
                )
            )
        }
    }

    return TaxCalculationResult(
        filingStatus = r.filingStatus,
        totalIncome = totalIncome,
        totalAdjustments = totalAdjustments,
        agi = agi,
        magi = magi,
        standardDeduction = dollar(deduction.standard),
        itemizedDeduction = dollar(deduction.itemized),
        deductionUsed = deduction.used,
        deductionAmount = deductionAmount,
        itemizedSavings = dollar(deduction.itemizedSavings),
        qbiDeduction = qbiDeduction,
        taxableIncomeBeforeQbi = taxableIncomeBeforeQbi,
        taxableIncome = taxableIncome,
        regularTax = regularTax,
        usedTaxTable = usedTaxTable,
        usedQualDivWorksheet = usedQualDivWorksheet,
        amt = amt,
        additionalMedicareTax = additionalMedicareTax,
        niit = niit,
        seTax = seTax,
        nonrefundableCredits = nonrefundableCredits,
        totalNonrefundableCredits = totalNonrefundableCredits,
        refundableCredits = refundableCredits,
        totalRefundableCredits = totalRefundableCredits,
        otherTaxes = otherTaxes,
        totalTax = totalTax,
        totalPayments = totalPayments,
        refundOrOwed = refundOrOwed,
        owes = owes,
        underpaymentPenalty = 0.0,
        marginalRate = marginalRatePct,
        effectiveRate = effectiveRate,
        capitalLossCarryover = CapitalLossCarryover(
            shortTerm = dollar(capGains.carryoverShort),
            longTerm = dollar(capGains.carryoverLong),
        ),
        trace = trace,
        warnings = warnings,
        auditFlags = auditFlags,
        state = stateResult,
    )
}

/** JS `Math.round` — half up — rendered as a whole number for the audit copy. */
private fun jsRound(x: Double): String = floor(x + 0.5).toInt().toString()
