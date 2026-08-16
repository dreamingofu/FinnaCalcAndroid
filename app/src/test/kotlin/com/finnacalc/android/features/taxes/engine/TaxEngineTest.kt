package com.finnacalc.android.features.taxes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engine tests against hand-computed 2025 figures. Every expectation here is
 * derived from the published tables (Rev. Proc. 2024-40 + OBBBA), not from the
 * implementation, so a regression in the port shows up as a failure.
 */
class TaxEngineTest {

    private fun w2(wages: Double, withholding: Double = 0.0, owner: IncomeOwner = IncomeOwner.Taxpayer) =
        W2(
            id = "w-$wages-$owner",
            owner = owner,
            box1Wages = wages,
            box2FederalWithholding = withholding,
            box3SsWages = wages,
            box5MedicareWages = wages,
        )

    private fun simpleReturn(
        wages: Double = 0.0,
        withholding: Double = 0.0,
        status: FilingStatus = FilingStatus.Single,
        dependents: List<Dependent> = emptyList(),
        dob: String = "1990-06-15",
    ) = TaxReturn2025(
        taxpayer = TaxpayerInfo(firstName = "Test", lastName = "Filer", dateOfBirth = dob),
        filingStatus = status,
        dependents = dependents,
        income = IncomeData(
            w2 = if (wages > 0) listOf(w2(wages, withholding)) else emptyList(),
            flags = IncomeFlags(hasW2 = wages > 0),
        ),
    )

    private fun child(id: String) = Dependent(
        id = id,
        firstName = "Kid",
        dateOfBirth = "2018-03-01",
        qualifiesForCTC = true,
        qualifiesForEITC = true,
    )

    // MARK: Rounding

    @Test
    fun `dollar rounds half away from zero`() {
        assertEquals(1.0, dollar(0.5), 0.0)
        assertEquals(0.0, dollar(0.49), 0.0)
        assertEquals(-1.0, dollar(-0.5), 0.0)
        assertEquals(124.0, dollar(123.5), 0.0)
        assertEquals(0.0, dollar(Double.NaN), 0.0)
    }

    // MARK: Brackets

    @Test
    fun `bracket tax matches the published single schedule`() {
        // 2025 single: 10% to 11,925 then 12% to 48,475.
        // At exactly 48,475: 1,192.50 + 4,386.00 = 5,578.50.
        assertEquals(5_578.50, bracketTax(48_475.0, FilingStatus.Single), 0.01)
        // MFJ mirrors it at double the width up to the 22% bracket.
        assertEquals(11_157.0, bracketTax(96_950.0, FilingStatus.Mfj), 0.01)
    }

    @Test
    fun `marginal rate steps at the bracket edges`() {
        assertEquals(0.10, marginalRate(11_925.0, FilingStatus.Single), 0.0)
        assertEquals(0.12, marginalRate(11_925.01, FilingStatus.Single), 0.0)
        assertEquals(0.37, marginalRate(700_000.0, FilingStatus.Single), 0.0)
    }

    @Test
    fun `the tax table taxes a bucket midpoint below 100k`() {
        val result = computeRegularTax(50_010.0, FilingStatus.Single)
        assertTrue(result.usedTaxTable)
        // The $50,000–50,050 row is taxed at its $50,025 midpoint.
        assertEquals(dollar(bracketTax(50_025.0, FilingStatus.Single)), result.tax, 0.0)
        // At/above $100,000 the Computation Worksheet applies exactly.
        val big = computeRegularTax(150_000.0, FilingStatus.Single)
        assertTrue(!big.usedTaxTable)
        assertEquals(dollar(bracketTax(150_000.0, FilingStatus.Single)), big.tax, 0.0)
    }

    // MARK: Standard deduction

    @Test
    fun `standard deduction matches OBBBA amounts`() {
        assertEquals(15_750.0, computeStandardDeduction(simpleReturn(), 0.0), 0.0)
        assertEquals(
            31_500.0,
            computeStandardDeduction(simpleReturn(status = FilingStatus.Mfj), 0.0),
            0.0,
        )
        assertEquals(
            23_625.0,
            computeStandardDeduction(simpleReturn(status = FilingStatus.Hoh), 0.0),
            0.0,
        )
    }

