package dev.simpletuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.simpletuner.R
import dev.simpletuner.domain.InstrumentMode
import dev.simpletuner.domain.TuningPrecision
import dev.simpletuner.viewmodel.TunerStatus
import dev.simpletuner.viewmodel.TunerUiState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Mint = Color(0xFF43E0B0)
private val Amber = Color(0xFFF5B85B)
private val YellowGreen = Color(0xFFAAD461)
private val Red = Color(0xFFF25752)
private val Burgundy = Color(0xFFA62950)
private val Muted = Color(0xFF93A4B8)
private val Line = Color(0xFF344253)

private fun TuningPrecision.gaugeColors(): List<Pair<Float, Color>> {
    val greenCents = if (this == TuningPrecision.STANDARD) 10f else tunedCents.toFloat()
    return listOf(
        0f to Mint,
        greenCents / scaleCents to Mint,
        (if (this == TuningPrecision.STANDARD) tunedCents / scaleCents else 0.16).toFloat() to YellowGreen,
        0.24f to Amber,
        0.58f to Red,
        1f to Burgundy,
    )
}

private fun gaugeColor(colors: List<Pair<Float, Color>>, distance: Float): Color {
    val index = colors.indexOfFirst { it.first >= distance }.coerceAtLeast(1)
    val (start, from) = colors[index - 1]
    val (end, to) = colors[index]
    return lerp(from, to, ((distance - start) / (end - start)).coerceIn(0f, 1f))
}

