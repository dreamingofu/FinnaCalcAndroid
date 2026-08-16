//
// CalcLogic.kt
//
// Pure calculator math, ported 1:1 from the per-screen `enum XxxCalc` blocks
// in FinnaCalcIOS Features/Calculators (which themselves port the web spec's
// calc-data.js `compute()` functions). Kept UI-free so unit tests cover the
// formulas directly.
//

package com.finnacalc.android.features.calculators

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// MARK: - Emergency Fund

object EmergencyFundCalc {
    fun results(
        monthlyExpenses: Double,
        currentSavings: Double,
        targetType: String,
        months: Double,
        dollarAmount: Double,
        contribution: Double,
        apy: Double,
    ): List<CalcResultMetric>? {
        val target = if (targetType == "months") monthlyExpenses * months else dollarAmount
        if (target <= 0) return null

        val remaining = max(0.0, target - currentSavings)
        val progress = min(100.0, (currentSavings / target) * 100)

        var bal = currentSavings
        var m = 0
        val rMonthly = apy / 100 / 12
        val monthsToGoal: Int? = when {
            bal >= target -> 0
            contribution <= 0 -> null  // Infinity — no contribution means the goal is never reached
            else -> {
                while (bal < target && m < 1200) {
                    bal = bal * (1 + rMonthly) + contribution
                    m += 1
                }
                if (bal >= target) m else null
            }
        }

        return listOf(
            CalcResultMetric("Target emergency fund", CalcFmt.currency(target), CalcAccent.Green),
            CalcResultMetric("Still needed", CalcFmt.currency(remaining), CalcAccent.Red),
            CalcResultMetric(
                "Months to reach goal",
                monthsToGoal?.let { "$it mo" } ?: "Add a contribution",
                CalcAccent.Blue,
            ),
            CalcResultMetric("Progress today", CalcFmt.percent(progress, 0), CalcAccent.Orange),
        )
    }
}

// MARK: - Break-Even

object BreakEvenCalc {
    fun results(
        fixedCosts: Double,
        variableCost: Double,
        price: Double,
        businessType: String,
        targetMargin: Double,
        seasonality: Double,
    ): List<CalcResultMetric>? {
        val vc = if (businessType == "multi") variableCost * 1.05 else variableCost
        val cm = price - vc
        if (cm <= 0 || fixedCosts <= 0) return null

        val beUnits = fixedCosts / cm
        val beRevenue = beUnits * price
        val denom = cm - (targetMargin / 100) * price
        val targetUnits: Double? = if (denom > 0) fixedCosts / denom else null
        val seasonUnits = beUnits * (1 + seasonality / 100)

        return listOf(
            CalcResultMetric("Break-even units / month", "${CalcFmt.int(ceil(beUnits))} units", CalcAccent.Green),
            CalcResultMetric("Break-even revenue", CalcFmt.currency(beRevenue), CalcAccent.Blue),
            CalcResultMetric(
                "Units for target margin",
                targetUnits?.let { "${CalcFmt.int(ceil(it))} units" } ?: "Not achievable",
                if (targetUnits != null) CalcAccent.Orange else CalcAccent.Red,
            ),
            CalcResultMetric("Seasonality-adjusted units", "${CalcFmt.int(ceil(seasonUnits))} units", CalcAccent.Purple),
        )
    }
}

// MARK: - Startup Cost

object StartupCostCalc {
    fun results(
        setupCosts: Double,
        operatingCosts: Double,
        runwayMonths: Double,
        funding: Double,
    ): List<CalcResultMetric>? {
        if (setupCosts <= 0 && operatingCosts <= 0) return null
        val total = setupCosts + operatingCosts * runwayMonths
        val gap = total - funding
        return listOf(
            CalcResultMetric("Total startup cost", CalcFmt.currency(total), CalcAccent.Green),
            CalcResultMetric(
                if (gap > 0) "Funding gap" else "Funding surplus",
                CalcFmt.currency(abs(gap)),
                if (gap > 0) CalcAccent.Red else CalcAccent.Blue,
            ),
            CalcResultMetric("Runway reserve needed", CalcFmt.currency(operatingCosts * runwayMonths), CalcAccent.Purple),
        )
    }
}

// MARK: - Cash Flow

