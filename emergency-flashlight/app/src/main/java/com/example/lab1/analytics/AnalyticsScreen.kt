package com.example.lab1.analytics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun number(value: Float, decimals: Int = 2) = String.format(Locale.US, "%.${decimals}f", value)

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.onAction(AnalyticsAction.Opened) }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AnalyticsEvent.ShowMessage -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    AnalyticsContent(state, vm::onAction)
}

@Composable
fun AnalyticsContent(state: AnalyticsState, onAction: (AnalyticsAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LAB 05  /  ANALYTICS", color = colors.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("History", fontWeight = FontWeight.Bold, fontSize = 34.sp)
                Text("Newest readings stored in Room", color = colors.onSurfaceVariant)
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Acceleration magnitude, g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.points.isEmpty()) {
                        Text("No readings yet. Open the Control tab and move the device.", color = colors.onSurfaceVariant)
                    } else {
                        AccelerationChart(state.points, Modifier.fillMaxWidth().height(240.dp))
                    }
                    Text("Last ${state.points.size} points, one per second, plus every shake. Shake threshold: ${number(state.thresholdG, 1)} g.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatRow("Minimum / maximum", "${number(state.minG)} / ${number(state.maxG)} g")
                    StatRow("Average", "${number(state.averageG)} g")
                    StatRow("Shakes in the window", state.shakesInWindow.toString())
                    StatRow("Rows in the database", state.totalRecords.toString())
                    StatRow("Waiting for the cloud", state.pending.toString())
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Actuator events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.events.isEmpty()) {
                        Text("No events yet", style = MaterialTheme.typography.bodySmall)
                    }
                    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    state.events.forEach { event ->
                        Text("${format.format(Date(event.timestamp))}  ${event.action} (${event.source})",
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedButton(onClick = { onAction(AnalyticsAction.ClearHistory) }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear history")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Line chart of the newest points. X is the point index (equal spacing); labels show the clock time. */
@Composable
private fun AccelerationChart(points: List<ChartPoint>, modifier: Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction { lineSeries { series(points.map { it.value }) } }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeLabels = remember(points) {
        CartesianValueFormatter { _, x, _ ->
            points.getOrNull(x.toInt())?.let { timeFormat.format(Date(it.timestamp)) } ?: ""
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0)),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = timeLabels),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        // The chart always shows the whole window of 50 points, so scrolling and zooming are switched off.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
    )
}
