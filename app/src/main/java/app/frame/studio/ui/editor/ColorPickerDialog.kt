package app.frame.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.frame.studio.text.ColorMath

/** Any opaque color: hue strip plus saturation/brightness square. */
@Composable
fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = remember(initial) { ColorMath.argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val current = ColorMath.hsvToArgb(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text color") },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SaturationValueSquare(hue, sat, value) { s, v -> sat = s; value = v }
                HueStrip(hue) { hue = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(Color(current.toInt()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text("#%06X".format(current and 0xFFFFFF), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
    )
}

@Composable
private fun SaturationValueSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val hueColor = Color(ColorMath.hsvToArgb(hue, 1f, 1f).toInt())
    fun pick(p: Offset, w: Float, h: Float) =
        onChange((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
    Canvas(
        Modifier.fillMaxWidth().heightIn(max = 200.dp).aspectRatio(1.4f).clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val c = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueStrip(hue: Float, onHue: (Float) -> Unit) {
    val stops = (0..6).map { Color(ColorMath.hsvToArgb(it * 60f, 1f, 1f).toInt()) }
    fun pick(x: Float, w: Float) = onHue((x / w).coerceIn(0f, 1f) * 359.9f)
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); pick(change.position.x, size.width.toFloat()) }
            },
    ) {
        drawRect(Brush.horizontalGradient(stops))
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
