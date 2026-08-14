package com.example.wsbstide

import TideCalculator
import TideGraphGenerator
import TideRepository
import StandardTideTimeBasis
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
        val points = generator.generate(
            startMillis = now - 12 * 3_600_000L,
            endMillis   = now + 36 * 3_600_000L,
            stepMinutes = 5,
            station     = station,
        )

        setContent {
            WSBSTideTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TideChart(
                        points = points,
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
