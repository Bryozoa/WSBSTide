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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wsbstide.ui.TideChart
import com.example.wsbstide.ui.theme.WSBSTideTheme
import java.util.Calendar
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
        val station = TideRepository(this).getStation(year)

        val calculator = TideCalculator(StandardTideTimeBasis())
        val generator = TideGraphGenerator(calculator)

        val now = System.currentTimeMillis()
        val startMs = now - 12 * 3_600_000L
        val endMs   = now + 36 * 3_600_000L

        val points = generator.generate(
            startMillis = startMs,
            endMillis   = endMs,
            stepMinutes = 5,
            station     = station,
        )

        // WhiteSeaBioStation coordinates from harm_msc header
        val (startsAsDay, sunEventMillis) = sunEvents(
            latDeg    = 66.55,
            lonEastDeg = 33.10,
            startMs   = startMs,
            endMs     = endMs,
        )

        setContent {
            WSBSTideTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TideChart(
                        points          = points,
                        startsAsDay     = startsAsDay,
                        sunEventMillis  = sunEventMillis,
                        displayOffsetMs = station.meridianSeconds * 1000L,
                        modifier        = Modifier
                            .padding(innerPadding)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