object CashFlowCalc {
    fun results(
        startingBalance: Double,
        monthlyRevenue: Double,
        monthlyExpenses: Double,
        growthRate: Double,
        period: Double,
    ): List<CalcResultMetric>? {
        if (monthlyRevenue <= 0 && monthlyExpenses <= 0) return null
        var bal = startingBalance
        var rev = monthlyRevenue
        var low = bal
        var netTotal = 0.0
        repeat(period.toInt()) {
            val net = rev - monthlyExpenses
            bal += net
            netTotal += net
            if (bal < low) low = bal
            rev *= (1 + growthRate / 100)
        }
        return listOf(
            CalcResultMetric("Ending cash balance", CalcFmt.currency(bal), CalcAccent.Green),
            CalcResultMetric("Total net cash flow", CalcFmt.currency(netTotal), CalcAccent.Blue),
            CalcResultMetric(
                "Lowest projected balance", CalcFmt.currency(low),
                if (low < 0) CalcAccent.Red else CalcAccent.Purple,
            ),
        )
    }
}

// MARK: - Loan

object LoanCalc {
    enum class Mode(val label: String) {
        Payment("Payment"), Apr("APR"), Remaining("Remaining"), Initial("Initial");

        /** Heading/footer verb: "Your {verb} Calculation" / "Calculate {verb}". */
        val verb: String get() = label
    }

    fun results(
        amount: Double, rate: Double, term: Double,
        paymentsMade: Double, payment: Double, mode: Mode,
    ): List<CalcResultMetric>? {
        val r = rate / 100 / 12
        val n = term
        if (n <= 0) return null

        // APR is the rate solved from the other three, the same way Payment
        // solves the payment and Initial solves the principal.
        if (mode == Mode.Apr) {
            if (amount <= 0 || payment <= 0) return null
            val apr = solveRate(amount, payment, n) ?: return null
            val total = payment * n
            return listOf(
                CalcResultMetric("APR", CalcFmt.percent(apr, 2), CalcAccent.Green),
                CalcResultMetric("Monthly payment", CalcFmt.currency(payment, 2), CalcAccent.Blue),
                CalcResultMetric("Total paid", CalcFmt.currency(total, 2), CalcAccent.Purple),
                CalcResultMetric("Total interest", CalcFmt.currency(total - amount, 2), CalcAccent.Red),
            )
        }

        // Initial runs backwards from the other three: the principal is the
        // unknown, so it can't take the shared `amount > 0` guard below.
        if (mode == Mode.Initial) {
            if (payment <= 0) return null
            // Present value of the payment stream, which is what the lender
            // handed over on day one.
            val principal = if (r == 0.0) payment * n else payment * (1 - (1 + r).pow(-n)) / r
            val total = payment * n
            return listOf(
                CalcResultMetric("Initial loan amount", CalcFmt.currency(principal, 2), CalcAccent.Purple),
                CalcResultMetric("Monthly payment", CalcFmt.currency(payment, 2), CalcAccent.Green),
                CalcResultMetric("Total paid", CalcFmt.currency(total, 2), CalcAccent.Blue),
                CalcResultMetric("Total interest", CalcFmt.currency(total - principal, 2), CalcAccent.Red),
            )
        }

        val p = amount
        if (p <= 0) return null
        val pmt = if (r == 0.0) p / n else (p * r * (1 + r).pow(n)) / ((1 + r).pow(n) - 1)

        return when (mode) {
            Mode.Apr, Mode.Initial -> null  // handled above
            Mode.Remaining -> {
                val k = min(max(floor(paymentsMade), 0.0), n)
                val remaining = if (r == 0.0) p - pmt * k else p * (1 + r).pow(k) - pmt * (((1 + r).pow(k) - 1) / r)
                val remainingClamped = max(0.0, remaining)
                listOf(
                    CalcResultMetric("Initial amount", CalcFmt.currency(p, 2), CalcAccent.Purple),
                    CalcResultMetric(
                        "Remaining balance (${CalcFmt.int(k)} of ${CalcFmt.int(n)} mo)",
                        CalcFmt.currency(remainingClamped, 2), CalcAccent.Green,
                    ),
                    CalcResultMetric("Principal paid off", CalcFmt.currency(p - remainingClamped, 2), CalcAccent.Blue),
                    CalcResultMetric("Payments made", "${CalcFmt.int(k)} of ${CalcFmt.int(n)}", CalcAccent.Orange),
                )
            }
            Mode.Payment -> {
                val total = pmt * n
                listOf(
                    CalcResultMetric("Monthly payment", CalcFmt.currency(pmt, 2), CalcAccent.Green),
                    CalcResultMetric("Total paid", CalcFmt.currency(total, 2), CalcAccent.Blue),
                    CalcResultMetric("Total interest", CalcFmt.currency(total - p, 2), CalcAccent.Red),
                    CalcResultMetric("Principal financed", CalcFmt.currency(p, 2), CalcAccent.Purple),
                )
            }
        }
    }

