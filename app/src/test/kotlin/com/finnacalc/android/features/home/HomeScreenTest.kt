package com.finnacalc.android.features.home

import com.finnacalc.android.app.CrossTabNavigation
import com.finnacalc.android.features.calculators.CalculatorKind
import com.finnacalc.android.features.education.EducationContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home dashboard's pure helpers — the parts that decide what it says. */
class HomeScreenTest {

    // MARK: - Greeting

    @Test
    fun `the greeting follows the clock`() {
        assertEquals("Good morning", greeting(5))
        assertEquals("Good morning", greeting(11))
        assertEquals("Good afternoon", greeting(12))
        assertEquals("Good afternoon", greeting(16))
        assertEquals("Good evening", greeting(17))
        assertEquals("Good evening", greeting(23))
        // Small hours are still evening, not morning.
        assertEquals("Good evening", greeting(2))
    }

    // MARK: - Name

    @Test
    fun `a full name greets by its first token`() {
        assertEquals("Alex", firstName("Alex Rivera"))
        assertEquals("Alex", firstName("Alex"))
    }

    @Test
    fun `an email greets by the part before the at sign`() {
        // Otherwise the app says "Good morning, fm0291601@gmail.com".
        assertEquals("felipe", firstName("felipe@example.com"))
    }

    @Test
    fun `a name containing an email keeps its first token`() {
        assertEquals("Alex", firstName("Alex alex@example.com"))
    }

    @Test
    fun `an empty name degrades quietly`() {
        assertEquals("", firstName(""))
    }

    // MARK: - Lesson of the week

    @Test
    fun `every weekly lesson points at a real education topic`() {
        val topicIds = EducationContent.topics.map { it.first }.toSet()
        weeklyLessons.forEach { (topicId, eyebrow, title) ->
            assertTrue("$topicId isn't a topic", topicIds.contains(topicId))
            assertTrue(eyebrow.isNotBlank())
            assertTrue(title.isNotBlank())
        }
    }

    @Test
    fun `the rotation is deterministic and covers every lesson`() {
        // Same week, same lesson, for everyone — no backend needed.
        val picks = (1..53).map { week -> weeklyLessons[kotlin.math.abs(week) % weeklyLessons.size] }
        assertEquals(weeklyLessons.toSet(), picks.toSet())
        assertEquals(picks[0], (1..53).map { weeklyLessons[it % weeklyLessons.size] }[0])
    }

    // MARK: - Calculator titles

    @Test
    fun `calculator titles shed their trailing noun`() {
        assertEquals("Loan", shortTitle(CalculatorKind.Loan))
        CalculatorKind.entries.forEach {
            val short = shortTitle(it)
            assertFalse("${it.title} kept its suffix", short.endsWith(" Calculator"))
            assertFalse(short.endsWith(" Estimator"))
            assertFalse(short.endsWith(" Projector"))
            assertTrue(short.isNotBlank())
        }
    }

    // MARK: - Cross-tab launch intents

    @Test
    fun `a page request rides along with the tab switch`() {
        CrossTabNavigation.pendingBudgetingPage = null
        CrossTabNavigation.request("budgeting", page = "goals")
        assertEquals("goals", CrossTabNavigation.pendingBudgetingPage)
    }

    @Test
    fun `an investing tab request is kept separately from the budgeting one`() {
        CrossTabNavigation.pendingBudgetingPage = null
        CrossTabNavigation.pendingInvestingTab = null
        CrossTabNavigation.request("investing", tabName = "portfolio")
        assertEquals("portfolio", CrossTabNavigation.pendingInvestingTab)
        assertNull(CrossTabNavigation.pendingBudgetingPage)
    }

    @Test
    fun `a plain tab switch asks for no particular page`() {
        CrossTabNavigation.pendingBudgetingPage = null
        CrossTabNavigation.pendingInvestingTab = null
        CrossTabNavigation.request("education")
        assertNull(CrossTabNavigation.pendingBudgetingPage)
        assertNull(CrossTabNavigation.pendingInvestingTab)
    }
}
