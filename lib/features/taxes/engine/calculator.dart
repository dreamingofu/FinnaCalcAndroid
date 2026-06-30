/// calculateFederalTax — the pure orchestrator.
///
/// Runs the ordered IRS computation pipeline and assembles a fully traced
/// TaxCalculationResult. No side effects, no I/O — deterministic output.
///
/// PHASE 2 SCOPE: wages + interest + dividends (ordinary & qualified), capital
/// gains (Schedule D, \$3k loss limit + carryover), Schedule C + self-employment
/// tax (Schedule SE) + the 50% deduction, Schedule E, Social Security taxability,
/// the full set of above-the-line adjustments (educator, HSA, IRA w/ phaseout,
/// SE-health, SEP, student loan), standard-vs-itemized (Schedule A), the
/// Qualified Dividends & Capital Gain Tax Worksheet, and the Child Tax Credit /
/// Additional CTC.
///
/// Still pending (Phase 3): QBI deduction, EITC and the other credits, AMT, NIIT,
/// and the Additional Medicare Tax — warnings flag returns those would affect.
library;

import 'dart:math' as math;

import 'constants/filing_thresholds_2024.dart';
import 'pipeline/additional_medicare.dart';
import 'pipeline/adjustments_calc.dart';
import 'pipeline/amt.dart';
import 'pipeline/capital_gains.dart';
import 'pipeline/care_credit.dart';
import 'pipeline/child_tax_credit.dart';
import 'pipeline/deduction_compare.dart';
import 'pipeline/education_credits.dart';
import 'pipeline/eitc.dart';
import 'pipeline/gross_income.dart';
import 'pipeline/niit.dart';
import 'pipeline/other_credits.dart';
import 'pipeline/payments_calc.dart';
import 'pipeline/premium_tax_credit.dart';
import 'pipeline/qbi.dart';
import 'pipeline/qualified_div_cap_gain.dart';
import 'pipeline/regular_tax.dart';
import 'pipeline/schedule_c.dart';
import 'pipeline/schedule_se.dart';
import 'pipeline/social_security.dart';
import 'round.dart';
import 'state/state_tax.dart';
import 'state/state_types.dart';
import 'types/filing.dart';
import 'types/result.dart';
import 'types/tax_return.dart';

/// Age at the end of 2024 (for the childless-EITC age test). Null if no DOB.
double? ageAtEndOf2024(String dob) {
  if (dob.isEmpty) return null;
  final d = DateTime.tryParse(dob);
  if (d == null) return null;
  // DateTime.tryParse treats a bare date as LOCAL; JS treats it as UTC. Rebuild
  // in UTC from the calendar fields so the year matches the TS source.
  final utc = DateTime.utc(
    d.year,
    d.month,
    d.day,
    d.hour,
    d.minute,
    d.second,
    d.millisecond,
  );
  return (2024 - utc.year).toDouble();
}

