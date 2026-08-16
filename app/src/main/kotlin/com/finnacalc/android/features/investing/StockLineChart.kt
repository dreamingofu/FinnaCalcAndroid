//
// StockLineChart.kt
//
// Port of iOS Features/Investing/StockLineChart.swift — the Cash App-style
// price chart with two render styles (Catmull-Rom close line, or OHLC
// candlesticks), the 1D/1W/1M/1Y/ALL range pills, drag-to-scrub, pinch zoom
// with pan, and optional price/date scales in their own gutters.
//
// Deviation from iOS: SwiftUI Charts has no Compose equivalent, so the plot
// is drawn on a Canvas. The maths — index-based x domain (a raw epoch domain
// collapses a single day into a sliver), padded y domain over the VISIBLE
// window, line-anchored scrub, 4-tick scales — is ported directly.
//

package com.finnacalc.android.features.investing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.market.CandlePoint
import com.finnacalc.android.features.calculators.CalcFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The five Cash App range pills. `raw` doubles as the API `range` param. */
enum class ChartRange(val raw: String) {
    OneDay("1D"), OneWeek("1W"), OneMonth("1M"), OneYear("1Y"), All("ALL")
}

enum class ChartStyle { Line, Candles }

@Composable
fun StockLineChart(
    points: List<CandlePoint>,
    modifier: Modifier = Modifier,
    /** Reference for up/down colour — previous close on 1D, else first point. */
    baseline: Double? = null,
    /** Drives the date format of the x-axis labels. */
    range: ChartRange = ChartRange.OneDay,
    style: ChartStyle = ChartStyle.Line,
    /** Four prices down the trailing edge and four dates under the plot. */
    showScales: Boolean = false,
    /**
     * A second line drawn for comparison, already aligned to `points` and
     * scaled by the owner so both start at the same value. Dashed brand blue,
     * exempt from scrubbing.
     */
    comparePoints: List<CandlePoint>? = null,
    /** Sample index under the finger while scrubbing, else null. */
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
) {
    val colors = Theme.colors
    val positive = colors.positive
    val negative = colors.negative
    val muted = colors.mutedForeground
    val border = colors.border
    val compareColor = colors.primary.copy(alpha = 0.8f)

    // Pinch-zoom window over sample indices (null = everything).
    var visibleLo by remember(points) { mutableStateOf<Double?>(null) }
    var visibleHi by remember(points) { mutableStateOf<Double?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }

    val fullHi = max(points.size - 1, 1).toDouble()
    val domainLo = visibleLo ?: 0.0
    val domainHi = visibleHi ?: fullHi

    val visiblePoints = remember(points, domainLo, domainHi) {
        val lo = domainLo.toInt().coerceIn(0, max(points.size - 1, 0))
        val hi = kotlin.math.ceil(domainHi).toInt().coerceIn(0, max(points.size - 1, 0))
        if (points.isEmpty() || lo > hi) points else points.subList(lo, hi + 1)
    }

    val isUp = run {
        val last = points.lastOrNull()?.c ?: return@run true
        val base = baseline ?: points.firstOrNull()?.c ?: last
        last >= base
    }
    val lineColor = if (isUp) positive else negative

    // The comparison line lives on the same axis, so the domain must cover it
    // or it clips out of the plot. A hair of headroom keeps strokes off the edges.
    val yLo: Double
    val yHi: Double
    run {
        val highs = visiblePoints.map { it.h ?: it.c } + (comparePoints?.map { it.c } ?: emptyList())
        val lows = visiblePoints.map { it.l ?: it.c } + (comparePoints?.map { it.c } ?: emptyList())
        val lo = lows.minOrNull()
        val hi = highs.maxOrNull()
        if (lo == null || hi == null || hi <= lo) {
            yLo = 0.0
            yHi = 1.0
        } else {
            val pad = (hi - lo) * 0.08
            yLo = lo - pad
            yHi = hi + pad
        }
    }

    // A $1,000 stock needs a wider label than a $12 one.
    val wideScale = yHi >= 1000
    val priceGutter = if (wideScale) 44.dp else 34.dp
    val dateGutter = 18.dp
    val scaleFont = if (wideScale) 8 else 9

    // Left→right draw-in whenever a new series arrives.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points.map { it.t }) {
        visibleLo = null
        visibleHi = null
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(700))
    }

    val currentPoints by rememberUpdatedState(points)

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(
                    end = if (showScales) priceGutter else 0.dp,
                    bottom = if (showScales) dateGutter else 0.dp,
                )
                .pointerInput(points) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val width = domainHi - domainLo
                        if (zoom != 1f) {
                            // Pinch out (zoom > 1) → narrower window → zoom in.
                            val center = (domainLo + domainHi) / 2
                            var w = (width / zoom).coerceIn(5.0, fullHi)
                            var lo = center - w / 2
                            var hi = center + w / 2
                            if (lo < 0) { hi += -lo; lo = 0.0 }
                            if (hi > fullHi) { lo -= hi - fullHi; hi = fullHi }
                            lo = max(lo, 0.0)
                            if (hi - lo >= fullHi) {
                                visibleLo = null
                                visibleHi = null
                            } else {
                                visibleLo = lo
                                visibleHi = hi
                            }
                        } else if (visibleLo != null && abs(pan.x) > 0f) {
                            // Zoomed in: a one-finger drag that isn't a scrub pans.
                            val delta = -pan.x * width / size.width
                            var lo = domainLo + delta
                            var hi = domainHi + delta
                            if (lo < 0) { hi += -lo; lo = 0.0 }
                            if (hi > fullHi) { lo -= hi - fullHi; hi = fullHi }
                            visibleLo = lo
                            visibleHi = hi
                        }
                    }
                }
                .pointerInput(points) {
                    detectTapGestures(onDoubleTap = {
                        visibleLo = null
                        visibleHi = null
                    })
                }
                .pointerInput(points, domainLo, domainHi) {
                    detectDragGestures(
                        onDragStart = { start ->
                            // Scrub is LINE-ANCHORED: it engages only when the
                            // touch begins within 36dp of the plotted price, so
                            // a drag elsewhere pans instead.
                            val span = max(domainHi - domainLo, 1.0)
                            val idx = (domainLo + (start.x / size.width) * span)
                                .roundToInt().coerceIn(0, max(currentPoints.size - 1, 0))
                            val point = currentPoints.getOrNull(idx)
                            if (point != null && yHi > yLo) {
                                val lineY = ((yHi - point.c) / (yHi - yLo)).toFloat() * size.height
                                if (abs(start.y - lineY) <= 36.dp.toPx()) {
                                    isScrubbing = true
                                    onSelectedIndexChange(idx)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            if (!isScrubbing) return@detectDragGestures
                            val span = max(domainHi - domainLo, 1.0)
                            val idx = (domainLo + (change.position.x / size.width) * span)
                                .roundToInt().coerceIn(0, max(currentPoints.size - 1, 0))
                            onSelectedIndexChange(idx)
                        },
                        onDragEnd = {
                            isScrubbing = false
                            onSelectedIndexChange(null)
                        },
                        onDragCancel = {
                            isScrubbing = false
                            onSelectedIndexChange(null)
                        },
                    )
                }
        ) {
            if (points.isEmpty() || yHi <= yLo) return@Canvas
            val span = max(domainHi - domainLo, 1.0)
            fun xFor(index: Int): Float = ((index - domainLo) / span).toFloat() * size.width
            fun yFor(value: Double): Float = ((yHi - value) / (yHi - yLo)).toFloat() * size.height

            withReveal(reveal.value) {
                when (style) {
                    ChartStyle.Line -> {
                        drawSeries(points, ::xFor, ::yFor, lineColor, 2.dp.toPx())
                        comparePoints?.let {
                            drawSeries(
                                it, ::xFor, ::yFor, compareColor, 1.6.dp.toPx(),
                                dash = floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                            )
                        }
                    }
                    ChartStyle.Candles -> {
                        val bodyWidth = (size.width / max(points.size, 1) * 0.6f).coerceAtLeast(1f)
                        points.forEachIndexed { index, point ->
                            val open = point.o ?: point.c
                            val up = point.c >= open
                            val color = if (up) positive else negative
                            val x = xFor(index)
                            // Wick
                            drawLine(
                                color,
                                Offset(x, yFor(point.h ?: max(open, point.c))),
                                Offset(x, yFor(point.l ?: min(open, point.c))),
                                strokeWidth = 1.dp.toPx(),
                            )
                            // Body
                            val top = yFor(max(open, point.c))
                            val bottom = yFor(min(open, point.c))
                            drawRect(
                                color,
                                topLeft = Offset(x - bodyWidth / 2, top),
                                size = Size(bodyWidth, max(bottom - top, 1f)),
                            )
                        }
                    }
                }
            }

            // Scrub guide + dot.
            selectedIndex?.let { i ->
                points.getOrNull(i)?.let { point ->
                    val x = xFor(i)
                    drawLine(muted.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    drawCircle(
                        if (style == ChartStyle.Line) lineColor else colors.foreground,
                        radius = if (style == ChartStyle.Line) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(x, yFor(point.c)),
                    )
                }
            }
        }

        if (showScales) {
            ChartScales(
                points = points,
                domainLo = domainLo,
                domainHi = domainHi,
                yLo = yLo,
                yHi = yHi,
                range = range,
                priceGutter = priceGutter,
                dateGutter = dateGutter,
                fontSize = scaleFont,
                axisColor = border,
                labelColor = muted,
            )
        }
    }
}

