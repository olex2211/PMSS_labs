package com.example.lab1.flashlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab1.helpers.ble.ConnectionStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lab1.helpers.ShakeDetector
import com.example.lab1.ui.theme.Lab1Theme
import java.util.Locale

private fun number(value: Float, decimals: Int = 2) = String.format(Locale.US, "%.${decimals}f", value)

@Composable
fun FlashlightScreen(
    vm: FlashlightViewModel,
    onRequestCamera: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentRequest by rememberUpdatedState(onRequestCamera)
    LaunchedEffect(vm, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.events.collect { event ->
                when (event) {
                    is FlashlightEvent.ShowMessage -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                    FlashlightEvent.RequestCamera -> currentRequest()
                }
            }
        }
    }
    FlashlightContent(state, vm::onAction)
}

@Composable
fun FlashlightContent(state: FlashlightUiState, onAction: (FlashlightAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val needsPermission = !state.torch.simulated && !state.cameraPermission
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LAB 02  /  VARIANT 16", color = colors.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Emergency\nFlashlight", fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp)
                Text("Shake to turn on the light", color = colors.onSurfaceVariant)
            }

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LampGraphic(state.torch.enabled, Modifier.size(76.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (state.torch.enabled) "Light is on" else "Light is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (state.torch.simulated) "Simulation · device has no flash" else "Camera flash", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    if (needsPermission) {
                        Text("Grant camera access to control the hardware flashlight.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onAction(FlashlightAction.RequestCamera) }, modifier = Modifier.fillMaxWidth()) { Text("Grant camera access") }
                    } else {
                        Button(onClick = { onAction(FlashlightAction.ToggleLight) }, enabled = state.torch.available, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.torch.enabled) "Turn off flashlight" else "Turn on flashlight")
                        }
                    }
                    state.torch.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Turn on automatically when shaken", style = MaterialTheme.typography.titleSmall)
                            Text("Only while the app is open", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Switch(checked = state.automatic, onCheckedChange = { onAction(FlashlightAction.AutomaticChanged(it)) }, enabled = state.sensorAvailable,
                            modifier = Modifier.semantics { contentDescription = "Turn on automatically when shaken" })
                    }
                }
            }

            BlePanel(state, onAction)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Accelerometer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(when {
                    !state.sensorAvailable -> "Unavailable"
                    state.sensorActive -> "● Live"
                    else -> "Paused"
                }, color = if (state.sensorActive) colors.primary else colors.error, style = MaterialTheme.typography.labelMedium)
            }
            if (!state.sensorAvailable) {
                Text("This device has no accelerometer. Use an emulator with Virtual Sensors support.", color = colors.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AxisValue("X", state.x, state.hasReading, Color(0xFFFFB69B), Modifier.weight(1f))
                AxisValue("Y", state.y, state.hasReading, Color(0xFFBCF36B), Modifier.weight(1f))
                AxisValue("Z", state.z, state.hasReading, Color(0xFF94CFF6), Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Acceleration magnitude", style = MaterialTheme.typography.titleSmall)
                        Text(if (state.hasReading) "${number(state.magnitudeG)} g" else "— g", color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    AccelerationMeter(state.magnitudeG, state.thresholdG)
                    Text("At rest ≈ 1 g · peak ${number(state.peakG)} g", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text("Shake threshold: ${number(state.thresholdG, 1)} g", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = state.thresholdG,
                        onValueChange = { onAction(FlashlightAction.ThresholdChanged(it)) },
                        onValueChangeFinished = { onAction(FlashlightAction.SaveThreshold) },
                        valueRange = ShakeDetector.MIN_THRESHOLD_G..ShakeDetector.MAX_THRESHOLD_G,
                        steps = 19,
                        modifier = Modifier.semantics { contentDescription = "Shake threshold" },
                    )
                    Text("Tilting alone does not turn on the light. Return the phone to rest after a shake; another shake can trigger after 1 s.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Shakes: ${state.shakeCount}", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onAction(FlashlightAction.ClearShakes) }, enabled = state.shakeCount > 0) { Text("Clear") }
                    }
                    if (state.events.isEmpty()) {
                        Text("Waiting for the first shake", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.events.forEach { event ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(event.time, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                                Text("Shake · ${number(event.magnitudeG)} g", color = colors.primary)
                            }
                        }
                    }
                }
            }
            Text("Sensor readings stop and the flashlight turns off when the app goes into the background.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(state.sensorName, style = MaterialTheme.typography.labelSmall, color = colors.outline)
        }
    }
}

