package com.wellmosphere.freedsp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.ui.theme.Numbers
import com.wellmosphere.freedsp.ui.theme.Palette

/**
 * One adjustable value: label, a tappable readout for typing an exact number, a slider over [toSlider]
 * space, and fine steppers. [parse] turns typed text ("8k", "-3.5") into a value or null.
 */
@Composable
fun ValueControl(
    label: String,
    value: Double,
    display: (Double) -> String,
    toSlider: (Double) -> Float,
    fromSlider: (Float) -> Double,
    step: (Double, Int) -> Double,
    parse: (String) -> Double?,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text(display(value), style = Numbers, color = Palette.Text,
                modifier = Modifier.clickable(enabled = enabled) { editing = true }.padding(horizontal = 6.dp, vertical = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stepper("−", enabled) { onChange(step(value, -1)) }
            Slider(
                value = toSlider(value), onValueChange = { onChange(fromSlider(it)) }, enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(activeTrackColor = Palette.Signal, thumbColor = Palette.Signal, inactiveTrackColor = Palette.Line),
            )
            Stepper("+", enabled) { onChange(step(value, 1)) }
        }
    }
    if (editing) ExactValueDialog(label, display(value), parse, onDone = { v -> editing = false; v?.let(onChange) })
}

@Composable
private fun Stepper(symbol: String, enabled: Boolean, onClick: () -> Unit) =
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp), shape = RoundedCornerShape(12.dp)) {
        Text(symbol, style = MaterialTheme.typography.titleMedium)
    }

@Composable
private fun ExactValueDialog(label: String, current: String, parse: (String) -> Double?, onDone: (Double?) -> Unit) {
    var text by remember { mutableStateOf(current.filter { it.isDigit() || it in ".-k" }) }
    val parsed = parse(text)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(label) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                supportingText = { Text(if (parsed == null) "Try something like 8k, -3.5 or 0.71" else "It will be kept within the cable's range") })
        },
        confirmButton = { TextButton(onClick = { onDone(parsed) }, enabled = parsed != null) { Text("Set") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/** "8k" → 8000, "1.5k" → 1500, "-3.5" → -3.5. */
fun parseNumber(text: String): Double? {
    val t = text.trim().lowercase().removeSuffix("hz").removeSuffix("db").trim()
    return if (t.endsWith("k")) t.dropLast(1).toDoubleOrNull()?.times(1000) else t.toDoubleOrNull()
}
