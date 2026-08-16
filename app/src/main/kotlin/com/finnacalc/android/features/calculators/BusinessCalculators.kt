//
// BusinessCalculators.kt
//
// Business calculator screens, ported from FinnaCalcIOS:
//   · BreakEvenCalculatorView.swift
//   · StartupCostCalculatorView.swift
//   · CashFlowCalculatorView.swift
//   · PricingCalculatorView.swift
//   · EmployeeContractorCalculatorView.swift
//   · ProfitMarginCalculatorView.swift
// (Screens grouped per category here; iOS keeps one file per screen. Pure
// math lives in CalcLogic.kt. The Startup industry template is decorative
// only — it doesn't feed the formula, matching the literal spec.)
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

// MARK: - Break-Even

@Composable
fun BreakEvenCalculatorScreen() {
    var fixedCosts by rememberSaveable { mutableStateOf("") }
    var variableCost by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var businessType by rememberSaveable { mutableStateOf("single") }
    var targetMargin by rememberSaveable { mutableStateOf("") }
    var seasonality by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = BreakEvenCalc.results(
        fixedCosts = fixedCosts.calcValue,
        variableCost = variableCost.calcValue,
        price = price.calcValue,
        businessType = businessType,
        targetMargin = targetMargin.calcValue,
        seasonality = seasonality.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.BreakEven.icon,
        title = "Break-Even Point Calculator",
        description = "Find out exactly how many units you need to sell to cover all costs and reach profitability",
        verb = "Break-Even Point",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Your costs") {
            CalcGrid(
                {
                    CalcCurrencyField(
                        "Fixed Costs per Month", fixedCosts, { fixedCosts = it },
                        hint = "Rent, salaries, insurance — costs that stay the same regardless of sales volume.",
                    )
                },
                {
                    CalcCurrencyField(
                        "Variable Cost per Unit", variableCost, { variableCost = it },
                        hint = "Materials, packaging, shipping — costs that scale with each unit sold.",
                    )
                },
            )
        }
        CalcSectionCard("Pricing") {
            CalcGrid(
                { CalcCurrencyField("Selling Price per Unit", price, { price = it }) },
                {
                    CalcSelectField(
                        "Business Type", businessType, { businessType = it },
                        listOf("single" to "Single Product", "multi" to "Multiple Products"),
                    )
                },
            )
        }
        CalcSectionCard("Targets") {
            CalcGrid(
                {
                    CalcPercentField(
                        "Target Net Profit Margin", targetMargin, { targetMargin = it },
                        hint = "The profit margin you want after covering all costs, as a percent of revenue.",
                    )
                },
                {
                    CalcPercentField(
                        "Seasonality Adjustment", seasonality, { seasonality = it },
                        hint = "Extra cushion for slow seasons — 10% adds a 10% buffer to your break-even target.",
                    )
                },
            )
        }
    }
}

// MARK: - Startup Cost

@Composable
fun StartupCostCalculatorScreen() {
    var template by rememberSaveable { mutableStateOf("retail") }
    var setupCosts by rememberSaveable { mutableStateOf("") }
    var operatingCosts by rememberSaveable { mutableStateOf("") }
    var runwayMonths by rememberSaveable { mutableStateOf("6") }
    var funding by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = StartupCostCalc.results(
        setupCosts = setupCosts.calcValue,
        operatingCosts = operatingCosts.calcValue,
        runwayMonths = runwayMonths.calcValue,
        funding = funding.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.StartupCost.icon,
        title = "Startup Cost Estimator",
        description = "Estimate total startup costs with industry templates and funding gap analysis",
        verb = "Startup Cost",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Setup") {
            CalcGrid(
                {
                    CalcSelectField(
                        "Industry Template", template, { template = it },
                        listOf(
                            "retail" to "Retail",
                            "restaurant" to "Restaurant",
                            "saas" to "SaaS",
                            "service" to "Service Business",
                        ),
                    )
                },
                { CalcCurrencyField("One-Time Setup Costs", setupCosts, { setupCosts = it }) },
            )
        }
        CalcSectionCard("Operating") {
            CalcGrid(
                { CalcCurrencyField("Monthly Operating Costs", operatingCosts, { operatingCosts = it }) },
                {
                    CalcStepperField(
                        "Months of Runway Needed", runwayMonths, { runwayMonths = it }, min = 1, unit = "mo",
                        hint = "How many months of operating costs to hold in reserve after launch.",
                    )
                },
            )
        }
        CalcSectionCard("Funding") {
            CalcGrid(
                { CalcCurrencyField("Available Funding", funding, { funding = it }) },
            )
        }
    }
}

// MARK: - Cash Flow

