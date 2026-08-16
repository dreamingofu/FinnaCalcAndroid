/**
 * TaxModels.kt
 *
 * Port of iOS Features/Taxes/Engine/TaxModels.swift — the tax return input
 * shape and the calculation result, mirroring the web engine's types.
 *
 * Every monetary field is a Double carrying cents; only the 1040 line
 * boundaries round (see TaxRound.dollar).
 */

package com.finnacalc.android.features.taxes.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FilingStatus(val raw: String) {
    @SerialName("single") Single("single"),
    @SerialName("mfj") Mfj("mfj"),
    @SerialName("mfs") Mfs("mfs"),
    @SerialName("hoh") Hoh("hoh"),
    @SerialName("qss") Qss("qss");

    val title: String
        get() = when (this) {
            Single -> "Single"
            Mfj -> "Married filing jointly"
            Mfs -> "Married filing separately"
            Hoh -> "Head of household"
            Qss -> "Qualifying surviving spouse"
        }
}

@Serializable
enum class StateCode {
    AL, AK, AZ, AR, CA, CO, CT, DE, DC, FL,
    GA, HI, ID, IL, IN, IA, KS, KY, LA, ME,
    MD, MA, MI, MN, MS, MO, MT, NE, NV, NH,
    NJ, NM, NY, NC, ND, OH, OK, OR, PA, RI,
    SC, SD, TN, TX, UT, VT, VA, WA, WV, WI, WY,
}

@Serializable
data class TaxpayerInfo(
    val firstName: String = "",
    val lastName: String = "",
    val ssn: String = "",
    /** ISO yyyy-MM-dd. */
    val dateOfBirth: String = "",
    val occupation: String = "",
    val blind: Boolean = false,
    val claimedAsDependentByAnother: Boolean = false,
)

@Serializable
data class Address(
    val line1: String = "",
    val line2: String? = null,
    val city: String = "",
    val state: StateCode? = null,
    val zip: String = "",
)

@Serializable
enum class DependentRelationshipType {
    @SerialName("child") Child,
    @SerialName("relative") Relative,
}

@Serializable
data class Dependent(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val ssn: String = "",
    val dateOfBirth: String = "",
    val relationshipType: DependentRelationshipType = DependentRelationshipType.Child,
    /** e.g. "son", "daughter", "parent". */
    val relationship: String = "",
    val monthsLivedWithTaxpayer: Double = 12.0,
    val taxpayerProvidedOverHalfSupport: Boolean = true,
    val qualifiesForCTC: Boolean = false,
    val qualifiesForODC: Boolean = false,
    val qualifiesForEITC: Boolean = false,
    val qualifiesForCareCredit: Boolean = false,
)

@Serializable
enum class IncomeOwner {
    @SerialName("taxpayer") Taxpayer,
    @SerialName("spouse") Spouse,
}

/** Alias kept for the calculator's per-owner maps. */
typealias Owner = IncomeOwner

@Serializable
data class W2Box12Entry(val code: String, val amount: Double)

@Serializable
data class W2(
    val id: String,
    val owner: IncomeOwner = IncomeOwner.Taxpayer,
    val employerName: String = "",
    val box1Wages: Double = 0.0,
    val box2FederalWithholding: Double = 0.0,
    val box3SsWages: Double = 0.0,
    val box4SsWithheld: Double = 0.0,
    val box5MedicareWages: Double = 0.0,
    val box6MedicareWithheld: Double = 0.0,
    val box12: List<W2Box12Entry> = emptyList(),
    val statutoryEmployee: Boolean = false,
    val box17StateWithholding: Double = 0.0,
)

@Serializable
data class Form1099Int(
    val id: String,
    val payer: String = "",
    val box1Interest: Double = 0.0,
    val box3UsTreasuryInterest: Double = 0.0,
    val box8TaxExemptInterest: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
)

@Serializable
data class Form1099Div(
    val id: String,
    val payer: String = "",
    val box1aOrdinaryDividends: Double = 0.0,
    val box1bQualifiedDividends: Double = 0.0,
    val box2aCapitalGainDistributions: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
)

@Serializable
data class CapitalTransaction(
    val id: String,
    val description: String = "",
    val proceeds: Double = 0.0,
    val costBasis: Double = 0.0,
    val longTerm: Boolean = false,
    val washSaleAdjustment: Double? = null,
)

@Serializable
data class Form1099R(
    val id: String,
    val payer: String = "",
    val box1GrossDistribution: Double = 0.0,
    val box2aTaxableAmount: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
    val box7DistributionCode: String = "",
    val iraSepSimple: Boolean = false,
)

@Serializable
data class Form1099Ssa(
    val id: String,
    val owner: IncomeOwner = IncomeOwner.Taxpayer,
    val box5NetBenefits: Double = 0.0,
    val federalWithholding: Double = 0.0,
)

@Serializable
data class Form1099Nec(
    val id: String,
    val payer: String = "",
    val box1Compensation: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
)

