//
// CalculatorCatalog.kt
//
// Port of iOS Features/Calculators/CalculatorCatalog.swift — the calculator
// catalog shown on the Home hub (same titles, descriptions, order, and
// categories as the web `calculators` array). Tax Calculator is intentionally
// omitted here; it ships with the tax engine in Phase 6.
//
// SF Symbols map to the closest Material icons; the catalog stays the only
// source of truth, so the icon on the hub row is the icon on the page it opens.
//

package com.finnacalc.android.features.calculators

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SsidChart
import androidx.compose.material.icons.outlined.Support
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class CalculatorKind(
    val title: String,
    val summary: String,
    val category: String,
    val icon: ImageVector,
) {
    EmergencyFund(
        "Emergency Fund Calculator",
        "Calculate how much you need in your emergency fund and track progress toward your goal",
        "Personal Finance",
        Icons.Outlined.Support,  // a safety net (lifebuoy)
    ),
    BreakEven(
        "Break-Even Point Calculator",
        "Find out exactly how many units you need to sell to cover all costs and reach profitability",
        "Business",
        Icons.Outlined.Balance,  // revenue == costs
    ),
    StartupCost(
        "Startup Cost Estimator",
        "Estimate total startup costs with industry templates and funding gap analysis",
        "Business",
        Icons.Outlined.Apartment,
    ),
    CashFlow(
        "Cash Flow Projector",
        "Project your business cash flow over time with growth rate modeling",
        "Business",
        Icons.Outlined.SsidChart,  // projected over time
    ),
    Loan(
        "Loan Calculator",
        "Calculate payments, true APR, and the initial and remaining loan amount for any loan type",
        "Loans",
        Icons.Outlined.Payments,
    ),
    Pricing(
        "Pricing Calculator",
        "Set the right price for your products and services with competitive analysis",
        "Business",
        Icons.Outlined.Sell,  // a price tag
    ),
    Roi(
        "ROI Calculator",
        "Calculate annualized return on investment with inflation and tax adjustments",
        "Investment",
        Icons.Outlined.PieChart,
    ),
    EmployeeContractor(
        "Employee vs Contractor Calculator",
        "Compare the true total cost of hiring employees versus independent contractors",
        "Business",
        Icons.Outlined.People,
    ),
    ProfitMargin(
        "Profit Margin Calculator",
        "Calculate gross, operating, and net profit margins with industry benchmarks",
        "Business",
        Icons.Outlined.Percent,  // a margin is a %
    ),
    Retirement(
        "Retirement / 401(k) Calculator",
        "Project your 401(k) balance at retirement, including employer match and growth",
        "Personal Finance",
        Icons.AutoMirrored.Outlined.DirectionsWalk,
    ),
    CompoundInterest(
        "Compound Interest Calculator",
        "See how your savings grow over time with compound interest and contributions",
        "Personal Finance",
        Icons.AutoMirrored.Outlined.TrendingUp,  // the growth curve
    ),
}

/** Dispatch a calculator kind to its screen. */
@Composable
fun CalculatorDestination(kind: CalculatorKind) {
    when (kind) {
        CalculatorKind.EmergencyFund -> EmergencyFundCalculatorScreen()
        CalculatorKind.BreakEven -> BreakEvenCalculatorScreen()
        CalculatorKind.StartupCost -> StartupCostCalculatorScreen()
        CalculatorKind.CashFlow -> CashFlowCalculatorScreen()
        CalculatorKind.Loan -> LoanCalculatorScreen()
        CalculatorKind.Pricing -> PricingCalculatorScreen()
        CalculatorKind.Roi -> RoiCalculatorScreen()
        CalculatorKind.EmployeeContractor -> EmployeeContractorCalculatorScreen()
        CalculatorKind.ProfitMargin -> ProfitMarginCalculatorScreen()
        CalculatorKind.Retirement -> RetirementCalculatorScreen()
        CalculatorKind.CompoundInterest -> CompoundInterestCalculatorScreen()
    }
}
