package com.example.lab1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.lab1.analytics.AnalyticsScreen
import com.example.lab1.analytics.AnalyticsViewModel
import com.example.lab1.flashlight.FlashlightScreen
import com.example.lab1.flashlight.FlashlightViewModel

/** Two tabs without a navigation library: the tab index is the only navigation state. */
@Composable
fun AppRoot(flashlight: FlashlightViewModel, analytics: AnalyticsViewModel, onRequestCamera: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0},
                    icon = { TabIcon(chart = false) }, label = { Text("Control") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { TabIcon(chart = true) }, label = { Text("Analytics") })
            }
        },
    ) { padding ->
        // The screens add safeDrawingPadding themselves, so the insets used by the bars are marked as consumed.
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                0 -> FlashlightScreen(flashlight, onRequestCamera)
                else -> AnalyticsScreen(analytics)
            }
        }
    }
}

@Composable
private fun TabIcon(chart: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        if (chart) {
            val path = Path().apply {
                moveTo(size.width * 0.05f, size.height * 0.75f)
                lineTo(size.width * 0.35f, size.height * 0.35f)
                lineTo(size.width * 0.6f, size.height * 0.6f)
                lineTo(size.width * 0.95f, size.height * 0.15f)
            }
            drawPath(path, color, style = stroke)
        } else {
            drawCircle(color, radius = size.minDimension * 0.42f, style = stroke)
            drawLine(color, Offset(size.width / 2, size.height * 0.28f), Offset(size.width / 2, size.height * 0.55f),
                strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