@Serializable
data class Form1099Misc(
    val id: String,
    val payer: String = "",
    val box1Rents: Double = 0.0,
    val box2Royalties: Double = 0.0,
    val box3OtherIncome: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
)

@Serializable
data class Form1099G(
    val id: String,
    val payer: String = "",
    val box1Unemployment: Double = 0.0,
    val box2StateRefund: Double = 0.0,
    val box4FederalWithholding: Double = 0.0,
)

@Serializable
data class Form1099Sa(
    val id: String,
    val box1GrossDistribution: Double = 0.0,
    val unqualifiedAmount: Double = 0.0,
)

@Serializable
data class ScheduleCBusiness(
    val id: String,
    val owner: IncomeOwner = IncomeOwner.Taxpayer,
    val businessName: String = "",
    val description: String = "",
    val grossReceipts: Double = 0.0,
    val costOfGoodsSold: Double = 0.0,
    val expenses: Map<String, Double> = emptyMap(),
    val homeOfficeDeduction: Double = 0.0,
    val vehicleExpense: Double = 0.0,
    val isSSTB: Boolean = false,
)

@Serializable
data class ScheduleEProperty(
    val id: String,
    val description: String = "",
    val netIncome: Double = 0.0,
)

@Serializable
data class IncomeFlags(
    val hasW2: Boolean = false,
    val hasInterest: Boolean = false,
    val hasDividends: Boolean = false,
    val hasCapitalGains: Boolean = false,
    val hasRetirementDistributions: Boolean = false,
    val hasSocialSecurity: Boolean = false,
    val hasSelfEmployment: Boolean = false,
    val hasRental: Boolean = false,
    val hasUnemployment: Boolean = false,
    val hasOtherIncome: Boolean = false,
)

@Serializable
data class IncomeData(
    val w2: List<W2> = emptyList(),
    val f1099Int: List<Form1099Int> = emptyList(),
    val f1099Div: List<Form1099Div> = emptyList(),
    val f1099B: List<CapitalTransaction> = emptyList(),
    val f1099R: List<Form1099R> = emptyList(),
    val f1099Ssa: List<Form1099Ssa> = emptyList(),
    val f1099Nec: List<Form1099Nec> = emptyList(),
    val f1099Misc: List<Form1099Misc> = emptyList(),
    val f1099G: List<Form1099G> = emptyList(),
    val f1099Sa: List<Form1099Sa> = emptyList(),
    val scheduleC: List<ScheduleCBusiness> = emptyList(),
    val scheduleE: List<ScheduleEProperty> = emptyList(),
    val otherIncome: Double = 0.0,
    val capitalLossCarryoverShort: Double = 0.0,
    val capitalLossCarryoverLong: Double = 0.0,
    val flags: IncomeFlags = IncomeFlags(),
)

@Serializable
enum class HsaCoverage {
    @SerialName("self-only") SelfOnly,
    @SerialName("family") Family,
    @SerialName("none") None,
}

@Serializable
data class Adjustments(
    val educatorExpenses: Double = 0.0,
    val hsaContribution: Double = 0.0,
    val hsaCoverage: HsaCoverage = HsaCoverage.None,
    val sepSimpleContribution: Double = 0.0,
    val selfEmployedHealthInsurance: Double = 0.0,
    val traditionalIraContribution: Double = 0.0,
    val coveredByWorkplacePlan: Boolean = false,
    val spouseCoveredByWorkplacePlan: Boolean = false,
    val studentLoanInterest: Double = 0.0,
)

@Serializable
data class ItemizedDeductions(
    val medicalExpenses: Double = 0.0,
    val stateLocalIncomeOrSalesTax: Double = 0.0,
    val realEstateTaxes: Double = 0.0,
    val personalPropertyTaxes: Double = 0.0,
    val mortgageInterest: Double = 0.0,
    val mortgageBalance: Double = 0.0,
    val mortgageAfterDec2017: Boolean = true,
    val charitableCash: Double = 0.0,
    val charitableNonCash: Double = 0.0,
    val casualtyLosses: Double = 0.0,
)

@Serializable
data class EducationStudent(
    val id: String,
    val name: String = "",
    val qualifiedExpenses: Double = 0.0,
    val aotcEligible: Boolean = true,
    val priorAotcYears: Double = 0.0,
    val felonyDrugConviction: Boolean = false,
)

@Serializable
data class CareCreditInput(
    val expenses: Double = 0.0,
    val taxpayerEarnedIncome: Double = 0.0,
    val spouseEarnedIncome: Double = 0.0,
    val employerBenefits: Double = 0.0,
)

@Serializable
data class CreditInputs(
    val students: List<EducationStudent> = emptyList(),
    val hasEducationExpenses: Boolean = false,
    val care: CareCreditInput = CareCreditInput(),
    val hasCareExpenses: Boolean = false,
    val retirementContributions: Double = 0.0,
    val isFullTimeStudent: Boolean = false,
    val cleanEnergyCost: Double = 0.0,
    val evCreditAmount: Double = 0.0,
    val foreignTaxPaid: Double = 0.0,
    val hasMarketplaceCoverage: Boolean = false,
    val advancePremiumTaxCredit: Double = 0.0,
    val premiumTaxCreditAllowed: Double = 0.0,
)