@Composable
private fun AxisValue(axis: String, value: Float, hasReading: Boolean, color: Color, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(axis, color = color, fontWeight = FontWeight.Bold)
        Text(if (hasReading) number(value) else "—", fontSize = 21.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text("m/s²", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Signed bars also react to tilt while the total acceleration stays near 1 g.
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(color.copy(alpha = 0.16f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width / 2 + size.width / 2 * (value / 20f).coerceIn(-1f, 1f), size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AccelerationMeter(value: Float, threshold: Float) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(22.dp).semantics { contentDescription = "Acceleration ${number(value)} g, threshold ${number(threshold)} g" }) {
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
        if (value > 0) drawLine(color, Offset(0f, y), Offset(size.width * (value / 4f).coerceIn(0f, 1f), y), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
        val x = size.width * threshold / 4f
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun LampGraphic(enabled: Boolean, modifier: Modifier) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics { contentDescription = if (enabled) "Light indicator is on" else "Light indicator is off" }) {
        val u = size.minDimension
        drawCircle(color.copy(alpha = if (enabled) 0.17f else 0.08f))
        drawRoundRect(color, Offset(u * 0.37f, u * 0.43f), Size(u * 0.26f, u * 0.33f), style = Stroke(u * 0.035f))
        drawLine(color, Offset(u * 0.29f, u * 0.32f), Offset(u * 0.71f, u * 0.32f), u * 0.06f, StrokeCap.Round)
        drawLine(color, Offset(u * 0.29f, u * 0.32f), Offset(u * 0.37f, u * 0.43f), u * 0.035f)
        drawLine(color, Offset(u * 0.71f, u * 0.32f), Offset(u * 0.63f, u * 0.43f), u * 0.035f)
        drawCircle(color, u * 0.03f, Offset(u * 0.5f, u * 0.55f))
        if (enabled) {
            for (i in -1..1) {
                drawLine(color, Offset(u * (0.5f + i * 0.14f), u * 0.21f), Offset(u * (0.5f + i * 0.18f), u * 0.12f), u * 0.035f, StrokeCap.Round)
            }
        }
    }
}

@Preview(showBackground = true, locale = "en", widthDp = 393, heightDp = 1400)
@Composable
private fun FlashlightPreview() {
    Lab1Theme {
        FlashlightContent(FlashlightUiState(z = 9.81f, magnitudeG = 1f, hasReading = true, sensorActive = true)) {}
    }
}

@Composable
private fun BlePanel(state: FlashlightUiState, onAction: (FlashlightAction) -> Unit) {
    val connected = state.connection == ConnectionStatus.Connected
    val idle = state.connection == ConnectionStatus.Disconnected
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BLE · virtual smart hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Mock service · Bluetooth radio is not used", style = MaterialTheme.typography.bodySmall)
            Text(when {
                state.scanning -> "Scanning · searching for devices"
                connected && state.transferring -> "Connected · Data Transfer"
                else -> state.connection.name
            }, color = MaterialTheme.colorScheme.primary)
            if (state.scanning || state.connection == ConnectionStatus.Connecting || state.transferring) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(onClick = { onAction(FlashlightAction.Scan) }, enabled = idle && !state.scanning,
                modifier = Modifier.fillMaxWidth()) { Text("Search for devices") }
            state.devices.forEach { device ->
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Text(device.id, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onAction(FlashlightAction.Connect(device)) },
                    enabled = idle && !state.scanning) { Text("Connect") }
            }
            if (state.connection != ConnectionStatus.Disconnected) {
                OutlinedButton(onClick = { onAction(FlashlightAction.Disconnect) }) { Text("Disconnect") }
            }
            Text("Hub state: " + when (state.remoteLight) { true -> "ON"; false -> "OFF"; null -> "unknown" })
            val ready = connected && !state.transferring
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(FlashlightAction.SendLight(true)) }, enabled = ready) { Text("BLE ON") }
                Button(onClick = { onAction(FlashlightAction.SendLight(false)) }, enabled = ready) { Text("BLE OFF") }
            }
            OutlinedButton(onClick = { onAction(FlashlightAction.ReadStatus) }, enabled = ready) { Text("Read status") }
            OutlinedButton(onClick = { onAction(FlashlightAction.SendReading) }, enabled = ready && state.hasReading) {
                Text("Send current reading")
            }
            Text("A shake automatically sends X/Y/Z and g to the connected hub. BLE ON/OFF returns a notification and controls this flashlight.",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BLE terminal", fontWeight = FontWeight.Bold)
                TextButton(onClick = { onAction(FlashlightAction.ClearTerminal) }) { Text("Clear terminal") }
            }
            if (state.terminal.isEmpty()) Text("No data exchanged yet", style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                state.terminal.asReversed().forEach { entry ->
                    Text("${entry.time} ${entry.message}", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
    }
}