    /**
     * Solves the annualised rate (in %) at which the payment stream's present
     * value equals the amount borrowed, by bisection. Present value is
     * strictly decreasing in the rate, so the root is unique; 200 halvings of
     * [0, 1] monthly converge far past display precision.
     *
     * Returns null when the payments never repay the principal, which has no
     * solution: at 0% the borrower would still owe money at the end.
     */
    fun solveRate(principal: Double, monthlyPayment: Double, months: Double): Double? {
        if (principal <= 0 || monthlyPayment <= 0 || monthlyPayment * months < principal) return null
        // Payments that exactly repay the principal are 0% financing, which is
        // common on promotional auto loans. Bisection can't return it: zero is
        // its lower bound, so the search would never reach it.
        if (monthlyPayment * months - principal < 0.005) return 0.0
        fun presentValue(i: Double): Double =
            if (i == 0.0) monthlyPayment * months else monthlyPayment * (1 - (1 + i).pow(-months)) / i
        var lo = 0.0
        var hi = 1.0
        repeat(200) {
            val mid = (lo + hi) / 2
            if (presentValue(mid) > principal) lo = mid else hi = mid
        }
        return (lo + hi) / 2 * 1200
    }
}

// MARK: - Pricing

object PricingCalc {
    fun results(
        cost: Double,
        competitorPrice: Double,
        positioning: String,
        targetMargin: Double,
    ): List<CalcResultMetric>? {
        if (cost <= 0) return null
        val minPrice = if (targetMargin < 100) cost / (1 - targetMargin / 100) else cost * 2
        var posPrice = competitorPrice
        if (positioning == "premium") {
            posPrice = competitorPrice * 1.1
        } else if (positioning == "value") {
            posPrice = competitorPrice * 0.9
        }
        val recommended = max(minPrice, posPrice)
        return listOf(
            CalcResultMetric("Recommended price", CalcFmt.currency(recommended, 2), CalcAccent.Green),
            CalcResultMetric("Minimum viable price", CalcFmt.currency(minPrice, 2), CalcAccent.Blue),
            CalcResultMetric("Profit per unit", CalcFmt.currency(recommended - cost, 2), CalcAccent.Purple),
        )
    }
}

// MARK: - ROI

object RoiCalc {
    fun results(
        initial: Double,
        final: Double,
        years: Double,
        inflation: Double,
        taxRate: Double,
    ): List<CalcResultMetric>? {
        if (initial <= 0) return null
        val totalReturn = final - initial
        val totalROI = (totalReturn / initial) * 100
        val annualized = ((final / initial).pow(1 / max(years, 0.1)) - 1) * 100
        val afterTax = totalReturn * (1 - taxRate / 100)
        val real = annualized - inflation
        return listOf(
            CalcResultMetric("Total ROI", CalcFmt.percent(totalROI, 1), CalcAccent.Green),
            CalcResultMetric("Annualized return", CalcFmt.percent(annualized, 1), CalcAccent.Blue),
            CalcResultMetric("Inflation-adjusted return", CalcFmt.percent(real, 1), CalcAccent.Orange),
            CalcResultMetric("After-tax gain", CalcFmt.currency(afterTax, 2), CalcAccent.Purple),
        )
    }
}

// MARK: - Employee vs Contractor

object EmployeeContractorCalc {
    fun results(
        salary: Double,
        benefits: Double,
        hourlyRate: Double,
        hoursPerYear: Double,
    ): List<CalcResultMetric>? {
        if (salary <= 0 && hourlyRate <= 0) return null
        val empCost = salary * (1 + benefits / 100)
        val contCost = hourlyRate * hoursPerYear
        val diff = empCost - contCost
        return listOf(
            CalcResultMetric("Lower total cost", if (diff > 0) "Contractor" else "Employee", CalcAccent.Green),
            CalcResultMetric("Total employee cost", CalcFmt.currency(empCost), CalcAccent.Blue),
            CalcResultMetric("Total contractor cost", CalcFmt.currency(contCost), CalcAccent.Purple),
            CalcResultMetric("Annual savings", CalcFmt.currency(abs(diff)), CalcAccent.Orange),
        )
    }
}