    @Test
    fun `age 65 adds the unmarried additional amount`() {
        // Born 1950 → 65+ for 2025: 15,750 + 2,000.
        val r = simpleReturn(dob = "1950-01-01")
        assertEquals(17_750.0, computeStandardDeduction(r, 0.0), 0.0)
    }

    @Test
    fun `a dependent's standard deduction is limited to earned income plus 450`() {
        val r = simpleReturn().copy(
            taxpayer = TaxpayerInfo(dateOfBirth = "2005-01-01", claimedAsDependentByAnother = true),
        )
        // Earned 1,000 → max(1,350, 1,450) = 1,450.
        assertEquals(1_450.0, computeStandardDeduction(r, 1_000.0), 0.0)
        // Tiny earnings floor at 1,350.
        assertEquals(1_350.0, computeStandardDeduction(r, 100.0), 0.0)
        // Never above the regular standard deduction.
        assertEquals(15_750.0, computeStandardDeduction(r, 90_000.0), 0.0)
    }

    // MARK: End-to-end

    @Test
    fun `a simple single wage return computes AGI, taxable income and tax`() {
        val result = calculateFederalTax(simpleReturn(wages = 60_000.0, withholding = 6_000.0))
        assertEquals(60_000.0, result.totalIncome, 0.0)
        assertEquals(60_000.0, result.agi, 0.0)
        // 60,000 − 15,750 standard = 44,250 taxable.
        assertEquals(44_250.0, result.taxableIncome, 0.0)
        assertEquals(DeductionUsed.Standard, result.deductionUsed)
        // Tax table on the 44,250–44,300 bucket midpoint.
        assertEquals(dollar(bracketTax(44_275.0, FilingStatus.Single)), result.regularTax, 0.0)
        // 44,250 sits in the 12% bracket (11,925–48,475).
        assertEquals(12.0, result.marginalRate, 0.0)
        assertTrue(!result.owes)
        assertEquals(6_000.0 - result.totalTax, result.refundOrOwed, 0.0)
    }

    @Test
    fun `zero income produces no tax and no refund`() {
        val result = calculateFederalTax(simpleReturn())
        assertEquals(0.0, result.totalTax, 0.0)
        assertEquals(0.0, result.taxableIncome, 0.0)
        assertEquals(0.0, result.refundOrOwed, 0.0)
    }

    @Test
    fun `the trace carries the 1040 lines in order`() {
        val result = calculateFederalTax(simpleReturn(wages = 60_000.0))
        val ids = result.trace.map { it.id }
        assertTrue(ids.contains("totalIncome"))
        assertTrue(ids.indexOf("totalIncome") < ids.indexOf("agi"))
        assertTrue(ids.indexOf("agi") < ids.indexOf("taxableIncome"))
        assertTrue(ids.indexOf("taxableIncome") < ids.indexOf("totalTax"))
        // Every trace line names its form reference.
        assertTrue(result.trace.all { it.formRef.isNotEmpty() })
    }

    // MARK: Child Tax Credit

    @Test
    fun `child tax credit is 2200 per child and phases out over the threshold`() {
        val r = simpleReturn(wages = 60_000.0, withholding = 3_000.0, dependents = listOf(child("c1")))
        val result = calculateFederalTax(r)
        assertEquals(2_200.0, result.nonrefundableCredits["childTaxCredit"]!!, 0.0)

        // At 210,000 MAGI (single), the phaseout takes $50 per $1,000 over
        // 200,000 → 10 steps × 50 = 500 off.
        val ctc = computeChildTaxCredit(r, 210_000.0, 50_000.0, 60_000.0)
        assertEquals(2_200.0, ctc.tentativeCredit, 0.0)
        assertEquals(1_700.0, ctc.creditAfterPhaseout, 0.0)
    }

