package com.example.wsbstide

import TideCalculator
import TideGraphGenerator
import TideRepository
import StandardTideTimeBasis
import sunEvents
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wsbstide.model.TidePoint
import com.example.wsbstide.ui.TideChart
import com.example.wsbstide.ui.theme.WSBSTideTheme
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
        val station = TideRepository(this).getStation(year)

        val calculator = TideCalculator(StandardTideTimeBasis())
        val generator  = TideGraphGenerator(calculator)

        val now    = System.currentTimeMillis()
        val startMs = now - 12 * 3_600_000L
        val endMs   = now + 36 * 3_600_000L

        val points = generator.generate(
            startMillis = startMs,
            endMillis   = endMs,
            stepMinutes = 5,
            station     = station,
        )

        val (startsAsDay, sunEventMillis) = sunEvents(
            latDeg     = 66.55,   // WhiteSeaBioStation, from harm_msc header
            lonEastDeg = 33.10,
            startMs    = startMs,
            endMs      = endMs,
        )

        val displayOffsetMs = station.meridianSeconds * 1000L

        setContent {
            WSBSTideTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TideScreen(
                        points          = points,
                        startsAsDay     = startsAsDay,
                        sunEventMillis  = sunEventMillis,
                        displayOffsetMs = displayOffsetMs,
                        modifier        = Modifier
                            .padding(innerPadding)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TideScreen(
    points: List<TidePoint>,
    startsAsDay: Boolean,
    sunEventMillis: List<Long>,
    displayOffsetMs: Long,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val sortedPoints       = remember(points) { points.sortedBy { it.timestampMillis } }
    val dataStartMs        = sortedPoints.first().timestampMillis
    val dataEndMs          = sortedPoints.last().timestampMillis
    val viewportDurationMs = 12L * 3_600_000L
    val clampMax           = (dataEndMs - viewportDurationMs).coerceAtLeast(dataStartMs)

    // Initialise once so "now" sits 2 h from the left edge; not re-initialised on minute ticks.
    var viewportStartMs by remember {
        mutableLongStateOf((System.currentTimeMillis() - 2L * 3_600_000L).coerceIn(dataStartMs, clampMax))
    }

    TideChart(
        points             = points,
        nowMs              = nowMs,
        startsAsDay        = startsAsDay,
        sunEventMillis     = sunEventMillis,
        displayOffsetMs    = displayOffsetMs,
        viewportStartMs    = viewportStartMs,
        viewportDurationMs = viewportDurationMs,
        onDragDeltaMs      = { delta ->
            viewportStartMs = (viewportStartMs + delta).coerceIn(dataStartMs, clampMax)
        },
        modifier = modifier,
    )
}
