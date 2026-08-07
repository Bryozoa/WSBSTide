package com.example.wsbstide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wsbstide.model.TidePoint
import com.example.wsbstide.ui.TideChart
import com.example.wsbstide.ui.theme.WSBSTideTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WSBSTideTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    TideTestScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun TideTestScreen(
    modifier: Modifier = Modifier
) {

    val start = System.currentTimeMillis()

    val testPoints = listOf(
        TidePoint(
            timestampMillis = start,
            height = 0.2
        ),
        TidePoint(
            timestampMillis = start + 1_000,
            height = 0.4
        ),
        TidePoint(
            timestampMillis = start + 2_000,
            height = 0.9
        ),
        TidePoint(
            timestampMillis = start + 3_000,
            height = 1.5
        ),
        TidePoint(
            timestampMillis = start + 4_000,
            height = 1.0
        ),
        TidePoint(
            timestampMillis = start + 5_000,
            height = 0.5
        ),
        TidePoint(
            timestampMillis = start + 6_000,
            height = 0.15
        )
    )

    TideChart(
        points = testPoints,
        modifier = modifier
            .padding(16.dp)
    )
}