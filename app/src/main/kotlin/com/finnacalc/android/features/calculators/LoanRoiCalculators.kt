//
// LoanRoiCalculators.kt
//
// Loan + ROI calculator screens, ported from FinnaCalcIOS:
//   · LoanCalculatorView.swift — every mode solves for whichever piece is
//     missing, given the other three; the results heading and footer verb
//     follow the active mode.
//   · ROICalculatorView.swift
// (Pure math lives in CalcLogic.kt.)
//

package com.finnacalc.android.features.calculators

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// MARK: - Loan

@Composable
fun LoanCalculatorScreen() {
    var mode by rememberSaveable { mutableStateOf(LoanCalc.Mode.Payment) }
    var loanType by rememberSaveable { mutableStateOf("personal") }
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableStateOf("60") }
    // Remaining mode: how many monthly payments have been made so far.
    var paymentsMade by rememberSaveable { mutableStateOf("12") }
    // Initial mode: what the borrower pays each month, which is the input the
    // principal gets solved back out of.
    var monthlyPayment by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = LoanCalc.results(
        amount = amount.calcValue, rate = rate.calcValue, term = term.calcValue,
        paymentsMade = paymentsMade.calcValue,
        payment = monthlyPayment.calcValue, mode = mode,
    )

    // Names the field that is actually missing, and the one case that has no
    // answer at all: payments that never repay the principal.
    val invalidMessage = when (mode) {
        LoanCalc.Mode.Payment ->
            "Enter a loan amount and a term to see the payment."
        LoanCalc.Mode.Apr ->
            if (amount.calcValue > 0 && monthlyPayment.calcValue > 0 &&
                monthlyPayment.calcValue * term.calcValue < amount.calcValue
            ) {
                "Those payments never repay the loan: $term payments of " +
                    "${CalcFmt.currency(monthlyPayment.calcValue)} comes to less than the amount borrowed, " +
                    "so there is no rate that fits."
            } else {
                "Enter the loan amount, the monthly payment and the term to solve the APR."
            }
        LoanCalc.Mode.Initial ->
            "Enter the monthly payment, the rate and the term to see what was borrowed."
        LoanCalc.Mode.Remaining ->
            "Enter a loan amount and a term to see what is still owed."
    }

    CalculatorScreen(
        icon = CalculatorKind.Loan.icon,
        title = "Loan Calculator",
        description = "Solve for whichever piece you are missing: the payment, the APR, the initial amount, or what is still owed",
        verb = mode.verb,
        revealed = revealed,
        results = results,
        invalidMessage = invalidMessage,
        onCalculate = { revealed = true },
    ) {
        CalcModeTabsCard(
            mode, { mode = it },
            LoanCalc.Mode.entries.map { it to it.label },
        )
        CalcSectionCard("Loan details") {
            CalcGrid(
                {
                    CalcSelectField(
                        "Loan Type", loanType, { loanType = it },
                        listOf(
                            "personal" to "Personal Loan",
                            "auto" to "Auto Loan",
                            "mortgage" to "Mortgage",
                            "student" to "Student Loan",
                        ),
                    )
                },
                if (mode != LoanCalc.Mode.Initial) {
                    { CalcCurrencyField("Loan Amount", amount, { amount = it }) }
                } else null,
            )
        }
        CalcSectionCard("Repayment") {
            CalcGrid(
                if (mode != LoanCalc.Mode.Apr) {
                    { CalcPercentField("Interest Rate", rate, { rate = it }) }
                } else null,
                { CalcStepperField("Term", term, { term = it }, min = 6, unit = "mo") },
                if (mode == LoanCalc.Mode.Remaining) {
                    { CalcStepperField("Payments Made", paymentsMade, { paymentsMade = it }, min = 0, unit = "mo") }
                } else null,
                if (mode == LoanCalc.Mode.Initial || mode == LoanCalc.Mode.Apr) {
                    {
                        CalcCurrencyField(
                            "Monthly Payment", monthlyPayment, { monthlyPayment = it },
                            hint = if (mode == LoanCalc.Mode.Apr) {
                                "What you pay each month. With the amount borrowed and the term, this is enough to solve the rate you are actually paying."
                            } else {
                                "What you pay each month. With the rate and the term, this is enough to work back to what was originally borrowed."
                            },
                        )
                    }
                } else null,
            )
        }
    }
}

// MARK: - ROI

@Composable
fun RoiCalculatorScreen() {
    var initial by rememberSaveable { mutableStateOf("") }
    var final by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf("3") }
    var inflation by rememberSaveable { mutableStateOf("") }
    var taxRate by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = RoiCalc.results(
        initial = initial.calcValue,
        final = final.calcValue,
        years = years.calcValue,
        inflation = inflation.calcValue,
        taxRate = taxRate.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.Roi.icon,
        title = "ROI Calculator",
        description = "Calculate annualized return on investment with inflation and tax adjustments",
        verb = "ROI",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Investment") {
            CalcGrid(
                { CalcCurrencyField("Initial Investment", initial, { initial = it }) },
                { CalcCurrencyField("Final Value", final, { final = it }) },
            )
        }
        CalcSectionCard("Timeframe") {
            CalcGrid(
                { CalcStepperField("Investment Period", years, { years = it }, min = 1, unit = "yr") },
            )
        }
        CalcSectionCard("Adjustments") {
            CalcGrid(
                {
                    CalcPercentField(
                        "Annual Inflation Rate", inflation, { inflation = it },
                        hint = "Used to show your return in today’s purchasing power.",
                    )
                },
                { CalcPercentField("Tax Rate on Gains", taxRate, { taxRate = it }) },
            )
        }
    }
}