@Composable
fun TunerScreen(state: TunerUiState, onMode: (InstrumentMode) -> Unit,
                onPrecision: (TuningPrecision) -> Unit,
                onPermission: () -> Unit, onSettings: () -> Unit, onRetry: () -> Unit) {
    val tracking = (state.status as? TunerStatus.Tracking)?.pitch
    val isActive = state.status is TunerStatus.Listening || state.status is TunerStatus.Uncertain || tracking != null
    val isTuned = tracking?.stable == true
    val precision = state.precision
    val standard = precision == TuningPrecision.STANDARD
    val cents = tracking?.match?.cents ?: 0.0
    val steps = cents / precision.centsPerStep
    val rounded = if (steps < 0) -abs(steps).roundToInt() else steps.roundToInt()
    val limit = precision.scaleCents / precision.centsPerStep
    val target = (steps / limit).toFloat()
    val needle by animateFloatAsState(target.coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = if (standard) 1f else 0.85f,
            stiffness = if (standard) 100f else 180f), label = "needle")
    val colors = remember(precision) { precision.gaugeColors() }
    val accent = if (tracking == null) Muted else gaugeColor(colors, abs(needle).coerceIn(0f, 1f))

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 700.dp
            val gaugeHeight = (maxHeight * if (compact) 0.24f else 0.27f).coerceIn(130.dp, 235.dp)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if (isActive) Mint else Line, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(if (isActive) R.string.mic_active else R.string.mic_inactive),
                            color = Muted, fontSize = 9.sp, letterSpacing = 0.6.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeSelector(InstrumentMode.entries, state.mode, onMode) {
                        stringResource(if (it == InstrumentMode.GUITAR) R.string.guitar else R.string.bass)
                    }
                    ModeSelector(TuningPrecision.entries, precision, onPrecision) {
                        stringResource(if (it == TuningPrecision.STANDARD) R.string.standard_mode else R.string.precise_mode)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(targetState = tracking?.match?.string, label = "note") { string ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(string?.note ?: stringResource(R.string.no_note),
                                fontSize = if (compact) 66.sp else 82.sp, lineHeight = if (compact) 74.sp else 90.sp,
                                fontWeight = FontWeight.Light, color = if (string == null) Line else Color.White)
                            if (string != null) Text(string.octave.toString(), modifier = Modifier.padding(top = 13.dp, start = 3.dp),
                                fontSize = 22.sp, color = Muted)
                        }
                    }
                    Text(if (tracking == null) "— Hz" else stringResource(R.string.frequency_format, tracking.frequencyHz),
                        color = Muted, style = MaterialTheme.typography.bodyLarge, letterSpacing = 1.sp)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val description = stringResource(
                        if (standard) R.string.standard_scale_accessibility else R.string.scale_accessibility,
                    )
                    Gauge(needle, accent, colors, Modifier.fillMaxWidth().height(gaugeHeight)
                        .semantics { contentDescription = description })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("−$limit", color = Muted, fontSize = 11.sp)
                        Text("0", color = if (isTuned) Mint else Muted, fontSize = 11.sp)
                        Text("+$limit", color = Muted, fontSize = 11.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val measurement = when {
                        tracking == null -> stringResource(R.string.no_note)
                        standard -> if (rounded > 0) "+$rounded" else rounded.toString()
                        rounded == 0 -> stringResource(R.string.zero_cents)
                        else -> stringResource(R.string.cents_format, rounded)
                    }
                    Text(measurement,
                        color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    val statusText = when (val status = state.status) {
                        is TunerStatus.PermissionRequired -> if (status.permanentlyDenied) R.string.permission_settings else R.string.permission
                        TunerStatus.Starting -> R.string.starting
                        TunerStatus.Listening -> R.string.play_string
                        TunerStatus.Uncertain -> R.string.uncertain
                        TunerStatus.AudioUnavailable -> R.string.audio_error
                        is TunerStatus.Tracking -> when {
                            status.pitch.stable -> R.string.tuned
                            abs(status.pitch.match.cents) <= precision.tunedCents -> R.string.stabilizing
                            status.pitch.match.cents < 0 -> R.string.tighten
                            else -> R.string.loosen
                        }
                    }
                    Text(stringResource(statusText), color = if (isTuned) Mint else Muted,
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    when (val status = state.status) {
                        is TunerStatus.PermissionRequired -> Button(onClick = if (status.permanentlyDenied) onSettings else onPermission) {
                            Text(stringResource(if (status.permanentlyDenied) R.string.open_settings else R.string.allow))
                        }
                        TunerStatus.AudioUnavailable -> Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(if (compact) 0.dp else 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.standard_tuning), color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(stringResource(R.string.reference), color = Line, fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (string in state.mode.strings) {
                        val active = tracking?.match?.string == string
                        val description = stringResource(R.string.selected_string, string.number, string.note, string.octave)
                        Column(Modifier.weight(1f).border(1.dp, if (active) accent else Line.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .background(if (active) accent.copy(alpha = 0.09f) else Color(0xFF151D29), RoundedCornerShape(12.dp))
                            .semantics { contentDescription = description; selected = active }
                            .padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${string.note}${string.octave}", color = if (active) accent else Muted,
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(string.number.toString(), color = if (active) accent else Line, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ModeSelector(items: List<T>, current: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF151D29), RoundedCornerShape(14.dp)).padding(4.dp)) {
        for (item in items) {
            val selected = item == current
            Box(Modifier.weight(1f).background(if (selected) Color(0xFF283747) else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { onSelect(item) }.semantics { this.selected = selected }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center) {
                Text(label(item),
                    color = if (selected) Color.White else Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Gauge(position: Float, accent: Color, colors: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = minOf(size.width * 0.42f, size.height * 0.83f)
        val center = Offset(size.width / 2, size.height * 0.91f)
        drawCircle(brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.09f), Color.Transparent), center, radius),
            radius = radius, center = center)
        val arcColors = colors.asReversed().map { (distance, color) -> (0.75f - distance / 4f) to color } +
            colors.drop(1).map { (distance, color) -> (0.75f + distance / 4f) to color }
        drawArc(Brush.sweepGradient(*arcColors.toTypedArray(), center = center), 180f, 180f, false,
            topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        for (tick in 0..40) {
            val angle = (180.0 + tick * 4.5) * PI / 180.0
            val major = tick % 4 == 0
            val length = if (tick == 20) 15.dp.toPx() else if (major) 10.dp.toPx() else 5.dp.toPx()
            val outer = radius
            val inner = radius - length
            val color = gaugeColor(colors, abs(tick - 20) / 20f).copy(alpha = if (major) 1f else 0.6f)
            drawLine(color, Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (tick == 20) 2.5.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
        }
        val angle = (270.0 + position.coerceIn(-1f, 1f) * 90.0) * PI / 180.0
        val end = Offset(center.x + cos(angle).toFloat() * radius * 0.78f,
            center.y + sin(angle).toFloat() * radius * 0.78f)
        drawLine(accent.copy(alpha = 0.18f), center, end, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent, center, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color(0xFF151D29), 8.dp.toPx(), center)
        drawCircle(accent, 4.dp.toPx(), center)
    }
}
