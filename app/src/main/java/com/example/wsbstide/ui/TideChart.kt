package com.example.wsbstide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.wsbstide.model.TidePoint

@Composable
fun TideChart(
    points: List<TidePoint>,
    modifier: Modifier = Modifier
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

        // Horizontal grid
        repeat(5) { index ->
            val fraction = index / 4f

            val y =
                topPadding +
                        fraction * chartHeight

            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(
                    leftPadding,
                    y
                ),
                end = androidx.compose.ui.geometry.Offset(
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