@Serializable
data class Payments(
    val additionalWithholding: Double = 0.0,
    val estimatedPayments: Double = 0.0,
    val priorYearTax: Double? = null,
    val priorYearAgi: Double? = null,
)

@Serializable
enum class BankAccountType {
    @SerialName("checking") Checking,
    @SerialName("savings") Savings,
}

@Serializable
data class BankInfo(
    val routingNumber: String = "",
    val accountNumber: String = "",
    val accountType: BankAccountType = BankAccountType.Checking,
)

@Serializable
data class TaxReturnMeta(val taxYear: Int = 2025, val lastEdited: String = "")

@Serializable
data class Residency(
    val state: StateCode? = null,
    val partYearResident: Boolean = false,
    val stateWithholding: Double = 0.0,
)

@Serializable
data class NewDeductions2025(
    val qualifiedTips: Double = 0.0,
    val qualifiedOvertime: Double = 0.0,
    val carLoanInterest: Double = 0.0,
)

@Serializable
data class TaxReturn2025(
    val meta: TaxReturnMeta = TaxReturnMeta(),
    val taxpayer: TaxpayerInfo = TaxpayerInfo(),
    val spouse: TaxpayerInfo? = null,
    val address: Address = Address(),
    val filingStatus: FilingStatus = FilingStatus.Single,
    val dependents: List<Dependent> = emptyList(),
    val residency: Residency = Residency(),
    /** MFS only: lived apart from spouse for ALL of 2025 (affects SS base amounts). */
    val livedApartFromSpouse: Boolean = false,
    val income: IncomeData = IncomeData(),
    val adjustments: Adjustments = Adjustments(),
    val newDeductions: NewDeductions2025 = NewDeductions2025(),
    val itemized: ItemizedDeductions = ItemizedDeductions(),
    /** Force itemizing even when standard is larger (e.g. MFS spouse itemized). */
    val forceItemize: Boolean = false,
    val credits: CreditInputs = CreditInputs(),
    val payments: Payments = Payments(),
    /** SENSITIVE — never persisted. */
    val bank: BankInfo? = null,
)

// MARK: - Result

@Serializable
enum class DeductionUsed {
    @SerialName("standard") Standard,
    @SerialName("itemized") Itemized,
}

@Serializable
data class MagiBreakdown(
    val niit: Double,
    val ira: Double,
    val studentLoan: Double,
    val ptc: Double,
    val ctc: Double,
    val aotc: Double,
)

@Serializable
data class CapitalLossCarryover(val shortTerm: Double, val longTerm: Double)

/** One traced 1040 line: what it is, where it comes from, and its amount. */
@Serializable
data class LineTrace(
    val id: String,
    val label: String,
    val formRef: String,
    val amount: Double,
)

@Serializable
data class Warning(val code: String, val message: String)

@Serializable
enum class AuditSeverity {
    @SerialName("info") Info,
    @SerialName("warn") Warn,
    @SerialName("high") High,
}

@Serializable
data class AuditFlag(
    val severity: AuditSeverity,
    val message: String,
    val relatedLine: String? = null,
)

@Serializable
data class StateResult(
    val code: StateCode,
    val name: String,
    val hasIncomeTax: Boolean,
    /** False when the state has a tax we don't model — say so, don't guess. */
    val supported: Boolean,
    val taxableIncome: Double = 0.0,
    val tax: Double = 0.0,
    val withholding: Double = 0.0,
    val refundOrOwed: Double = 0.0,
    val note: String? = null,
)

@Serializable
data class TaxCalculationResult(
    val filingStatus: FilingStatus,
    val totalIncome: Double,
    val totalAdjustments: Double,
    val agi: Double,
    val magi: MagiBreakdown,
    val standardDeduction: Double,
    val itemizedDeduction: Double,
    val deductionUsed: DeductionUsed,
    val deductionAmount: Double,
    val itemizedSavings: Double,
    val qbiDeduction: Double,
    val taxableIncomeBeforeQbi: Double,
    val taxableIncome: Double,
    val regularTax: Double,
    val usedTaxTable: Boolean,
    val usedQualDivWorksheet: Boolean,
    val amt: Double,
    val additionalMedicareTax: Double,
    val niit: Double,
    val seTax: Double,
    val nonrefundableCredits: Map<String, Double>,
    val totalNonrefundableCredits: Double,
    val refundableCredits: Map<String, Double>,
    val totalRefundableCredits: Double,
    val otherTaxes: Double,
    val totalTax: Double,
    val totalPayments: Double,
    val refundOrOwed: Double,
    val owes: Boolean,
    val underpaymentPenalty: Double,
    val marginalRate: Double,
    val effectiveRate: Double,
    val capitalLossCarryover: CapitalLossCarryover,
    val trace: List<LineTrace>,
    val warnings: List<Warning>,
    val auditFlags: List<AuditFlag>,
    val state: StateResult? = null,
)
