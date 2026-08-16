package com.finnacalc.android.features.taxes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the interview layer ported in Questions.kt and Build1040Summary.kt:
 * section/question routing, the answer→return coercions, and the summary
 * structuring. Values check against the iOS/TS originals.
 */
class QuestionsTest {

    private fun num(v: Double) = AnswerValue.Num(v)
    private fun str(v: String) = AnswerValue.Str(v)
    private fun yes() = AnswerValue.Bool(true)

    // MARK: - Routing

    @Test
    fun `ungated sections are always visible`() {
        val visible = visibleSections(emptyMap()).map { it.id }
        assertEquals(
            listOf("about-you", "income-other", "adjustments", "deductions", "payments"),
            visible,
        )
    }

    @Test
    fun `life situation flags open their sections`() {
        val a = mapOf("ls_job" to yes(), "ls_deps" to yes())
        val visible = visibleSections(a).map { it.id }
        assertTrue(visible.contains("income-job"))
        assertTrue(visible.contains("dependents"))
        assertFalse(visible.contains("income-self"))
    }

    @Test
    fun `credits section opens on any of its four flags`() {
        listOf("ls_care", "ls_education", "ls_energy", "ls_savings").forEach { flag ->
            val visible = visibleSections(mapOf(flag to yes())).map { it.id }
            assertTrue("$flag should open credits", visible.contains("credits"))
        }
        assertFalse(visibleSections(mapOf("ls_job" to yes())).map { it.id }.contains("credits"))
    }

    @Test
    fun `spouse questions appear only for joint filers`() {
        val single = questionsFor("about-you", mapOf("q_filing" to str("single"))).map { it.id }
        assertFalse(single.contains("q_spouse_age"))
        assertFalse(single.contains("q_lived_apart"))

        val joint = questionsFor("about-you", mapOf("q_filing" to str("mfj"))).map { it.id }
        assertTrue(joint.contains("q_spouse_age"))
        assertTrue(joint.contains("q_spouse_blind"))
        assertFalse(joint.contains("q_lived_apart"))

        val separate = questionsFor("about-you", mapOf("q_filing" to str("mfs"))).map { it.id }
        assertTrue(separate.contains("q_lived_apart"))
        assertFalse(separate.contains("q_spouse_age"))
    }

    @Test
    fun `tips and overtime are hidden for married filing separately`() {
        val job = questionsFor("income-job", mapOf("q_filing" to str("mfs"))).map { it.id }
        assertFalse(job.contains("q_tips"))
        assertFalse(job.contains("q_overtime"))

        val single = questionsFor("income-job", mapOf("q_filing" to str("single"))).map { it.id }
        assertTrue(single.contains("q_tips"))
        assertTrue(single.contains("q_overtime"))
    }

    @Test
    fun `itemized follow-ups unlock behind the itemize gate`() {
        assertEquals(
            listOf("q_itemize", "q_car_loan_interest"),
            questionsFor("deductions", emptyMap()).map { it.id },
        )
        val open = questionsFor("deductions", mapOf("q_itemize" to yes())).map { it.id }
        assertTrue(open.contains("q_salt"))
        assertTrue(open.contains("q_charitable"))
    }

    @Test
    fun `every question belongs to a real section`() {
        val sectionIds = SECTIONS.map { it.id }.toSet()
        QUESTION_BANK.forEach { q ->
            assertTrue("${q.id} has unknown section ${q.sectionId}", sectionIds.contains(q.sectionId))
        }
        assertEquals("question ids must be unique", QUESTION_BANK.size, QUESTION_BANK.map { it.id }.toSet().size)
    }

    @Test
    fun `progress counts only visible sections`() {
        // Five ungated sections; completing one is 20%.
        assertEquals(20, getProgress(listOf("about-you"), emptyMap()))
        // A completed section that isn't visible doesn't count.
        assertEquals(0, getProgress(listOf("income-job"), emptyMap()))
        assertEquals(0, getProgress(emptyList(), emptyMap()))
    }

    // MARK: - buildReturn coercions

    @Test
    fun `only numbers count as numbers`() {
        // A string answer in a numeric slot must not become income.
        val r = buildReturn(mapOf("q_wages" to str("50000")))
        assertEquals(0.0, r.income.w2.sumOf { it.box1Wages }, 0.001)
        assertTrue(r.income.w2.isEmpty())
    }

    @Test
    fun `age becomes a mid-year date of birth and zero stays blank`() {
        assertEquals("1985-06-15", buildReturn(mapOf("q_age" to num(40.0))).taxpayer.dateOfBirth)
        assertEquals("", buildReturn(emptyMap()).taxpayer.dateOfBirth)
        assertEquals("", buildReturn(mapOf("q_age" to num(-3.0))).taxpayer.dateOfBirth)
    }

