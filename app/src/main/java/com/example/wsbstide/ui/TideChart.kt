package com.example.wsbstide.ui

import android.annotation.SuppressLint
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
import androidx.compose.ui.unit.dp
import com.example.wsbstide.model.TidePoint

private val DayColor   = Color(1f, 1f, 128f / 255f, 1f)   // rgb:ff/ff/80 — matches legacy bgday
private val NightColor = Color(128f / 255f, 128f / 255f, 1f, 1f) // rgb:80/80/ff — matches legacy bgnite

@Composable
fun TideChart(
    points: List<TidePoint>,
    startsAsDay: Boolean = true,
    sunEventMillis: List<Long> = emptyList(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        return
    }

    val sortedPoints = remember(points) {
        points.sortedBy { it.timestampMillis }
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {

        val leftPadding = 16.dp.toPx()
        val rightPadding = 16.dp.toPx()
        val topPadding = 16.dp.toPx()
        val bottomPadding = 16.dp.toPx()

        val chartWidth =
            size.width - leftPadding - rightPadding

        val chartHeight =
            size.height - topPadding - bottomPadding

        val minTime =
            sortedPoints.first().timestampMillis

        val maxTime =
            sortedPoints.last().timestampMillis

        val minHeight =
            sortedPoints.minOf { it.height }

        val maxHeight =
            sortedPoints.maxOf { it.height }

        val timeRange =
            (maxTime - minTime).coerceAtLeast(1L)

        val heightRange =
            (maxHeight - minHeight)
                .takeIf { it > 0.0 }
                ?: 1.0

        fun xForTime(timestampMillis: Long): Float {
            val fraction =
                (timestampMillis - minTime).toDouble() /
                        timeRange.toDouble()

            return leftPadding +
                    fraction.toFloat() * chartWidth
        }

        fun yForHeight(height: Double): Float {
            val fraction =
                (height - minHeight) / heightRange

            return topPadding +
                    (1f - fraction.toFloat()) * chartHeight
        }

        // Day / night background bands
        var isDay = startsAsDay
        var bandStartX = leftPadding
        val transitions = sunEventMillis
            .filter { it in minTime..maxTime }
            .map { xForTime(it) } + listOf(leftPadding + chartWidth)
        for (tx in transitions) {
            val bandColor = if (isDay) DayColor else NightColor
            drawRect(
                color = bandColor,
                topLeft = Offset(bandStartX, topPadding),
                size = Size((tx - bandStartX).coerceAtLeast(0f), chartHeight),
            )
            isDay = !isDay
            bandStartX = tx
        }

        // Horizontal grid
        repeat(5) { index ->
            val fraction = index / 4f

            val y =
                topPadding +
                        fraction * chartHeight

            drawLine(
                color = gridColor,
                start = Offset(
                    leftPadding,
                    y
                ),
                end = Offset(
                    leftPadding + chartWidth,
                    y
                ),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path()

        val firstPoint = sortedPoints.first()

        path.moveTo(
            xForTime(firstPoint.timestampMillis),
            yForHeight(firstPoint.height)
        )

        for (point in sortedPoints.drop(1)) {
            path.lineTo(
                xForTime(point.timestampMillis),
                yForHeight(point.height)
            )
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}