/** Catmull-Rom-ish smooth series through the sample points. */
private fun DrawScope.drawSeries(
    points: List<CandlePoint>,
    xFor: (Int) -> Float,
    yFor: (Double) -> Float,
    color: Color,
    width: Float,
    dash: FloatArray? = null,
) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(xFor(0), yFor(points[0].c))
    for (i in 1 until points.size) {
        val x0 = xFor(i - 1)
        val y0 = yFor(points[i - 1].c)
        val x1 = xFor(i)
        val y1 = yFor(points[i].c)
        // Horizontal-tangent cubic: the same visual smoothing Catmull-Rom
        // gives on an evenly-spaced index domain.
        val cx = (x0 + x1) / 2
        path.cubicTo(cx, y0, cx, y1, x1, y1)
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = dash?.let { androidx.compose.ui.graphics.PathEffect.dashPathEffect(it) },
        ),
    )
}

/** Reveals the plot left→right as `fraction` grows. */
private fun DrawScope.withReveal(fraction: Float, block: DrawScope.() -> Unit) {
    if (fraction >= 1f) {
        block()
        return
    }
    clipRect(right = size.width * fraction) { block() }
}

/**
 * Four prices in the trailing gutter and four dates below the plot, with hair
 * axis lines separating plot from labels — so the line can never run through
 * a label.
 */