// MARK: - Profit Margin

object ProfitMarginCalc {
    fun results(
        revenue: Double,
        cogs: Double,
        opex: Double,
        taxRate: Double,
    ): List<CalcResultMetric>? {
        if (revenue <= 0) return null
        val gross = revenue - cogs
        val grossM = gross / revenue * 100
        val operating = gross - opex
        val operatingM = operating / revenue * 100
        val net = operating * (1 - taxRate / 100)
        val netM = net / revenue * 100
        return listOf(
            CalcResultMetric("Net margin", CalcFmt.percent(netM, 1), CalcAccent.Green),
            CalcResultMetric("Gross margin", CalcFmt.percent(grossM, 1), CalcAccent.Blue),
            CalcResultMetric("Operating margin", CalcFmt.percent(operatingM, 1), CalcAccent.Purple),
            CalcResultMetric("Net profit", CalcFmt.currency(net), CalcAccent.Orange),
        )
    }
}

// MARK: - Retirement / 401(k)

object RetirementCalc {
    fun results(
        currentAge: Double,
        retirementAge: Double,
        currentBalance: Double,
        annualSalary: Double,
        contributionPct: Double,
        employerMatchRate: Double,
        employerMatchCapPct: Double,
        annualReturn: Double,
    ): List<CalcResultMetric>? {
        val years = retirementAge - currentAge
        if (years <= 0 || annualSalary <= 0) return null

        val yourAnnualContribution = annualSalary * (contributionPct / 100)
        val matchedPct = min(contributionPct, employerMatchCapPct)
        val employerAnnualContribution = annualSalary * (matchedPct / 100) * (employerMatchRate / 100)

        var balance = currentBalance
        var totalYourContributions = 0.0
        var totalEmployerContributions = 0.0
        repeat(ceil(years).toInt()) {
            balance += yourAnnualContribution + employerAnnualContribution
            balance *= (1 + annualReturn / 100)
            totalYourContributions += yourAnnualContribution
            totalEmployerContributions += employerAnnualContribution
        }

        val totalContributed = currentBalance + totalYourContributions + totalEmployerContributions
        val totalGrowth = balance - totalContributed

        return listOf(
            CalcResultMetric("Projected balance at retirement", CalcFmt.currency(balance), CalcAccent.Green),
            CalcResultMetric("Your total contributions", CalcFmt.currency(totalYourContributions), CalcAccent.Blue),
            CalcResultMetric("Employer match total", CalcFmt.currency(totalEmployerContributions), CalcAccent.Purple),
            CalcResultMetric("Investment growth earned", CalcFmt.currency(max(0.0, totalGrowth)), CalcAccent.Orange),
        )
    }
}

// MARK: - Compound Interest

object CompoundInterestCalc {
    fun results(
        initialDeposit: Double,
        monthlyContribution: Double,
        annualRate: Double,
        years: Double,
    ): List<CalcResultMetric>? {
        if ((initialDeposit <= 0 && monthlyContribution <= 0) || years <= 0) return null

        val months = (years * 12).roundToInt()
        val monthlyRate = annualRate / 100 / 12
        var balance = initialDeposit
        repeat(months) {
            balance = balance * (1 + monthlyRate) + monthlyContribution
        }

        val totalContributions = initialDeposit + monthlyContribution * months
        val totalInterest = balance - totalContributions
        val growthPct = if (totalContributions > 0) (totalInterest / totalContributions) * 100 else 0.0

        return listOf(
            CalcResultMetric("Final balance", CalcFmt.currency(balance), CalcAccent.Green),
            CalcResultMetric("Total contributions", CalcFmt.currency(totalContributions), CalcAccent.Blue),
            CalcResultMetric("Interest earned", CalcFmt.currency(max(0.0, totalInterest)), CalcAccent.Purple),
            CalcResultMetric("Total growth", CalcFmt.percent(growthPct, 1), CalcAccent.Orange),
        )
    }
}