    @Test
    fun `spouse exists only for joint and surviving-spouse filers`() {
        assertNotNull(buildReturn(mapOf("q_filing" to str("mfj"))).spouse)
        assertNotNull(buildReturn(mapOf("q_filing" to str("qss"))).spouse)
        assertNull(buildReturn(mapOf("q_filing" to str("mfs"))).spouse)
        assertNull(buildReturn(mapOf("q_filing" to str("single"))).spouse)
    }

    @Test
    fun `an unknown filing status falls back to single`() {
        assertEquals(FilingStatus.Single, buildReturn(mapOf("q_filing" to str("nonsense"))).filingStatus)
        assertEquals(FilingStatus.Hoh, buildReturn(mapOf("q_filing" to str("hoh"))).filingStatus)
    }

    @Test
    fun `dependents split into CTC children and other dependents`() {
        val r = buildReturn(mapOf("q_qual_children" to num(2.0), "q_other_deps" to num(1.0)))
        assertEquals(3, r.dependents.size)
        assertEquals(2, r.dependents.count { it.qualifiesForCTC })
        assertEquals(1, r.dependents.count { it.qualifiesForODC })
    }

    @Test
    fun `capital gains become proceeds and losses become basis`() {
        val gain = buildReturn(mapOf("q_ltcg" to num(5_000.0))).income.f1099B.first()
        assertEquals(5_000.0, gain.proceeds, 0.001)
        assertEquals(0.0, gain.costBasis, 0.001)
        assertTrue(gain.longTerm)

        val loss = buildReturn(mapOf("q_stcg" to num(-2_000.0))).income.f1099B.first()
        assertEquals(0.0, loss.proceeds, 0.001)
        assertEquals(2_000.0, loss.costBasis, 0.001)
        assertFalse(loss.longTerm)
    }

    @Test
    fun `ordinary dividends never fall below qualified`() {
        // Qualified dividends are a subset of ordinary; a user entering only
        // the qualified box must not shrink box 1a below it.
        val r = buildReturn(mapOf("q_qual_div" to num(3_000.0), "q_ord_div" to num(1_000.0)))
        val div = r.income.f1099Div.first()
        assertEquals(3_000.0, div.box1aOrdinaryDividends, 0.001)
        assertEquals(3_000.0, div.box1bQualifiedDividends, 0.001)
    }

    @Test
    fun `early retirement withdrawals carry distribution code 1`() {
        val normal = buildReturn(mapOf("q_retire_taxable" to num(10_000.0)))
        assertEquals("7", normal.income.f1099R.first().box7DistributionCode)

        val early = buildReturn(mapOf("q_retire_taxable" to num(10_000.0), "q_retire_early" to yes()))
        assertEquals("1", early.income.f1099R.first().box7DistributionCode)
    }

    @Test
    fun `care credit marks existing children before adding new ones`() {
        val r = buildReturn(
            mapOf(
                "q_qual_children" to num(2.0),
                "q_care_expenses" to num(6_000.0),
                "q_care_children" to num(2.0),
                "q_wages" to num(50_000.0),
            )
        )
        // No synthetic dependents: both CTC children were reused.
        assertEquals(2, r.dependents.size)
        assertEquals(2, r.dependents.count { it.qualifiesForCareCredit })
        assertTrue(r.credits.hasCareExpenses)
        assertEquals(50_000.0, r.credits.care.taxpayerEarnedIncome, 0.001)
    }

    @Test
    fun `care credit appends children beyond the CTC ones`() {
        val r = buildReturn(
            mapOf(
                "q_qual_children" to num(1.0),
                "q_care_expenses" to num(6_000.0),
                "q_care_children" to num(3.0),
            )
        )
        assertEquals(3, r.dependents.count { it.qualifiesForCareCredit })
        assertEquals(3, r.dependents.size)
    }

    @Test
    fun `care credit needs both expenses and children`() {
        val noChildren = buildReturn(mapOf("q_care_expenses" to num(6_000.0)))
        assertFalse(noChildren.credits.hasCareExpenses)
        val noExpenses = buildReturn(mapOf("q_care_children" to num(2.0)))
        assertFalse(noExpenses.credits.hasCareExpenses)
    }

    @Test
    fun `itemized entries are dropped when the itemize gate is off`() {
        val a = mapOf("q_salt" to num(9_000.0), "q_charitable" to num(4_000.0))
        assertEquals(0.0, buildReturn(a).itemized.stateLocalIncomeOrSalesTax, 0.001)

        val on = buildReturn(a + ("q_itemize" to yes()))
        assertEquals(9_000.0, on.itemized.stateLocalIncomeOrSalesTax, 0.001)
        assertEquals(4_000.0, on.itemized.charitableCash, 0.001)
    }

