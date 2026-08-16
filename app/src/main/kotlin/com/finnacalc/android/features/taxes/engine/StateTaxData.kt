/**
 * StateTaxData.kt
 *
 * Port of iOS Features/Taxes/Engine/StateTaxData.swift — the state income-tax
 * estimates. Only the states modelled here return figures; anything else says
 * plainly that it isn't estimated yet rather than guessing.
 *
 * Every config carries a note naming what it approximates and what it does NOT
 * model (age deductions, retirement exclusions, surcharges) so the estimate is
 * never mistaken for a return.
 */

package com.finnacalc.android.features.taxes.engine

data class StateBracket(val rate: Double, val min: Double, val max: Double)

data class StateInput(
    val code: StateCode?,
    val federalAgi: Double,
    val taxableSocialSecurity: Double,
    val retirementDistributions: Double,
    val filingStatus: FilingStatus,
    val dependents: Double,
    val stateWithholding: Double,
    val age65: Boolean,
)

data class StateConfig(
    val code: StateCode,
    val name: String,
    val hasIncomeTax: Boolean,
    val brackets: Map<FilingStatus, List<StateBracket>>? = null,
    val standardDeduction: Map<FilingStatus, Double>? = null,
    val personalExemption: Double? = null,
    val dependentExemption: Double? = null,
    val exemptionCredit: Double? = null,
    val dependentExemptionCredit: Double? = null,
    val taxesSocialSecurity: Boolean? = null,
    val excludesRetirement: Boolean? = null,
    val note: String? = null,
)

private fun flat(rate: Double) = listOf(StateBracket(rate, 0.0, Double.POSITIVE_INFINITY))

private fun uniform(b: List<StateBracket>): Map<FilingStatus, List<StateBracket>> = mapOf(
    FilingStatus.Single to b,
    FilingStatus.Mfj to b,
    FilingStatus.Mfs to b,
    FilingStatus.Hoh to b,
    FilingStatus.Qss to b,
)

private fun byStatus(
    single: List<StateBracket>,
    mfj: List<StateBracket>,
    hoh: List<StateBracket>,
): Map<FilingStatus, List<StateBracket>> = mapOf(
    FilingStatus.Single to single,
    FilingStatus.Mfs to single,
    FilingStatus.Mfj to mfj,
    FilingStatus.Qss to mfj,
    FilingStatus.Hoh to hoh,
)

private fun std(
    single: Double,
    mfj: Double,
    hoh: Double,
    mfs: Double? = null,
    qss: Double? = null,
): Map<FilingStatus, Double> = mapOf(
    FilingStatus.Single to single,
    FilingStatus.Mfj to mfj,
    FilingStatus.Hoh to hoh,
    FilingStatus.Mfs to (mfs ?: single),
    FilingStatus.Qss to (qss ?: mfj),
)

private const val NO_TAX_NOTE = "No state income tax."

