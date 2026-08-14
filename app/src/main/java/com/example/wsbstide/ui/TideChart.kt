package com.example.wsbstide.ui

import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wsbstide.model.TidePoint
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val DayColor   = Color(1f, 1f, 128f / 255f, 1f)        // rgb:ff/ff/80 — matches legacy bgday
private val NightColor = Color(128f / 255f, 128f / 255f, 1f, 1f) // rgb:80/80/ff — matches legacy bgnite

/** Choose a round step size that gives ~[targetCount] labels over [range]. */
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

/** Linear interpolation of tide height at [ms] from a pre-sorted points list. */
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
    /** UTC offset for display (e.g. 10800000 for UTC+3). Used to convert timestamps to local hours. */
    displayOffsetMs: Long = 0L,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val sortedPoints = remember(points) { points.sortedBy { it.timestampMillis } }

    val lineColor  = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    val labelColorArgb = labelColor.toArgb()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        val leftPadding   = 44.dp.toPx()   // widened for depth labels
        val rightPadding  = 16.dp.toPx()
        val topPadding    = 12.dp.toPx()
        val bottomPadding = 28.dp.toPx()   // widened for hour labels

        val chartWidth  = size.width  - leftPadding - rightPadding
        val chartHeight = size.height - topPadding  - bottomPadding

        val minTime = sortedPoints.first().timestampMillis
        val maxTime = sortedPoints.last().timestampMillis
        val minH    = sortedPoints.minOf { it.height }
        val maxH    = sortedPoints.maxOf { it.height }

        val timeRange   = (maxTime - minTime).coerceAtLeast(1L)
        val heightRange = (maxH - minH).takeIf { it > 0.0 } ?: 1.0

        fun xForTime(ms: Long): Float =
            leftPadding + ((ms - minTime).toDouble() / timeRange).toFloat() * chartWidth

        fun yForHeight(h: Double): Float =
            topPadding + (1f - ((h - minH) / heightRange).toFloat()) * chartHeight

        // ── Day / night background bands ────────────────────────────────────
        var isDay      = startsAsDay
        var bandStartX = leftPadding
        val transitions = sunEventMillis
            .filter { it in minTime..maxTime }
            .map { xForTime(it) } + listOf(leftPadding + chartWidth)
        for (tx in transitions) {
            drawRect(
                color     = if (isDay) DayColor else NightColor,
                topLeft   = Offset(bandStartX, topPadding),
                size      = Size((tx - bandStartX).coerceAtLeast(0f), chartHeight),
            )
            isDay      = !isDay
            bandStartX = tx
        }

        // ── Depth axis: horizontal grid lines + left-side labels ─────────────
        val depthStep = niceStep(heightRange, targetCount = 5)
        val firstMark = kotlin.math.ceil(minH / depthStep) * depthStep
        var markH     = firstMark
        var firstLabel = true

        drawIntoCanvas { canvas ->
            val labelPx  = 10.sp.toPx()
            val paint = Paint().apply {
                color     = labelColorArgb
                textSize  = labelPx
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }

            while (markH <= maxH + depthStep * 0.01) {
                val y = yForHeight(markH)
                if (y in topPadding..(topPadding + chartHeight)) {
                    // Faint horizontal rule across the chart
                    drawLine(
                        color       = gridColor,
                        start       = Offset(leftPadding, y),
                        end         = Offset(leftPadding + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    // Small tick on left edge
                    drawLine(
                        color       = labelColor,
                        start       = Offset(leftPadding - 4.dp.toPx(), y),
                        end         = Offset(leftPadding, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    // Label: first one shows unit
                    val text = if (firstLabel) {
                        firstLabel = false
                        "%.1f m".format(markH)
                    } else {
                        "%.1f".format(markH)
                    }
                    canvas.nativeCanvas.drawText(
                        text,
                        leftPadding - 6.dp.toPx(),
                        y + labelPx / 2.5f,
                        paint,
                    )
                }
                markH += depthStep
            }
        }

        // ── Time axis: hourly tick marks + labels ────────────────────────────
        val bottomY     = topPadding + chartHeight
        val hourMs      = 3_600_000L
        // Snap to first full local hour at or after minTime
        val firstHourMs = run {
            val localMin = minTime + displayOffsetMs
            val snapped  = (localMin / hourMs) * hourMs
            val first    = if (snapped < localMin) snapped + hourMs else snapped
            first - displayOffsetMs
        }

        // Auto-select label density so labels don't collide (~40px per label)
        val pxPerHour  = chartWidth / ((maxTime - minTime).toDouble() / hourMs)
        val labelEvery = when {
            pxPerHour >= 60  -> 1
            pxPerHour >= 30  -> 2
            pxPerHour >= 15  -> 3
            pxPerHour >= 10  -> 6
            else             -> 12
        }

        drawIntoCanvas { canvas ->
            val labelPx  = 10.sp.toPx()
            val paint = Paint().apply {
                color     = labelColorArgb
                textSize  = labelPx
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            var t = firstHourMs
            while (t <= maxTime) {
                val x = xForTime(t)
                val localHour = ((t + displayOffsetMs) / hourMs % 24).toInt()
                val isMidnight = localHour == 0

                // Tick mark: midnight is taller
                val tickTop = if (isMidnight) bottomY - 8.dp.toPx() else bottomY - 4.dp.toPx()
                drawLine(
                    color       = labelColor,
                    start       = Offset(x, tickTop),
                    end         = Offset(x, bottomY),
                    strokeWidth = if (isMidnight) 2.dp.toPx() else 1.dp.toPx(),
                )

                // Label
                if (localHour % labelEvery == 0) {
                    canvas.nativeCanvas.drawText(
                        localHour.toString(),
                        x,
                        bottomY + labelPx + 2.dp.toPx(),
                        paint,
                    )
                }

                t += hourMs
            }
        }

        // ── Tide line ───────────────────────────────────────────────────────
        val path = Path()
        path.moveTo(xForTime(sortedPoints.first().timestampMillis),
                    yForHeight(sortedPoints.first().height))
        for (point in sortedPoints.drop(1)) {
            path.lineTo(xForTime(point.timestampMillis), yForHeight(point.height))
        }
        drawPath(
            path  = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── "Now" cross — drawn last so it sits on top of everything ────────
        // Matches legacy's two-line cross (x, y±4px) and (x±4px, y), but in red.
        if (nowMs in minTime..maxTime) {
            val nx = xForTime(nowMs)
            val ny = yForHeight(interpolateHeight(sortedPoints, nowMs))
            val arm = 5.dp.toPx()
            val stroke = 2.dp.toPx()
            drawLine(Color.Red, Offset(nx, ny - arm), Offset(nx, ny + arm), stroke)
            drawLine(Color.Red, Offset(nx - arm, ny), Offset(nx + arm, ny), stroke)
        }
    }
}