    @Test
    fun `additional child tax credit is capped by the earned income formula`() {
        // Low income: 15% of (earned − 2,500), capped at 1,700 per child.
        val ctc = computeChildTaxCredit(
            simpleReturn(dependents = listOf(child("c1"))),
            magi = 12_000.0, taxAvailable = 0.0, earnedIncome = 12_000.0,
        )
        // 15% of 9,500 = 1,425 < the 1,700 cap.
        assertEquals(1_425.0, ctc.additionalChildTaxCredit, 0.01)
    }

    // MARK: EITC

    @Test
    fun `eitc peaks at the published maximum for one child`() {
        val r = simpleReturn(wages = 12_730.0, dependents = listOf(child("c1")))
        val result = computeEitc(
            ComputeEitcParams(r = r, earnedIncome = 12_730.0, agi = 12_730.0, investmentIncome = 0.0, taxpayerAge = 35.0)
        )
        assertEquals(4_328.0, result.credit, 1.0)
    }

    @Test
    fun `investment income over the limit disqualifies the eitc`() {
        val r = simpleReturn(wages = 12_000.0, dependents = listOf(child("c1")))
        val result = computeEitc(
            ComputeEitcParams(r = r, earnedIncome = 12_000.0, agi = 12_000.0, investmentIncome = 12_000.0, taxpayerAge = 35.0)
        )
        assertEquals(0.0, result.credit, 0.0)
        assertTrue(!result.eligible)
        assertNotNull(result.disqualReason)
    }

    @Test
    fun `childless eitc requires age 25 to 64`() {
        val r = simpleReturn(wages = 9_000.0)
        val young = computeEitc(
            ComputeEitcParams(r = r, earnedIncome = 9_000.0, agi = 9_000.0, investmentIncome = 0.0, taxpayerAge = 20.0)
        )
        assertEquals(0.0, young.credit, 0.0)
        val eligible = computeEitc(
            ComputeEitcParams(r = r, earnedIncome = 9_000.0, agi = 9_000.0, investmentIncome = 0.0, taxpayerAge = 30.0)
        )
        assertTrue(eligible.credit > 0)
    }

    // MARK: Self-employment

    @Test
    fun `se tax applies 15,3 percent to 92,35 percent of net profit`() {
        val net = 100_000.0
        val se = computeSelfEmploymentTax(
            mapOf(Owner.Taxpayer to net, Owner.Spouse to 0.0),
            mapOf(Owner.Taxpayer to 0.0, Owner.Spouse to 0.0),
        )
        val earnings = net * 0.9235
        assertEquals(earnings * (0.124 + 0.029), se.seTax, 0.01)
        assertEquals(se.seTax / 2, se.deduction, 0.01)
    }

    @Test
    fun `w2 social security wages consume the wage base before se earnings`() {
        // W-2 already at the wage base: only the 2.9% Medicare part remains.
        val se = computeSelfEmploymentTax(
            mapOf(Owner.Taxpayer to 50_000.0),
            mapOf(Owner.Taxpayer to 176_100.0),
        )
        val earnings = 50_000.0 * 0.9235
        assertEquals(earnings * 0.029, se.seTax, 0.01)
    }

    @Test
    fun `net earnings under 400 owe no se tax`() {
        val se = computeSelfEmploymentTax(mapOf(Owner.Taxpayer to 400.0), emptyMap())
        assertEquals(0.0, se.seTax, 0.0)
    }

    // MARK: Social Security taxability

    @Test
    fun `social security is untaxed below the base amount`() {
        val taxable = computeTaxableSocialSecurity(
            TaxableSocialSecurityParams(
                benefits = 20_000.0, otherIncome = 10_000.0, taxExemptInterest = 0.0,
                adjustmentsForProvisional = 0.0, status = FilingStatus.Single, livedApartFromSpouse = false,
            )
        )
        // Provisional = 10,000 + 10,000 = 20,000 < 25,000 base.
        assertEquals(0.0, taxable, 0.0)
    }