val STATE_CONFIGS: Map<String, StateConfig> = mapOf(
    "TX" to StateConfig(StateCode.TX, "Texas", false, note = NO_TAX_NOTE),
    "FL" to StateConfig(StateCode.FL, "Florida", false, note = NO_TAX_NOTE),
    "TN" to StateConfig(StateCode.TN, "Tennessee", false, note = NO_TAX_NOTE),
    "WA" to StateConfig(
        StateCode.WA, "Washington", false,
        note = "No state income tax on wages. (Washington has a separate 7% excise on large " +
            "long-term capital gains, not modeled here.)",
    ),
    "PA" to StateConfig(
        StateCode.PA, "Pennsylvania", true,
        brackets = uniform(flat(0.0307)),
        taxesSocialSecurity = false,
        excludesRetirement = true,
        note = "Flat 3.07%. Retirement income and Social Security aren't taxed; PA's " +
            "class-of-income rules are approximated from federal AGI.",
    ),
    "IL" to StateConfig(
        StateCode.IL, "Illinois", true,
        brackets = uniform(flat(0.0495)),
        personalExemption = 2_850.0,
        dependentExemption = 2_850.0,
        excludesRetirement = true,
        note = "Flat 4.95%; retirement income and Social Security excluded.",
    ),
    "MI" to StateConfig(
        StateCode.MI, "Michigan", true,
        brackets = uniform(flat(0.0425)),
        personalExemption = 5_800.0,
        dependentExemption = 5_800.0,
        note = "Flat 4.25%. Social Security excluded; age-based retirement subtractions are not modeled.",
    ),
    "NC" to StateConfig(
        StateCode.NC, "North Carolina", true,
        brackets = uniform(flat(0.0425)),
        standardDeduction = std(12_750.0, 25_500.0, 19_125.0),
        note = "Flat 4.25% (2025). Social Security excluded.",
    ),
    "AZ" to StateConfig(
        StateCode.AZ, "Arizona", true,
        brackets = uniform(flat(0.025)),
        standardDeduction = std(15_000.0, 30_000.0, 22_500.0),
        note = "Flat 2.5% (2025 standard deduction). Social Security excluded.",
    ),
    "GA" to StateConfig(
        StateCode.GA, "Georgia", true,
        brackets = uniform(flat(0.0519)),
        standardDeduction = std(12_000.0, 24_000.0, 12_000.0),
        dependentExemption = 4_000.0,
        note = "Flat 5.19% (2025). Social Security excluded; the 62+ retirement exclusion isn't modeled.",
    ),
    "OH" to StateConfig(
        StateCode.OH, "Ohio", true,
        brackets = uniform(
            listOf(
                StateBracket(0.0, 0.0, 26_050.0),
                StateBracket(0.0275, 26_050.0, 100_000.0),
                StateBracket(0.03125, 100_000.0, Double.POSITIVE_INFINITY),
            )
        ),
        personalExemption = 2_400.0,
        dependentExemption = 2_400.0,
        note = "2025 brackets (0% up to $26,050, then 2.75%, 3.125% over $100k). Social Security excluded.",
    ),
    "VA" to StateConfig(
        StateCode.VA, "Virginia", true,
        brackets = uniform(
            listOf(
                StateBracket(0.02, 0.0, 3_000.0),
                StateBracket(0.03, 3_000.0, 5_000.0),
                StateBracket(0.05, 5_000.0, 17_000.0),
                StateBracket(0.0575, 17_000.0, Double.POSITIVE_INFINITY),
            )
        ),
        standardDeduction = std(8_750.0, 17_500.0, 8_750.0),
        personalExemption = 930.0,
        dependentExemption = 930.0,
        note = "Social Security excluded; the age deduction isn't modeled.",
    ),
    "CA" to StateConfig(
        StateCode.CA, "California", true,
        brackets = byStatus(
            single = listOf(
                StateBracket(0.01, 0.0, 10_756.0),
                StateBracket(0.02, 10_756.0, 25_499.0),
                StateBracket(0.04, 25_499.0, 40_245.0),
                StateBracket(0.06, 40_245.0, 55_866.0),
                StateBracket(0.08, 55_866.0, 70_606.0),
                StateBracket(0.093, 70_606.0, 360_659.0),
                StateBracket(0.103, 360_659.0, 432_787.0),
                StateBracket(0.113, 432_787.0, 721_314.0),
                StateBracket(0.123, 721_314.0, Double.POSITIVE_INFINITY),
            ),
            mfj = listOf(
                StateBracket(0.01, 0.0, 21_512.0),
                StateBracket(0.02, 21_512.0, 50_998.0),
                StateBracket(0.04, 50_998.0, 80_490.0),
                StateBracket(0.06, 80_490.0, 111_732.0),
                StateBracket(0.08, 111_732.0, 141_212.0),
                StateBracket(0.093, 141_212.0, 721_318.0),
                StateBracket(0.103, 721_318.0, 865_574.0),
                StateBracket(0.113, 865_574.0, 1_442_628.0),
                StateBracket(0.123, 1_442_628.0, Double.POSITIVE_INFINITY),
            ),
            hoh = listOf(
                StateBracket(0.01, 0.0, 21_527.0),
                StateBracket(0.02, 21_527.0, 51_000.0),
                StateBracket(0.04, 51_000.0, 65_744.0),
                StateBracket(0.06, 65_744.0, 81_364.0),
                StateBracket(0.08, 81_364.0, 96_107.0),
                StateBracket(0.093, 96_107.0, 490_493.0),
                StateBracket(0.103, 490_493.0, 588_593.0),
                StateBracket(0.113, 588_593.0, 980_987.0),
                StateBracket(0.123, 980_987.0, Double.POSITIVE_INFINITY),
            ),
        ),
        standardDeduction = std(5_540.0, 11_080.0, 11_080.0),
        exemptionCredit = 149.0,
        dependentExemptionCredit = 461.0,
        note = "2024 index values, the newest verified; California re-indexes annually. Social " +
            "Security excluded. The 1% mental-health surcharge over $1M isn't modeled.",
    ),
    "NY" to StateConfig(
        StateCode.NY, "New York", true,
        brackets = byStatus(
            single = listOf(
                StateBracket(0.04, 0.0, 8_500.0),
                StateBracket(0.045, 8_500.0, 11_700.0),
                StateBracket(0.0525, 11_700.0, 13_900.0),
                StateBracket(0.055, 13_900.0, 80_650.0),
                StateBracket(0.06, 80_650.0, 215_400.0),
                StateBracket(0.0685, 215_400.0, 1_077_550.0),
                StateBracket(0.0965, 1_077_550.0, 5_000_000.0),
                StateBracket(0.103, 5_000_000.0, 25_000_000.0),
                StateBracket(0.109, 25_000_000.0, Double.POSITIVE_INFINITY),
            ),
            mfj = listOf(
                StateBracket(0.04, 0.0, 17_150.0),
                StateBracket(0.045, 17_150.0, 23_600.0),
                StateBracket(0.0525, 23_600.0, 27_900.0),
                StateBracket(0.055, 27_900.0, 161_550.0),
                StateBracket(0.06, 161_550.0, 323_200.0),
                StateBracket(0.0685, 323_200.0, 2_155_350.0),
                StateBracket(0.0965, 2_155_350.0, 5_000_000.0),
                StateBracket(0.103, 5_000_000.0, 25_000_000.0),
                StateBracket(0.109, 25_000_000.0, Double.POSITIVE_INFINITY),
            ),
            hoh = listOf(
                StateBracket(0.04, 0.0, 12_800.0),
                StateBracket(0.045, 12_800.0, 17_650.0),
                StateBracket(0.0525, 17_650.0, 20_900.0),
                StateBracket(0.055, 20_900.0, 107_650.0),
                StateBracket(0.06, 107_650.0, 269_300.0),
                StateBracket(0.0685, 269_300.0, 1_616_450.0),
                StateBracket(0.0965, 1_616_450.0, 5_000_000.0),
                StateBracket(0.103, 5_000_000.0, 25_000_000.0),
                StateBracket(0.109, 25_000_000.0, Double.POSITIVE_INFINITY),
            ),
        ),
        standardDeduction = std(8_000.0, 16_050.0, 11_200.0),
        dependentExemption = 1_000.0,
        note = "Social Security excluded; pension exclusion and tax-benefit recapture aren't modeled.",
    ),
    "NJ" to StateConfig(
        StateCode.NJ, "New Jersey", true,
        brackets = byStatus(
            single = listOf(
                StateBracket(0.014, 0.0, 20_000.0),
                StateBracket(0.0175, 20_000.0, 35_000.0),
                StateBracket(0.035, 35_000.0, 40_000.0),
                StateBracket(0.05525, 40_000.0, 75_000.0),
                StateBracket(0.0637, 75_000.0, 500_000.0),
                StateBracket(0.0897, 500_000.0, 1_000_000.0),
                StateBracket(0.1075, 1_000_000.0, Double.POSITIVE_INFINITY),
            ),
            mfj = listOf(
                StateBracket(0.014, 0.0, 20_000.0),
                StateBracket(0.0175, 20_000.0, 50_000.0),
                StateBracket(0.0245, 50_000.0, 70_000.0),
                StateBracket(0.035, 70_000.0, 80_000.0),
                StateBracket(0.05525, 80_000.0, 150_000.0),
                StateBracket(0.0637, 150_000.0, 500_000.0),
                StateBracket(0.0897, 500_000.0, 1_000_000.0),
                StateBracket(0.1075, 1_000_000.0, Double.POSITIVE_INFINITY),
            ),
            hoh = listOf(
                StateBracket(0.014, 0.0, 20_000.0),
                StateBracket(0.0175, 20_000.0, 50_000.0),
                StateBracket(0.0245, 50_000.0, 70_000.0),
                StateBracket(0.035, 70_000.0, 80_000.0),
                StateBracket(0.05525, 80_000.0, 150_000.0),
                StateBracket(0.0637, 150_000.0, 500_000.0),
                StateBracket(0.0897, 500_000.0, 1_000_000.0),
                StateBracket(0.1075, 1_000_000.0, Double.POSITIVE_INFINITY),
            ),
        ),
        personalExemption = 1_000.0,
        dependentExemption = 1_500.0,
        note = "No standard deduction. Social Security excluded; the retirement-income exclusion isn't modeled.",
    ),
)