@Composable
private fun ChartScales(
    points: List<CandlePoint>,
    domainLo: Double,
    domainHi: Double,
    yLo: Double,
    yHi: Double,
    range: ChartRange,
    priceGutter: Dp,
    dateGutter: Dp,
    fontSize: Int,
    axisColor: Color,
    labelColor: Color,
) {
    val ySpan = yHi - yLo
    val xSpan = max(domainHi - domainLo, 1.0)
    // The top tick sits close under the plot's ceiling on purpose.
    val priceTicks = if (ySpan > 0) listOf(0.06, 0.353, 0.647, 0.94).map { yLo + ySpan * it } else emptyList()
    val dateTicks = if (points.isNotEmpty()) {
        listOf(0.07, 0.36, 0.64, 0.93)
            .map { (domainLo + xSpan * it).roundToInt().coerceIn(0, points.size - 1) }
            .distinct()
    } else emptyList()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val plotW = maxWidth - priceGutter
        val plotH = maxHeight - dateGutter
        // Axis lines: one above the dates, one left of the prices.
        Box(
            Modifier
                .offset(y = plotH)
                .width(plotW)
                .height(1.dp)
                .background(axisColor)
        )
        Box(
            Modifier
                .offset(x = plotW)
                .width(1.dp)
                .height(plotH)
                .background(axisColor)
        )
        priceTicks.forEach { value ->
            val f = if (ySpan > 0) (value - yLo) / ySpan else 0.0
            Text(
                "$" + CalcFormat.int(value),
                style = Theme.figure(fontSize, FontWeight.Medium),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.offset(
                    x = plotW + 3.dp,
                    y = (plotH * (1 - f).toFloat()) - 5.dp,
                ),
            )
        }
        dateTicks.forEach { i ->
            val x = plotW * ((i - domainLo) / xSpan).toFloat()
            Text(
                dateLabel(points[i], range),
                style = Theme.figure(fontSize, FontWeight.Medium),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.offset(
                    // Clamped so edge labels stay inside the plot width.
                    x = x.coerceIn(0.dp, (plotW - 46.dp).coerceAtLeast(0.dp)),
                    y = plotH + 3.dp,
                ),
            )
        }
    }
}

/**
 * Formatted in the DEVICE's time zone (see CandlePoint.localInstant): the API
 * hands back exchange wall-clock, which would otherwise print New York time
 * to everyone.
 */
private fun dateLabel(point: CandlePoint, range: ChartRange): String {
    val pattern = when (range) {
        ChartRange.OneDay -> "h:mm a"
        ChartRange.OneWeek -> "EEE"
        ChartRange.OneMonth, ChartRange.OneYear -> "MMM d"
        ChartRange.All -> "MMM yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(point.localInstant)
}

/** The range pills row. Selected pill = solid fill; others = faint fill. */
@Composable
fun ChartRangePicker(selection: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartRange.entries.forEach { range ->
            val selected = range == selection
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) Theme.colors.muted else Theme.colors.muted.copy(alpha = 0.4f)
                    )
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    range.raw,
                    style = Theme.figure(
                        Theme.FontSize.sm,
                        if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) Theme.colors.foreground else Theme.colors.mutedForeground,
                )
            }
        }
    }
}

/**
 * A tiny gesture-free line for a watchlist row. Coloured by the row's daily
 * change (not the sparkline window) so it agrees with the % beside it.
 */
@Composable
fun SparkLine(
    closes: List<Double>,
    isUp: Boolean,
    modifier: Modifier = Modifier,
    /** Overrides the default positive/negative colour (the inverted hero). */
    tint: Color? = null,
) {
    val color = tint ?: if (isUp) Theme.colors.positive else Theme.colors.negative
    Canvas(modifier) {
        if (closes.size < 2) return@Canvas
        val lo = closes.min()
        val hi = closes.max()
        if (hi <= lo) return@Canvas
        val pad = (hi - lo) * 0.1
        val yLo = lo - pad
        val yHi = hi + pad
        val path = Path()
        closes.forEachIndexed { index, close ->
            val x = size.width * index / (closes.size - 1)
            val y = ((yHi - close) / (yHi - yLo)).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