    @Test
    fun `social security caps at 85 percent of benefits`() {
        val taxable = computeTaxableSocialSecurity(
            TaxableSocialSecurityParams(
                benefits = 20_000.0, otherIncome = 200_000.0, taxExemptInterest = 0.0,
                adjustmentsForProvisional = 0.0, status = FilingStatus.Single, livedApartFromSpouse = false,
            )
        )
        assertEquals(17_000.0, taxable, 0.01)
    }

    // MARK: Capital gains

    @Test
    fun `capital losses are limited to 3000 with the rest carried over`() {
        val r = simpleReturn(wages = 50_000.0).let {
            it.copy(
                income = it.income.copy(
                    f1099B = listOf(
                        CapitalTransaction(id = "t1", proceeds = 1_000.0, costBasis = 11_000.0, longTerm = false)
                    ),
                    flags = it.income.flags.copy(hasCapitalGains = true),
                )
            )
        }
        val gains = computeCapitalGains(r)
        assertEquals(-10_000.0, gains.totalNet, 0.0)
        assertEquals(3_000.0, gains.allowedLoss, 0.0)
        assertEquals(-3_000.0, gains.includedInIncome, 0.0)
        assertEquals(7_000.0, gains.carryoverShort, 0.0)
    }

    @Test
    fun `preferential rates stack on top of ordinary income`() {
        // Single, 0% band up to 48,350 of taxable income.
        val stack = preferentialStackTax(40_000.0, 20_000.0, FilingStatus.Single)
        assertEquals(8_350.0, stack.amountAt0, 0.01)
        assertEquals(11_650.0, stack.amountAt15, 0.01)
        assertEquals(0.0, stack.amountAt20, 0.01)
        assertEquals(11_650.0 * 0.15, stack.tax, 0.01)
    }

    @Test
    fun `the qualified dividend worksheet never exceeds all-ordinary tax`() {
        val ti = 80_000.0
        val worksheet = computeQualifiedDivCapGainTax(ti, 10_000.0, 0.0, FilingStatus.Single)
        val allOrdinary = computeRegularTax(ti, FilingStatus.Single).tax
        assertTrue(worksheet.tax <= allOrdinary)
    }

    // MARK: Other taxes

    @Test
    fun `additional medicare tax applies over the threshold`() {
        // Single threshold 200,000; 0.9% on the excess.
        assertEquals(900.0, computeAdditionalMedicareTax(300_000.0, 0.0, FilingStatus.Single), 0.01)
        assertEquals(0.0, computeAdditionalMedicareTax(150_000.0, 0.0, FilingStatus.Single), 0.0)
    }

    @Test
    fun `niit taxes the lesser of investment income and magi over the threshold`() {
        // 3.8% of min(50,000 NII, 20,000 over threshold) = 760.
        assertEquals(760.0, computeNiit(50_000.0, 220_000.0, FilingStatus.Single), 0.01)
        assertEquals(0.0, computeNiit(50_000.0, 150_000.0, FilingStatus.Single), 0.0)
    }

    // MARK: SALT

    @Test
    fun `salt cap phases down but never below the floor`() {
        assertEquals(40_000.0, effectiveSaltCap(FilingStatus.Single, 400_000.0), 0.0)
        // 30% of 100,000 over the threshold = 30,000 off.
        assertEquals(10_000.0, effectiveSaltCap(FilingStatus.Single, 600_000.0), 0.0)
        // Never below the old $10,000 floor.
        assertEquals(10_000.0, effectiveSaltCap(FilingStatus.Single, 2_000_000.0), 0.0)
        assertEquals(20_000.0, effectiveSaltCap(FilingStatus.Mfs, 100_000.0), 0.0)
    }

    // MARK: OBBBA deductions

    @Test
    fun `senior deduction is 6000 per qualifying person and phases out`() {
        val senior = simpleReturn(dob = "1950-01-01")
        assertEquals(6_000.0, computeSeniorDeduction(senior, 50_000.0), 0.0)
        // 6% of 25,000 over the 75,000 threshold = 1,500 off.
        assertEquals(4_500.0, computeSeniorDeduction(senior, 100_000.0), 0.0)
        assertEquals(0.0, computeSeniorDeduction(simpleReturn(), 50_000.0), 0.0)
    }