private fun bracketTaxForState(amount: Double, brackets: List<StateBracket>): Double {
    if (amount <= 0) return 0.0
    var tax = 0.0
    for (b in brackets) {
        if (amount > b.min) {
            val upper = minOf(amount, b.max)
            tax += (upper - b.min) * b.rate
        }
    }
    return tax
}

private fun computeFromConfig(cfg: StateConfig, input: StateInput): StateResult {
    val withheld = dollar(input.stateWithholding)
    if (!cfg.hasIncomeTax || cfg.brackets == null) {
        return StateResult(
            code = cfg.code,
            name = cfg.name,
            hasIncomeTax = false,
            supported = true,
            taxableIncome = 0.0,
            tax = 0.0,
            withholding = withheld,
            refundOrOwed = withheld,
            note = cfg.note,
        )
    }
    val persons = 1.0 + (if (isMarriedStatus(input.filingStatus)) 1.0 else 0.0)
    var stateAgi = input.federalAgi
    if (cfg.taxesSocialSecurity != true) stateAgi -= input.taxableSocialSecurity
    if (cfg.excludesRetirement == true) stateAgi -= input.retirementDistributions
    stateAgi = nonNeg(stateAgi)

    val standardDeduction = cfg.standardDeduction?.get(input.filingStatus) ?: 0.0
    val exemptions =
        (cfg.personalExemption ?: 0.0) * persons + (cfg.dependentExemption ?: 0.0) * input.dependents
    val taxableIncome = nonNeg(stateAgi - standardDeduction - exemptions)
    var tax = bracketTaxForState(taxableIncome, cfg.brackets[input.filingStatus] ?: emptyList())
    val credits =
        (cfg.exemptionCredit ?: 0.0) * persons + (cfg.dependentExemptionCredit ?: 0.0) * input.dependents
    tax = nonNeg(dollar(tax) - credits)

    return StateResult(
        code = cfg.code,
        name = cfg.name,
        hasIncomeTax = true,
        supported = true,
        taxableIncome = dollar(taxableIncome),
        tax = dollar(tax),
        withholding = withheld,
        refundOrOwed = dollar(input.stateWithholding - tax),
        note = cfg.note,
    )
}

fun computeStateTax(input: StateInput): StateResult? {
    val code = input.code ?: return null
    val cfg = STATE_CONFIGS[code.name]
        // A state we don't model says so rather than reporting a zero that
        // reads like "you owe nothing".
        ?: return StateResult(
            code = code,
            name = code.name,
            hasIncomeTax = true,
            supported = false,
            taxableIncome = 0.0,
            tax = 0.0,
            withholding = dollar(input.stateWithholding),
            refundOrOwed = dollar(input.stateWithholding),
            note = "State tax for this state isn't estimated yet.",
        )
    return computeFromConfig(cfg, input)
}

data class SupportedState(val code: StateCode, val name: String, val hasIncomeTax: Boolean)

val SUPPORTED_STATES: List<SupportedState> =
    STATE_CONFIGS.values.map { SupportedState(it.code, it.name, it.hasIncomeTax) }
