package com.example.wsbstide

import TideCalculator
import TideGraphGenerator
import TideRepository
import StandardTideTimeBasis
import sunEvents
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wsbstide.model.TidePoint
import com.example.wsbstide.ui.TideChart
import com.example.wsbstide.ui.theme.WSBSTideTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val year    = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
        val station = TideRepository(this).getStation(year)

        val calculator = TideCalculator(StandardTideTimeBasis())
        val generator  = TideGraphGenerator(calculator)

        val now     = System.currentTimeMillis()
        val startMs = now - 12 * 3_600_000L
        val endMs   = now + 36 * 3_600_000L

        val points = generator.generate(
            startMillis = startMs,
            endMillis   = endMs,
            stepMinutes = 5,
            station     = station,
        )

        // Extended to 30 days so the next sunrise/sunset is always found even during
        // polar day or polar night (WSBS polar periods are at most ~24 days long).
        val (startsAsDay, sunEventMillis) = sunEvents(
            latDeg     = 66.55,
            lonEastDeg = 33.10,
            startMs    = startMs,
            endMs      = now + 30L * 24 * 3_600_000L,
        )

        val displayOffsetMs = TimeZone.getDefault().getOffset(now).toLong()

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

    var viewportStartMs by remember {
        mutableLongStateOf((System.currentTimeMillis() - 2L * 3_600_000L).coerceIn(dataStartMs, clampMax))
    }

    val nextHighTideMs = remember(sortedPoints, nowMs) { nextExtreme(sortedPoints, nowMs, high = true) }
    val nextLowTideMs  = remember(sortedPoints, nowMs) { nextExtreme(sortedPoints, nowMs, high = false) }
    val (nextSunriseMs, nextSunsetMs) = remember(sunEventMillis, startsAsDay, nowMs) {
        nextSunEvents(startsAsDay, sunEventMillis, nowMs)
    }

    Column(modifier = modifier) {
        Text(
            text      = "White Sea Biological Station",
            style     = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
            modifier   = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
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
        )
        InfoPanel(
            nowMs          = nowMs,
            nextHighTideMs = nextHighTideMs,
            nextLowTideMs  = nextLowTideMs,
            nextSunriseMs  = nextSunriseMs,
            nextSunsetMs   = nextSunsetMs,
            modifier       = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun InfoPanel(
    nowMs: Long,
    nextHighTideMs: Long?,
    nextLowTideMs: Long?,
    nextSunriseMs: Long?,
    nextSunsetMs: Long?,
    modifier: Modifier = Modifier,
) {
    val tz      = TimeZone.getDefault()
    val timeFmt = remember { SimpleDateFormat("HH:mm",          Locale.getDefault()).apply { timeZone = tz } }
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm",   Locale.getDefault()).apply { timeZone = tz } }

    val utcLabel = run {
        val offsetMs  = tz.getOffset(nowMs)
        val sign      = if (offsetMs >= 0) "+" else "-"
        val absMs     = kotlin.math.abs(offsetMs)
        val hours     = absMs / 3_600_000
        val minutes   = (absMs % 3_600_000) / 60_000
        if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
    }

    fun fmtNow(ms: Long) = "${timeFmt.format(Date(ms))} $utcLabel"
    fun fmtEvent(ms: Long?): String {
        if (ms == null) return "—"
        val nowCal   = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val eventCal = Calendar.getInstance(tz).apply { timeInMillis = ms }
        val sameDay  = nowCal.get(Calendar.YEAR)        == eventCal.get(Calendar.YEAR) &&
                       nowCal.get(Calendar.DAY_OF_YEAR) == eventCal.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) timeFmt.format(Date(ms)) else dateFmt.format(Date(ms))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow("Now",            fmtNow(nowMs))
        InfoRow("Next high tide", fmtEvent(nextHighTideMs))
        InfoRow("Next low tide",  fmtEvent(nextLowTideMs))
        InfoRow("Next Sunrise",        fmtEvent(nextSunriseMs))
        InfoRow("Next Sunset",         fmtEvent(nextSunsetMs))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** Returns the timestamp of the next local maximum (high=true) or minimum after [afterMs]. */
private fun nextExtreme(sorted: List<TidePoint>, afterMs: Long, high: Boolean): Long? {
    for (i in 1 until sorted.size - 1) {
        if (sorted[i].timestampMillis <= afterMs) continue
        val prev = sorted[i - 1].height
        val cur  = sorted[i].height
        val next = sorted[i + 1].height
        if (high  && cur > prev && cur > next) return sorted[i].timestampMillis
        if (!high && cur < prev && cur < next) return sorted[i].timestampMillis
    }
    return null
}

/**
 * Returns (nextSunriseMs, nextSunsetMs) strictly after [afterMs].
 * Either can be null if not found within the sun-event window — this should not
 * happen in practice since the window is 30 days, longer than any polar period at WSBS.
 */
private fun nextSunEvents(
    startsAsDay: Boolean,
    sunEventMillis: List<Long>,
    afterMs: Long,
): Pair<Long?, Long?> {
    val eventsBefore = sunEventMillis.count { it <= afterMs }
    val currentlyDay = if (eventsBefore % 2 == 0) startsAsDay else !startsAsDay
    val future = sunEventMillis.filter { it > afterMs }

    // If day now: future events go sunset, sunrise, sunset …
    // If night now: future events go sunrise, sunset, sunrise …
    return if (currentlyDay) {
        Pair(future.getOrNull(1), future.getOrNull(0))  // (sunrise, sunset)
    } else {
        Pair(future.getOrNull(0), future.getOrNull(1))  // (sunrise, sunset)
    }
}
