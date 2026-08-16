//
// PersonalCalculators.kt
//
// Personal-finance calculator screens, ported from FinnaCalcIOS:
//   · EmergencyFundCalculatorView.swift
//   · RetirementCalculatorView.swift
//   · CompoundInterestCalculatorView.swift
// (Screens grouped per category here; iOS keeps one file per screen. Pure
// math lives in CalcLogic.kt.)
//

package com.finnacalc.android.features.calculators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

// MARK: - Emergency Fund

@Composable
fun EmergencyFundCalculatorScreen() {
    var monthlyExpenses by rememberSaveable { mutableStateOf("") }
    var currentSavings by rememberSaveable { mutableStateOf("") }
    var targetType by rememberSaveable { mutableStateOf("months") }
    var months by rememberSaveable { mutableStateOf("6") }
    var dollarAmount by rememberSaveable { mutableStateOf("") }
    var contribution by rememberSaveable { mutableStateOf("") }
    var apy by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = EmergencyFundCalc.results(
        monthlyExpenses = monthlyExpenses.calcValue,
        currentSavings = currentSavings.calcValue,
        targetType = targetType,
        months = months.calcValue,
        dollarAmount = dollarAmount.calcValue,
        contribution = contribution.calcValue,
        apy = apy.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.EmergencyFund.icon,
        title = "Emergency Fund Calculator",
        description = "Calculate how much you need in your emergency fund and track progress toward your goal",
        verb = "Emergency Fund",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Your situation") {
            CalcGrid(
                { CalcCurrencyField("Monthly Expenses", monthlyExpenses, { monthlyExpenses = it }) },
                { CalcCurrencyField("Current Emergency Savings", currentSavings, { currentSavings = it }) },
            )
        }
        CalcSectionCard("Your goal") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CalcSegmentedField(
                    "Target Type", targetType, { targetType = it },
                    listOf("months" to "Months", "dollar" to "$ Amount"),
                )
                CalcGrid(
                    if (targetType == "months") {
                        {
                            CalcStepperField(
                                "Number of Months", months, { months = it }, min = 1, unit = "mo",
                                hint = "How many months of expenses to save. 3–6 is typical; 6–12 if your income is variable.",
                            )
                        }
                    } else {
                        { CalcCurrencyField("Target Dollar Amount", dollarAmount, { dollarAmount = it }) }
                    },
                )
            }
        }
        CalcSectionCard("Savings plan") {
            CalcGrid(
                { CalcCurrencyField("Monthly Savings Contribution", contribution, { contribution = it }) },
                {
                    CalcPercentField(
                        "Savings Account APY", apy, { apy = it },
                        hint = "Your savings account’s annual percentage yield. Higher APY grows your fund faster.",
                    )
                },
            )
        }
    }
}

// MARK: - Retirement / 401(k)

@Composable
fun RetirementCalculatorScreen() {
    var currentAge by rememberSaveable { mutableStateOf("30") }
    var retirementAge by rememberSaveable { mutableStateOf("65") }
    var currentBalance by rememberSaveable { mutableStateOf("") }
    var annualSalary by rememberSaveable { mutableStateOf("") }
    var contributionPct by rememberSaveable { mutableStateOf("") }
    var employerMatchRate by rememberSaveable { mutableStateOf("") }
    var employerMatchCap by rememberSaveable { mutableStateOf("") }
    var annualReturn by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = RetirementCalc.results(
        currentAge = currentAge.calcValue,
        retirementAge = retirementAge.calcValue,
        currentBalance = currentBalance.calcValue,
        annualSalary = annualSalary.calcValue,
        contributionPct = contributionPct.calcValue,
        employerMatchRate = employerMatchRate.calcValue,
        employerMatchCapPct = employerMatchCap.calcValue,
        annualReturn = annualReturn.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.Retirement.icon,
        title = "Retirement / 401(k) Calculator",
        description = "Project your 401(k) balance at retirement, including employer match and compound growth",
        verb = "Retirement Balance",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Your info") {
            CalcGrid(
                { CalcStepperField("Current Age", currentAge, { currentAge = it }, min = 16, unit = "yrs") },
                { CalcStepperField("Retirement Age", retirementAge, { retirementAge = it }, min = 17, unit = "yrs") },
            )
        }
        CalcSectionCard("Contributions") {
            CalcGrid(
                { CalcCurrencyField("Current 401(k) Balance", currentBalance, { currentBalance = it }) },
                { CalcCurrencyField("Annual Salary", annualSalary, { annualSalary = it }) },
                {
                    CalcPercentField(
                        "Your Contribution", contributionPct, { contributionPct = it },
                        hint = "The percent of your salary you contribute each year.",
                    )
                },
                {
                    CalcPercentField(
                        "Employer Match Rate", employerMatchRate, { employerMatchRate = it },
                        hint = "How much of your contribution your employer matches — e.g. 50% means they add 50¢ per dollar you contribute.",
                    )
                },
                {
                    CalcPercentField(
                        "Employer Match Cap", employerMatchCap, { employerMatchCap = it },
                        hint = "The most your employer matches, as a percent of salary — e.g. a 6% cap stops matching contributions beyond 6% of pay.",
                    )
                },
            )
        }
        CalcSectionCard("Growth") {
            CalcGrid(
                { CalcPercentField("Expected Annual Return", annualReturn, { annualReturn = it }) },
            )
        }
    }
}

// MARK: - Compound Interest

@Composable
fun CompoundInterestCalculatorScreen() {
    var initialDeposit by rememberSaveable { mutableStateOf("") }
    var monthlyContribution by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf("10") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = CompoundInterestCalc.results(
        initialDeposit = initialDeposit.calcValue,
        monthlyContribution = monthlyContribution.calcValue,
        annualRate = rate.calcValue,
        years = years.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.CompoundInterest.icon,
        title = "Compound Interest Calculator",
        description = "See how your savings grow over time with compound interest and monthly contributions",
        verb = "Growth",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Starting point") {
            CalcGrid(
                { CalcCurrencyField("Initial Deposit", initialDeposit, { initialDeposit = it }) },
                { CalcCurrencyField("Monthly Contribution", monthlyContribution, { monthlyContribution = it }) },
            )
        }
        CalcSectionCard("Growth") {
            CalcGrid(
                { CalcPercentField("Annual Interest Rate", rate, { rate = it }) },
                { CalcStepperField("Time Period", years, { years = it }, min = 1, unit = "yr") },
            )
        }
    }
}