    @Test
    fun `tips and overtime deductions are unavailable to MFS`() {
        val mfs = simpleReturn(status = FilingStatus.Mfs)
            .copy(newDeductions = NewDeductions2025(qualifiedTips = 5_000.0, qualifiedOvertime = 5_000.0))
        assertEquals(0.0, computeTipsDeduction(mfs, 50_000.0), 0.0)
        assertEquals(0.0, computeOvertimeDeduction(mfs, 50_000.0), 0.0)

        val single = simpleReturn().copy(newDeductions = NewDeductions2025(qualifiedTips = 5_000.0))
        assertEquals(5_000.0, computeTipsDeduction(single, 50_000.0), 0.0)
    }

    // MARK: QBI

    @Test
    fun `qbi is 20 percent under the threshold and zero for a phased-out SSTB`() {
        val under = computeQbiDeduction(
            ComputeQbiDeductionParams(100_000.0, 150_000.0, 0.0, isSSTB = false, status = FilingStatus.Single)
        )
        assertEquals(20_000.0, under.deduction, 0.0)
        assertTrue(!under.wageLimitMayApply)

        // SSTB fully phased out above threshold + range.
        val sstb = computeQbiDeduction(
            ComputeQbiDeductionParams(100_000.0, 300_000.0, 0.0, isSSTB = true, status = FilingStatus.Single)
        )
        assertEquals(0.0, sstb.deduction, 0.0)

        // Non-SSTB above the threshold keeps the deduction but flags the wage limit.
        val nonSstb = computeQbiDeduction(
            ComputeQbiDeductionParams(100_000.0, 300_000.0, 0.0, isSSTB = false, status = FilingStatus.Single)
        )
        assertTrue(nonSstb.wageLimitMayApply)
    }

    // MARK: State

    @Test
    fun `a no-tax state reports no income tax rather than zero owed`() {
        val result = computeStateTax(
            StateInput(StateCode.TX, 100_000.0, 0.0, 0.0, FilingStatus.Single, 0.0, 0.0, false)
        )!!
        assertTrue(!result.hasIncomeTax)
        assertTrue(result.supported)
        assertEquals("Texas", result.name)
    }

    @Test
    fun `an unmodelled state says so instead of guessing`() {
        val result = computeStateTax(
            StateInput(StateCode.MT, 100_000.0, 0.0, 0.0, FilingStatus.Single, 0.0, 0.0, false)
        )!!
        assertTrue(!result.supported)
        assertEquals(0.0, result.tax, 0.0)
        assertTrue(result.note!!.contains("isn't estimated yet"))
    }

    @Test
    fun `a flat state applies its rate after exemptions`() {
        // Illinois: flat 4.95%, $2,850 personal exemption.
        val result = computeStateTax(
            StateInput(StateCode.IL, 100_000.0, 0.0, 0.0, FilingStatus.Single, 0.0, 5_000.0, false)
        )!!
        assertEquals(97_150.0, result.taxableIncome, 0.0)
        assertEquals(dollar(97_150.0 * 0.0495), result.tax, 1.0)
    }

    @Test
    fun `state tax excludes taxable social security`() {
        val withSS = computeStateTax(
            StateInput(StateCode.NC, 100_000.0, 20_000.0, 0.0, FilingStatus.Single, 0.0, 0.0, false)
        )!!
        // 100,000 AGI − 20,000 SS − 12,750 standard = 67,250 taxable.
        assertEquals(67_250.0, withSS.taxableIncome, 0.0)
    }

    @Test
    fun `no state selected returns nothing at all`() {
        assertNull(
            computeStateTax(StateInput(null, 100_000.0, 0.0, 0.0, FilingStatus.Single, 0.0, 0.0, false))
        )
    }

    // MARK: Warnings

    @Test
    fun `withheld nothing on real wages raises an audit flag`() {
        val result = calculateFederalTax(simpleReturn(wages = 60_000.0, withholding = 0.0))
        assertTrue(result.auditFlags.any { it.relatedLine == "totalPayments" })
    }
}