TaxCalculationResult calculateFederalTax(TaxReturn2024 r) {
  final warnings = <Warning>[];
  final auditFlags = <AuditFlag>[];
  final trace = <LineTrace>[];
  void line(String id, String label, String formRef, double amount) {
    trace.add(LineTrace(id: id, label: label, formRef: formRef, amount: amount));
  }

  // ---- 1. Schedule C → net SE profit per owner ----
  final schedC = computeScheduleC(r);

  // ---- 2. Schedule SE → SE tax + 50% deduction (coordinated with W-2 SS wages) ----
  final w2SsWagesByOwner = (
    taxpayer: sumBy(
      r.income.w2.where((w) => w.owner == 'taxpayer'),
      (w) => w.box3SsWages,
    ),
    spouse: sumBy(
      r.income.w2.where((w) => w.owner == 'spouse'),
      (w) => w.box3SsWages,
    ),
  );
  final se = computeSelfEmploymentTax(schedC.netByOwner, w2SsWagesByOwner);
  final seTax = dollar(se.seTax);

  // ---- 3. Capital gains (Schedule D / 8949) ----
  final capGains = computeCapitalGains(r);

  // ---- 4. Schedule E (rental / royalty / passthrough) ----
  final scheduleENet = r.income.flags.hasRental
      ? sumBy(r.income.scheduleE, (e) => e.netIncome)
      : 0.0;

  // ---- 5. Ordinary income components ----
  final gross = computeGrossIncome(r);

  // All income except Social Security (used by the SS worksheet and AGI).
  final otherIncomeNoSS = gross.ordinaryTotal +
      schedC.totalNet +
      capGains.includedInIncome +
      scheduleENet;

  // ---- 6. Fixed (AGI-independent) above-the-line adjustments ----
  final educator = educatorDeduction(r);
  final hsa = hsaDeduction(r);
  final sep = math.min(
    math.max(0.0, r.adjustments.sepSimpleContribution),
    nonNeg(schedC.totalNet - se.deduction),
  );
  final seHealth = seHealthDeduction(r, schedC.totalNet, se.deduction, sep);
  final fixedAdjustments = educator + hsa + se.deduction + seHealth + sep;

  // ---- 7. Resolve Social Security taxability ↔ IRA deduction (fixed-point loop) ----
  // Both depend on AGI (which includes taxable SS), so iterate to a stable point.
  final ssBenefits = r.income.flags.hasSocialSecurity
      ? sumBy(r.income.f1099Ssa, (s) => s.box5NetBenefits)
      : 0.0;
  final age50 = isAge50For2024(r.taxpayer.dateOfBirth);
  var taxableSS = 0.0;
  var ira = 0.0;
  for (var i = 0; i < 6; i++) {
    // SS provisional income subtracts Schedule 1 lines 11–20, 23, 25 (incl. IRA,
    // excl. student loan), which here is fixedAdjustments + the current IRA estimate.
    final newSS = computeTaxableSocialSecurity(
      benefits: ssBenefits,
      otherIncome: otherIncomeNoSS,
      taxExemptInterest: gross.taxExemptInterest,
      adjustmentsForProvisional: fixedAdjustments + ira,
      status: r.filingStatus,
      livedApartFromSpouse: r.livedApartFromSpouse,
    );
    // IRA MAGI = AGI computed without the IRA (and student loan) deduction.
    final iraMagi = otherIncomeNoSS + newSS - fixedAdjustments;
    final newIra = iraDeduction(
      r.adjustments.traditionalIraContribution,
      iraMagi,
      r.filingStatus,
      r.adjustments.coveredByWorkplacePlan,
      r.adjustments.spouseCoveredByWorkplacePlan,
      age50,
    );
    final converged =
        (newSS - taxableSS).abs() < 0.005 && (newIra - ira).abs() < 0.005;
    taxableSS = newSS;
    ira = newIra;
    if (converged) break;
  }

  // ---- 8. Student loan interest (MAGI = AGI before this deduction) ----
  final studentLoanMagi =
      otherIncomeNoSS + taxableSS - (fixedAdjustments + ira);
  final studentLoan = studentLoanInterestDeduction(
    r.adjustments.studentLoanInterest,
    studentLoanMagi,
    r.filingStatus,
  );

  // ---- 9. Totals → AGI ----
  final totalAdjustments = dollar(fixedAdjustments + ira + studentLoan);
  final totalIncome = dollar(otherIncomeNoSS + taxableSS);
  line('totalIncome', 'Total income', 'Form 1040, line 9', totalIncome);
  line('adjustments', 'Adjustments to income', 'Schedule 1, line 25',
      totalAdjustments);
  final agi = dollar(totalIncome - totalAdjustments);
  line('agi', 'Adjusted gross income', 'Form 1040, line 11', agi);

  // ---- 10. MAGI variants (Phase 2: approximated as AGI; refined as rules land) ----
  final magi = MagiBreakdown(
    niit: agi,
    ira: agi,
    studentLoan: agi,
    ptc: agi,
    ctc: agi,
    aotc: agi,
  );

  // ---- 11. Deduction: standard vs itemized ----
  final wageEarnedIncome = gross.wageEarnedIncome;
  final seEarnedIncome = nonNeg(schedC.totalNet - se.deduction);
  final earnedIncome = wageEarnedIncome + seEarnedIncome;
  final deduction = computeDeduction(r, agi, earnedIncome);
  final deductionAmount = dollar(deduction.amount);
  line(
    'deduction',
    deduction.used == 'itemized' ? 'Itemized deductions' : 'Standard deduction',
    deduction.used == 'itemized' ? 'Schedule A' : 'Form 1040, line 12',
    deductionAmount,
  );

  // ---- 12. QBI deduction (§199A) ----
  final preferentialLTCG = capGains.preferentialLTCG;
  final qualifiedDividends = gross.qualifiedDividends;
  final netCapitalGainPreferential = qualifiedDividends + preferentialLTCG;
  final taxableIncomeBeforeQbi = nonNeg(agi - deductionAmount);
  final qbiIncome = nonNeg(schedC.totalNet - se.deduction - seHealth - sep);
  final isSSTB = r.income.scheduleC.any((c) => c.isSSTB);
  final qbi = computeQbiDeduction(
    qbiIncome: qbiIncome,
    taxableIncomeBeforeQbi: taxableIncomeBeforeQbi,
    netCapitalGain: netCapitalGainPreferential,
    isSSTB: isSSTB,
    status: r.filingStatus,
  );
  final qbiDeduction = dollar(qbi.deduction);
  if (qbiDeduction > 0) {
    line('qbi', 'Qualified business income deduction', 'Form 1040, line 13',
        qbiDeduction);
  }

  // ---- 13. Taxable income ----
  final taxableIncome = nonNeg(taxableIncomeBeforeQbi - qbiDeduction);
  line('taxableIncome', 'Taxable income', 'Form 1040, line 15', taxableIncome);

  // ---- 14. Regular tax: Qualified Div & Cap Gain Worksheet when preferential income exists ----
  final hasPreferential = qualifiedDividends > 0 || preferentialLTCG > 0;
  double regularTax;
  var usedTaxTable = false;
  var usedQualDivWorksheet = false;
  var marginalRate = 0.0;
  if (hasPreferential && taxableIncome > 0) {
    final qd = computeQualifiedDivCapGainTax(
      taxableIncome,
      qualifiedDividends,
      preferentialLTCG,
      r.filingStatus,
    );
    regularTax = qd.tax;
    usedQualDivWorksheet = true;
    marginalRate = computeRegularTax(taxableIncome, r.filingStatus).marginalRate;
  } else {
    final reg = computeRegularTax(taxableIncome, r.filingStatus);
    regularTax = reg.tax;
    usedTaxTable = reg.usedTaxTable;
    marginalRate = reg.marginalRate;
  }
  line('regularTax', 'Tax', 'Form 1040, line 16', regularTax);

  // ---- 15. AMT (Form 6251) ----
  final saltRaw = r.itemized.stateLocalIncomeOrSalesTax +
      r.itemized.realEstateTaxes +
      r.itemized.personalPropertyTaxes;
  final saltCap =
      r.filingStatus == FilingStatus.mfs ? SaltCap2024.mfs : SaltCap2024.standard;
  final amtAddBacks = deduction.used == 'itemized'
      ? math.min(saltRaw, saltCap)
      : deductionAmount;
  final amtResult = computeAmt(
    taxableIncome: taxableIncome,
    addBacks: amtAddBacks,
    preferentialIncome: netCapitalGainPreferential,
    regularTax: regularTax,
    status: r.filingStatus,
  );
  final amt = dollar(amtResult.amt);
  if (amt > 0) line('amt', 'Alternative minimum tax', 'Schedule 2, line 1', amt);
  final taxBeforeCredits = regularTax + amt;

  // ---- 16. Nonrefundable credits (Schedule 3 first, then CTC per the 8812 limit) ----
  final nonrefundableCredits = <String, double>{};
  var remainingTax = taxBeforeCredits;
  double applyCredit(String key, String label, String formRef, double amount) {
    final used = math.min(dollar(amount), remainingTax);
    if (used > 0) {
      nonrefundableCredits[key] = used;
      remainingTax -= used;
      line(key, label, formRef, used);
    }
    return used;
  }

  applyCredit('foreignTaxCredit', 'Foreign tax credit', 'Schedule 3, line 1',
      computeForeignTaxCredit(r));
  applyCredit('childDependentCare', 'Child & dependent care credit',
      'Schedule 3, line 6f', computeCareCredit(r, agi));
  final education = computeEducationCredits(r, magi.aotc);
  applyCredit('education', 'Education credits', 'Schedule 3, line 3',
      education.nonrefundable);
  applyCredit('saversCredit', "Retirement savings (Saver's) credit",
      'Schedule 3, line 4', computeSaversCredit(r, agi));
  applyCredit('cleanEnergy', 'Residential clean energy credit',
      'Schedule 3, line 5a', computeCleanEnergyCredit(r));
  applyCredit('evCredit', 'Clean vehicle credit', 'Schedule 3, line 6f',
      computeEvCredit(r, agi));

  // CTC / ODC limited to tax remaining after the Schedule 3 credits (8812 limit worksheet).
  final ctc = computeChildTaxCredit(r, magi.ctc, remainingTax, earnedIncome);
  applyCredit(
    'childTaxCredit',
    'Child Tax Credit / Credit for Other Dependents',
    'Form 1040, line 19',
    ctc.nonrefundable,
  );

  final totalNonrefundableCredits =
      nonrefundableCredits.values.fold<double>(0, (a, b) => a + b);
  final taxAfterNonrefundable =
      nonNeg(taxBeforeCredits - totalNonrefundableCredits);

  // ---- 17. Other taxes (Schedule 2 Part II) ----
  final medicareWages = sumBy(r.income.w2, (w) => w.box5MedicareWages);
  final additionalMedicareTax = dollar(
    computeAdditionalMedicareTax(medicareWages, se.netEarnings, r.filingStatus),
  );
  final netInvestmentIncome = gross.taxableInterest +
      gross.ordinaryDividends +
      nonNeg(capGains.includedInIncome) +
      nonNeg(scheduleENet);
  final niit = dollar(computeNiit(netInvestmentIncome, magi.niit, r.filingStatus));
  final ptc = computePremiumTaxCredit(r);
  // 10% additional tax on early retirement distributions (Form 5329).
  final earlyCodes = EarlyWithdrawalPenalty2024.earlyNoExceptionCodes;
  final earlyDistributions = r.income.flags.hasRetirementDistributions
      ? sumBy(
          r.income.f1099R
              .where((x) => earlyCodes.contains(x.box7DistributionCode)),
          (x) => x.box2aTaxableAmount,
        )
      : 0.0;
  final earlyWithdrawalPenalty =
      dollar(earlyDistributions * EarlyWithdrawalPenalty2024.rate);
  final otherTaxes = dollar(
    seTax + additionalMedicareTax + niit + ptc.repayment + earlyWithdrawalPenalty,
  );
  if (seTax > 0) line('seTax', 'Self-employment tax', 'Schedule 2, line 4', seTax);
  if (additionalMedicareTax > 0) {
    line('addlMedicare', 'Additional Medicare Tax', 'Schedule 2, line 11',
        additionalMedicareTax);
  }
  if (niit > 0) {
    line('niit', 'Net investment income tax', 'Schedule 2, line 12', niit);
  }
  if (earlyWithdrawalPenalty > 0) {
    line('earlyWithdrawal', 'Additional tax on early distributions',
        'Schedule 2, line 8', earlyWithdrawalPenalty);
  }
  if (ptc.repayment > 0) {
    line('aptcRepayment', 'Excess advance premium tax credit repayment',
        'Schedule 2, line 2', ptc.repayment);
  }
  final totalTax = dollar(taxAfterNonrefundable + otherTaxes);
  line('totalTax', 'Total tax', 'Form 1040, line 24', totalTax);

  // ---- 18. Refundable credits ----
  final investmentIncome = gross.taxableInterest +
      gross.taxExemptInterest +
      gross.ordinaryDividends +
      nonNeg(capGains.includedInIncome);
  final eitcResult = computeEitc(
    r: r,
    earnedIncome: earnedIncome,
    agi: agi,
    investmentIncome: investmentIncome,
    taxpayerAge: ageAtEndOf2024(r.taxpayer.dateOfBirth),
  );
  final eitc = eitcResult.credit;
  final actc = dollar(ctc.additionalChildTaxCredit);
  final refundableCredits = <String, double>{};
  if (eitc > 0) refundableCredits['earnedIncomeCredit'] = eitc;
  if (actc > 0) refundableCredits['additionalChildTaxCredit'] = actc;
  if (education.refundable > 0) {
    refundableCredits['refundableAotc'] = education.refundable;
  }
  if (ptc.netRefundable > 0) {
    refundableCredits['premiumTaxCredit'] = ptc.netRefundable;
  }
  final totalRefundableCredits =
      eitc + actc + education.refundable + ptc.netRefundable;
  if (eitc > 0) {
    line('eitc', 'Earned income credit', 'Form 1040, line 27', eitc);
  }
  if (actc > 0) {
    line('actc', 'Additional Child Tax Credit', 'Form 1040, line 28', actc);
  }
  if (education.refundable > 0) {
    line('refundableAotc', 'Refundable American Opportunity credit',
        'Form 1040, line 29', education.refundable);
  }

  // ---- 19. Payments + refund/owed ----
  final pay = computeWithholdingAndPayments(r);
  final totalPayments = dollar(pay.total + totalRefundableCredits);
  line('totalPayments', 'Total payments', 'Form 1040, line 33', totalPayments);
  final refundOrOwed = dollar(totalPayments - totalTax);
  final owes = refundOrOwed < 0;
  line(
    owes ? 'amountOwed' : 'refund',
    owes ? 'Amount you owe' : 'Refund',
    owes ? 'Form 1040, line 37' : 'Form 1040, line 34',
    refundOrOwed.abs(),
  );

  // ---- 20. Rates ----
  final marginalRatePct = marginalRate * 100;
  final effectiveRate = totalIncome > 0 ? (totalTax / totalIncome) * 100 : 0.0;

  // ---- 20b. State income tax ----
  final stateWithholding =
      sumBy(r.income.w2, (w) => w.box17StateWithholding) +
          (r.residency.stateWithholding);
  final stateResult = computeStateTax(StateInput(
    code: r.residency.state,
    federalAgi: agi,
    taxableSocialSecurity: taxableSS,
    retirementDistributions: gross.retirementDistributions,
    filingStatus: r.filingStatus,
    dependents: r.dependents.length.toDouble(),
    stateWithholding: stateWithholding,
    age65: (ageAtEndOf2024(r.taxpayer.dateOfBirth) ?? 0) >= 65,
  ));
  if (stateResult != null &&
      stateResult.hasIncomeTax &&
      stateResult.supported) {
    line('stateTax', '${stateResult.name} state income tax', 'State return',
        stateResult.tax);
  }

  // ---- 21. Warnings for not-yet-modeled refinements ----
  if (qbi.wageLimitMayApply) {
    warnings.add(Warning(
      code: 'QBI_WAGE_LIMIT',
      message:
          "Your taxable income is above the QBI threshold, where the W-2 wage / property (UBIA) limit can reduce the 20% deduction. We don't track business W-2 wages, so your QBI deduction may be overstated.",
    ));
  }
  if (r.credits.hasMarketplaceCoverage) {
    warnings.add(Warning(
      code: 'PTC_SIMPLIFIED',
      message:
          "Marketplace (ACA) premium tax credit is reconciled simply here; the income-based cap on repaying excess advance payments isn't modeled.",
    ));
  }
  if (eitcResult.disqualReason != null) {
    warnings.add(Warning(
      code: 'EITC_INELIGIBLE',
      message: 'Earned Income Credit not applied: ${eitcResult.disqualReason}',
    ));
  }

  // ---- 22. Audit / data-quality flags ----
  if (r.income.flags.hasW2 && pay.withholding == 0 && gross.wages > 0) {
    auditFlags.add(AuditFlag(
      severity: 'warn',
      message:
          'You have W-2 wages but no federal tax was withheld. Double-check box 2 of your W-2(s).',
      relatedLine: 'totalPayments',
    ));
  }
  if (owes && totalIncome > 0 && refundOrOwed.abs() > 0.1 * totalIncome) {
    auditFlags.add(AuditFlag(
      severity: 'info',
      message:
          'Your balance due is large relative to your income — consider adjusting withholding or making estimated payments next year.',
      relatedLine: 'amountOwed',
    ));
  }
  if (schedC.totalNet > 0 && se.deduction > 0) {
    auditFlags.add(AuditFlag(
      severity: 'info',
      message:
          'Self-employment tax of ${seTax.round()} applies; half of it (${se.deduction.round()}) is deducted above the line.',
      relatedLine: 'seTax',
    ));
  }
  // Underpayment (Form 2210) safe-harbor check — flag only (no penalty added to the bill).
  if (owes && refundOrOwed.abs() >= 1000) {
    final safeHarborCurrent = 0.9 * totalTax;
    final priorYearTax = r.payments.priorYearTax;
    final safeHarborPrior = priorYearTax != null
        ? ((r.payments.priorYearAgi ?? 0) > 150000 ? 1.1 : 1.0) * priorYearTax
        : double.infinity;
    final requiredAnnualPayment = math.min(safeHarborCurrent, safeHarborPrior);
    if (pay.withholding < requiredAnnualPayment) {
      auditFlags.add(AuditFlag(
        severity: 'warn',
        message:
            'You may owe an underpayment penalty (Form 2210) — too little was paid in during the year. Consider increasing withholding or making estimated payments.',
        relatedLine: 'amountOwed',
      ));
    }
  }

  return TaxCalculationResult(
    filingStatus: r.filingStatus,
    totalIncome: totalIncome,
    totalAdjustments: totalAdjustments,
    agi: agi,
    magi: magi,
    standardDeduction: dollar(deduction.standard),
    itemizedDeduction: dollar(deduction.itemized),
    deductionUsed: deduction.used,
    deductionAmount: deductionAmount,
    itemizedSavings: dollar(deduction.itemizedSavings),
    qbiDeduction: qbiDeduction,
    taxableIncomeBeforeQbi: taxableIncomeBeforeQbi,
    taxableIncome: taxableIncome,
    regularTax: regularTax,
    usedTaxTable: usedTaxTable,
    usedQualDivWorksheet: usedQualDivWorksheet,
    amt: amt,
    additionalMedicareTax: additionalMedicareTax,
    niit: niit,
    seTax: seTax,
    nonrefundableCredits: nonrefundableCredits,
    totalNonrefundableCredits: totalNonrefundableCredits,
    refundableCredits: refundableCredits,
    totalRefundableCredits: totalRefundableCredits,
    otherTaxes: otherTaxes,
    totalTax: totalTax,
    totalPayments: totalPayments,
    refundOrOwed: refundOrOwed,
    owes: owes,
    underpaymentPenalty: 0,
    marginalRate: marginalRatePct,
    effectiveRate: effectiveRate,
    capitalLossCarryover: CapitalLossCarryover(
      shortTerm: dollar(capGains.carryoverShort),
      longTerm: dollar(capGains.carryoverLong),
    ),
    trace: trace,
    warnings: warnings,
    auditFlags: auditFlags,
    state: stateResult,
  );
}