    @Test
    fun `state withholding is ignored without a state`() {
        val noState = buildReturn(mapOf("q_state_withholding" to num(2_000.0)))
        assertNull(noState.residency.state)
        assertEquals(0.0, noState.residency.stateWithholding, 0.001)

        val withState = buildReturn(
            mapOf("q_state" to str("CA"), "q_state_withholding" to num(2_000.0))
        )
        assertEquals(StateCode.CA, withState.residency.state)
        assertEquals(2_000.0, withState.residency.stateWithholding, 0.001)
    }

    @Test
    fun `hsa coverage maps from its select values`() {
        assertEquals(HsaCoverage.None, buildReturn(emptyMap()).adjustments.hsaCoverage)
        assertEquals(
            HsaCoverage.SelfOnly,
            buildReturn(mapOf("q_hsa_coverage" to str("self-only"))).adjustments.hsaCoverage,
        )
        assertEquals(
            HsaCoverage.Family,
            buildReturn(mapOf("q_hsa_coverage" to str("family"))).adjustments.hsaCoverage,
        )
    }

    @Test
    fun `prior-year figures stay null when unanswered`() {
        val blank = buildReturn(emptyMap()).payments
        assertNull(blank.priorYearTax)
        assertNull(blank.priorYearAgi)

        val filled = buildReturn(mapOf("q_prior_tax" to num(8_000.0), "q_prior_agi" to num(70_000.0))).payments
        assertEquals(8_000.0, filled.priorYearTax!!, 0.001)
        assertEquals(70_000.0, filled.priorYearAgi!!, 0.001)
    }

    // MARK: - End-to-end through the engine

    @Test
    fun `a simple W-2 interview produces a refund`() {
        val answers = mapOf(
            "ls_job" to yes(),
            "q_filing" to str("single"),
            "q_age" to num(35.0),
            "q_wages" to num(60_000.0),
            "q_withholding" to num(7_000.0),
        )
        val result = calculateFederalTax(buildReturn(answers))
        assertEquals(60_000.0, result.totalIncome, 0.001)
        assertEquals(15_750.0, result.standardDeduction, 0.001)
        assertEquals(44_250.0, result.taxableIncome, 0.001)
        assertFalse(result.owes)
        assertTrue(result.refundOrOwed > 0)
    }

    // MARK: - build1040Summary

    @Test
    fun `summary groups follow the 1040 order`() {
        val result = calculateFederalTax(
            buildReturn(mapOf("q_filing" to str("single"), "q_wages" to num(60_000.0)))
        )
        val summary = build1040Summary(result)
        assertEquals(2025, summary.taxYear)
        assertEquals("Single", summary.filingStatusLabel)
        assertEquals(listOf("Income", "Deductions", "Tax & credits", "Payments"), summary.groups.map { it.title })

        val income = summary.groups.first()
        assertEquals("Total income", income.lines[0].label)
        assertEquals("1040 line 9", income.lines[0].formRef)
        assertEquals(60_000.0, income.lines[0].amount, 0.001)
    }

    @Test
    fun `summary headline names a balance due when tax exceeds payments`() {
        val result = calculateFederalTax(
            buildReturn(mapOf("q_filing" to str("single"), "q_wages" to num(90_000.0)))
        )
        val summary = build1040Summary(result)
        assertTrue(summary.headline.owes)
        assertEquals("Estimated balance due", summary.headline.label)
        assertTrue(summary.headline.amount > 0)
    }

    @Test
    fun `unsupported states are left out of the summary`() {
        // Wyoming isn't modelled, so no state block is fabricated.
        val result = calculateFederalTax(
            buildReturn(mapOf("q_filing" to str("single"), "q_wages" to num(60_000.0), "q_state" to str("WY")))
        )
        assertNull(build1040Summary(result).state)
    }

    // MARK: - Suggestions

    @Test
    fun `suggestions nudge unchecked situations`() {
        val answers = mapOf("q_qual_children" to num(2.0))
        val result = calculateFederalTax(buildReturn(answers))
        val out = computeSuggestions(answers, result)
        assertTrue(out.any { it.contains("child or dependent care") })
        assertTrue(out.any { it.contains("American Opportunity Credit") })
    }

    @Test
    fun `a checked situation stops its nudge`() {
        val answers = mapOf("q_qual_children" to num(2.0), "ls_care" to yes())
        val result = calculateFederalTax(buildReturn(answers))
        assertFalse(computeSuggestions(answers, result).any { it.contains("child or dependent care") })
    }
}