@Composable
fun CashFlowCalculatorScreen() {
    var startingBalance by rememberSaveable { mutableStateOf("") }
    var monthlyRevenue by rememberSaveable { mutableStateOf("") }
    var monthlyExpenses by rememberSaveable { mutableStateOf("") }
    var growthRate by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("12") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = CashFlowCalc.results(
        startingBalance = startingBalance.calcValue,
        monthlyRevenue = monthlyRevenue.calcValue,
        monthlyExpenses = monthlyExpenses.calcValue,
        growthRate = growthRate.calcValue,
        period = period.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.CashFlow.icon,
        title = "Cash Flow Projector",
        description = "Project your business cash flow over time with growth rate modeling",
        verb = "Cash Flow",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Starting point") {
            CalcGrid(
                { CalcCurrencyField("Starting Cash Balance", startingBalance, { startingBalance = it }) },
            )
        }
        CalcSectionCard("Monthly activity") {
            CalcGrid(
                { CalcCurrencyField("Monthly Revenue", monthlyRevenue, { monthlyRevenue = it }) },
                { CalcCurrencyField("Monthly Expenses", monthlyExpenses, { monthlyExpenses = it }) },
            )
        }
        CalcSectionCard("Projection") {
            CalcGrid(
                {
                    CalcPercentField(
                        "Monthly Growth Rate", growthRate, { growthRate = it },
                        hint = "Expected month-over-month change in revenue. Use a negative number to model a decline.",
                    )
                },
                { CalcStepperField("Projection Period", period, { period = it }, min = 1, unit = "mo") },
            )
        }
    }
}

// MARK: - Pricing

@Composable
fun PricingCalculatorScreen() {
    var cost by rememberSaveable { mutableStateOf("") }
    var competitorPrice by rememberSaveable { mutableStateOf("") }
    var positioning by rememberSaveable { mutableStateOf("match") }
    var targetMargin by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = PricingCalc.results(
        cost = cost.calcValue,
        competitorPrice = competitorPrice.calcValue,
        positioning = positioning,
        targetMargin = targetMargin.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.Pricing.icon,
        title = "Pricing Calculator",
        description = "Set the right price for your products and services with competitive analysis",
        verb = "Price",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Your costs") {
            CalcGrid(
                { CalcCurrencyField("Cost per Unit", cost, { cost = it }) },
            )
        }
        CalcSectionCard("Market") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CalcGrid(
                    { CalcCurrencyField("Competitor Price", competitorPrice, { competitorPrice = it }) },
                )
                CalcSegmentedField(
                    "Desired Positioning", positioning, { positioning = it },
                    listOf("value" to "Value", "match" to "Match", "premium" to "Premium"),
                )
            }
        }
        CalcSectionCard("Target") {
            CalcGrid(
                { CalcPercentField("Target Profit Margin", targetMargin, { targetMargin = it }) },
            )
        }
    }
}

// MARK: - Employee vs Contractor

@Composable
fun EmployeeContractorCalculatorScreen() {
    var salary by rememberSaveable { mutableStateOf("") }
    var benefits by rememberSaveable { mutableStateOf("") }
    var hourlyRate by rememberSaveable { mutableStateOf("") }
    var hoursPerYear by rememberSaveable { mutableStateOf("2000") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = EmployeeContractorCalc.results(
        salary = salary.calcValue,
        benefits = benefits.calcValue,
        hourlyRate = hourlyRate.calcValue,
        hoursPerYear = hoursPerYear.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.EmployeeContractor.icon,
        title = "Employee vs Contractor Calculator",
        description = "Compare the true total cost of hiring employees versus independent contractors",
        verb = "Comparison",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Employee cost") {
            CalcGrid(
                { CalcCurrencyField("Annual Salary", salary, { salary = it }) },
                {
                    CalcPercentField(
                        "Benefits & Overhead", benefits, { benefits = it },
                        hint = "Payroll tax, healthcare, and other costs on top of salary — typically 20–35%.",
                    )
                },
            )
        }
        CalcSectionCard("Contractor cost") {
            CalcGrid(
                { CalcCurrencyField("Contractor Hourly Rate", hourlyRate, { hourlyRate = it }) },
                { CalcStepperField("Hours per Year", hoursPerYear, { hoursPerYear = it }, min = 100, unit = "hrs") },
            )
        }
    }
}

// MARK: - Profit Margin

@Composable
fun ProfitMarginCalculatorScreen() {
    var revenue by rememberSaveable { mutableStateOf("") }
    var cogs by rememberSaveable { mutableStateOf("") }
    var opex by rememberSaveable { mutableStateOf("") }
    var taxRate by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }

    val results = ProfitMarginCalc.results(
        revenue = revenue.calcValue,
        cogs = cogs.calcValue,
        opex = opex.calcValue,
        taxRate = taxRate.calcValue,
    )

    CalculatorScreen(
        icon = CalculatorKind.ProfitMargin.icon,
        title = "Profit Margin Calculator",
        description = "Calculate gross, operating, and net profit margins with industry benchmarks",
        verb = "Profit Margin",
        revealed = revealed,
        results = results,
        onCalculate = { revealed = true },
    ) {
        CalcSectionCard("Revenue & costs") {
            CalcGrid(
                { CalcCurrencyField("Revenue", revenue, { revenue = it }) },
                { CalcCurrencyField("Cost of Goods Sold", cogs, { cogs = it }) },
            )
        }
        CalcSectionCard("Expenses & tax") {
            CalcGrid(
                { CalcCurrencyField("Operating Expenses", opex, { opex = it }) },
                {
                    CalcPercentField(
                        "Tax Rate", taxRate, { taxRate = it },
                        hint = "Applied to net profit to estimate your after-tax take-home.",
                    )
                },
            )
        }
    }
}
