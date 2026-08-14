package com.example.wsbstide.ui

import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsbstide.model.TidePoint
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val DayColor   = Color(1f, 1f, 128f / 255f, 1f)          // rgb:ff/ff/80 — matches legacy bgday
private val NightColor = Color(128f / 255f, 128f / 255f, 1f, 1f)  // rgb:80/80/ff — matches legacy bgnite
private val TideGreen  = Color(0xFF163417)                 // tide line and extremum markers

private fun niceStep(range: Double, targetCount: Int = 5): Double {
    val raw = range / targetCount
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val nice = when {
        n < 1.5 -> 1.0
        n < 3.0 -> 2.0
        n < 7.0 -> 5.0
        else    -> 10.0
    }
    return nice * mag
}

private fun interpolateHeight(sorted: List<TidePoint>, ms: Long): Double {
    if (ms <= sorted.first().timestampMillis) return sorted.first().height
    if (ms >= sorted.last().timestampMillis)  return sorted.last().height
    val i = sorted.indexOfFirst { it.timestampMillis >= ms }
    val a = sorted[i - 1]; val b = sorted[i]
    val f = (ms - a.timestampMillis).toDouble() / (b.timestampMillis - a.timestampMillis)
    return a.height + f * (b.height - a.height)
}

@Composable
fun TideChart(
    points: List<TidePoint>,
    nowMs: Long = System.currentTimeMillis(),
    startsAsDay: Boolean = true,
    sunEventMillis: List<Long> = emptyList(),
    displayOffsetMs: Long = 0L,
    /** Left edge of visible window. 0L means show the full data range (no scrolling). */
    viewportStartMs: Long = 0L,
    /** Width of visible window in ms. 0L means show all data. */
    viewportDurationMs: Long = 0L,
    /** Called with signed ms delta when the user drags horizontally. */
    onDragDeltaMs: (Long) -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val sortedPoints = remember(points) { points.sortedBy { it.timestampMillis } }

    val labelColor     = MaterialTheme.colorScheme.onSurface
    val gridColor      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val labelColorArgb = labelColor.toArgb()

    val chartWidthState = remember { mutableFloatStateOf(0f) }
    val onDragUpdated   = rememberUpdatedState(onDragDeltaMs)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .onSizeChanged { sz ->
                // Full canvas width — no internal left/right padding on the x-axis.
                chartWidthState.floatValue = sz.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(viewportDurationMs) {
                if (viewportDurationMs <= 0L) return@pointerInput
                detectHorizontalDragGestures { _, dragAmount ->
                    val w = chartWidthState.floatValue
                    if (w > 0f) {
                        val msPerPx = viewportDurationMs.toDouble() / w
                        onDragUpdated.value((-dragAmount * msPerPx).toLong())
                    }
                }
            },
    ) {
        val topPadding    = 12.dp.toPx()
        val bottomPadding = 28.dp.toPx()

        // Chart fills the full canvas width; depth labels are overlaid on the left edge.
        val chartWidth  = size.width
        val chartHeight = size.height - topPadding - bottomPadding

        val minTime   = if (viewportDurationMs > 0L) viewportStartMs
                        else sortedPoints.first().timestampMillis
        val maxTime   = if (viewportDurationMs > 0L) viewportStartMs + viewportDurationMs
                        else sortedPoints.last().timestampMillis
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)

        // y-axis fixed to full data range for a stable scale while scrolling.
        val minH        = sortedPoints.minOf { it.height }
        val maxH        = sortedPoints.maxOf { it.height }
        val heightRange = (maxH - minH).takeIf { it > 0.0 } ?: 1.0

        fun xForTime(ms: Long): Float =
            ((ms - minTime).toDouble() / timeRange).toFloat() * chartWidth

        fun yForHeight(h: Double): Float =
            topPadding + (1f - ((h - minH) / heightRange).toFloat()) * chartHeight

        // ── Day / night background bands ────────────────────────────────────
        val eventsBeforeViewport = sunEventMillis.count { it < minTime }
        val viewportStartsAsDay  = if (eventsBeforeViewport % 2 == 0) startsAsDay else !startsAsDay

        var isDay      = viewportStartsAsDay
        var bandStartX = 0f
        val transitions = sunEventMillis
            .filter { it in minTime..maxTime }
            .map { xForTime(it) } + listOf(chartWidth)
        for (tx in transitions) {
            drawRect(
                color   = if (isDay) DayColor else NightColor,
                topLeft = Offset(bandStartX, topPadding),
                size    = Size((tx - bandStartX).coerceAtLeast(0f), chartHeight),
            )
            isDay      = !isDay
            bandStartX = tx
        }

        // ── Depth axis: horizontal grid lines + overlaid left-edge labels ─────
        val depthStep  = niceStep(heightRange, targetCount = 5)
        val firstMark  = kotlin.math.ceil(minH / depthStep) * depthStep
        var markH      = firstMark
        var firstLabel = true

        drawIntoCanvas { canvas ->
            val labelPx = 10.sp.toPx()
            val paint = Paint().apply {
                color       = labelColorArgb
                textSize    = labelPx
                textAlign   = Paint.Align.LEFT
                isAntiAlias = true
            }
            while (markH <= maxH + depthStep * 0.01) {
                val y = yForHeight(markH)
                if (y in topPadding..(topPadding + chartHeight)) {
                    drawLine(gridColor, Offset(0f, y), Offset(chartWidth, y), 1.dp.toPx())
                    // Small tick at left edge
                    drawLine(labelColor, Offset(0f, y), Offset(4.dp.toPx(), y), 1.dp.toPx())
                    val text = if (firstLabel) {
                        firstLabel = false; "%.1f m".format(markH)
                    } else {
                        "%.1f".format(markH)
                    }
                    canvas.nativeCanvas.drawText(text, 6.dp.toPx(), y + labelPx / 2.5f, paint)
                }
                markH += depthStep
            }
        }

        // ── Time axis: tick marks + labels ──────────────────────────────────
        // Hour ticks are 2× the original size; half-hour ticks use the original size.
        // Original: regular hour = 4dp long / 1dp wide; midnight = 8dp / 2dp.
        // Now:      regular hour = 8dp / 2dp; midnight = 16dp / 4dp; half-hour = 4dp / 1dp.
        val bottomY    = topPadding + chartHeight
        val hourMs     = 3_600_000L
        val halfHourMs = 1_800_000L
        // Snap to the first half-hour boundary at or after minTime (in local time).
        val firstTickMs = run {
            val localMin = minTime + displayOffsetMs
            val snapped  = (localMin / halfHourMs) * halfHourMs
            val first    = if (snapped < localMin) snapped + halfHourMs else snapped
            first - displayOffsetMs
        }
        val pxPerHour  = chartWidth / (timeRange.toDouble() / hourMs)
        val labelEvery = when {
            pxPerHour >= 60 -> 1
            pxPerHour >= 30 -> 2
            pxPerHour >= 15 -> 3
            pxPerHour >= 10 -> 6
            else            -> 12
        }

        drawIntoCanvas { canvas ->
            val labelPx = 10.sp.toPx()
            val paint = Paint().apply {
                color       = labelColorArgb
                textSize    = labelPx
                textAlign   = Paint.Align.CENTER
                isAntiAlias = true
            }
            var t = firstTickMs
            while (t <= maxTime) {
                val x            = xForTime(t)
                val minuteOfHour = ((t + displayOffsetMs) / 60_000L % 60).toInt()
                val isHalfHour = minuteOfHour == 30
                val localHour  = ((t + displayOffsetMs) / hourMs % 24).toInt()

                val (tickLen, tickWidth) = if (isHalfHour)
                    Pair(4.dp.toPx(), 1.dp.toPx())
                else
                    Pair(8.dp.toPx(), 2.dp.toPx())
                drawLine(labelColor, Offset(x, bottomY - tickLen), Offset(x, bottomY), tickWidth)

                if (!isHalfHour && localHour % labelEvery == 0)
                    canvas.nativeCanvas.drawText(localHour.toString(), x, bottomY + labelPx + 2.dp.toPx(), paint)

                t += halfHourMs
            }
        }

        // ── Extremum drop-lines ──────────────────────────────────────────────
        // Thin vertical green lines from each high/low tide point down to the bottom axis.
        for (i in 1 until sortedPoints.size - 1) {
            val prev = sortedPoints[i - 1].height
            val cur  = sortedPoints[i].height
            val next = sortedPoints[i + 1].height
            if (cur > prev && cur > next || cur < prev && cur < next) {
                val x = xForTime(sortedPoints[i].timestampMillis)
                val y = yForHeight(cur)
                drawLine(TideGreen, Offset(x, y), Offset(x, bottomY), 1.dp.toPx())
            }
        }

        // ── Tide line ────────────────────────────────────────────────────────
        val path = Path()
        path.moveTo(xForTime(sortedPoints.first().timestampMillis), yForHeight(sortedPoints.first().height))
        for (point in sortedPoints.drop(1)) {
            path.lineTo(xForTime(point.timestampMillis), yForHeight(point.height))
        }
        drawPath(path, TideGreen, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── "Now" cross ─────────────────────────────────────────────────────
        if (nowMs in minTime..maxTime) {
            val nx     = xForTime(nowMs)
            val ny     = yForHeight(interpolateHeight(sortedPoints, nowMs))
            val arm    = 5.dp.toPx()
            val stroke = 2.dp.toPx()
            drawLine(Color.Red, Offset(nx, ny - arm), Offset(nx, ny + arm), stroke)
            drawLine(Color.Red, Offset(nx - arm, ny), Offset(nx + arm, ny), stroke)
        }
    }
}
