/**
 * TaxRound.kt
 *
 * IRS rounding helpers, ported from iOS Engine/TaxRound.swift.
 *
 * The IRS lets filers round to whole dollars: amounts under 50 cents round
 * down, 50 cents and over round up (away from zero for negatives). Apply
 * [dollar] only at the 1040 line boundaries the IRS rounds at — keep cents
 * internally.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.abs
import kotlin.math.floor

/** Round to a whole dollar, half away from zero (IRS convention). */
fun dollar(x: Double): Double {
    if (!x.isFinite()) return 0.0
    val sign = if (x > 0) 1.0 else if (x < 0) -1.0 else 0.0
    return sign * floor(abs(x) + 0.5)
}

/** Clamp to non-negative (many 1040 lines are floored at zero). */
fun nonNeg(x: Double): Double = if (x > 0) x else 0.0

/** Sum a numeric field across a list, treating NaN as zero (JS `|| 0`). */
fun <T> sumBy(items: List<T>, fn: (T) -> Double): Double =
    items.fold(0.0) { acc, item ->
        val v = fn(item)
        acc + (if (v.isNaN()) 0.0 else v)
    }
