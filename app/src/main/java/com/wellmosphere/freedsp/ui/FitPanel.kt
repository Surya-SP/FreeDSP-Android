package com.wellmosphere.freedsp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.wellmosphere.freedsp.EqViewModel
import com.wellmosphere.freedsp.UiState
import com.wellmosphere.freedsp.store.CurveStore.Kind
import com.wellmosphere.freedsp.ui.components.ChoiceField
import com.wellmosphere.freedsp.ui.components.Panel
import com.wellmosphere.freedsp.ui.components.rememberShimmer
import com.wellmosphere.freedsp.ui.components.springClick
import com.wellmosphere.freedsp.ui.theme.Palette

/** Your IEM + a target → auto-fit. Both curves also show on the graph once picked. */
@Composable
fun FitPanel(state: UiState, vm: EqViewModel, onImport: (Kind) -> Unit) = Panel {
    Text("Match a target", style = MaterialTheme.typography.titleMedium)
    Text(
        "Add your IEM's measurement, pick a sound to aim for, and auto-fit finds the bands that get you there. " +
            "Measurements are on squig.link sites and in AutoEq's database; save one as text, then import it. " +
            "Above about 6 kHz measurements are rough, so trust your ears there.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceField(
            "Your IEM", state.measurement?.name, "Import its measurement", state.measurements.map { it.name }, "Import a measurement…",
            onPick = { vm.pickCurve(Kind.MEASUREMENT, it) }, onImport = { onImport(Kind.MEASUREMENT) },
            onRemove = { vm.deleteCurve(Kind.MEASUREMENT, it) }, removable = { true },
        )
        ChoiceField(
            "Target", state.target?.name, "Pick a target", state.targets.map { it.name }, "Import a target…",
            onPick = { vm.pickCurve(Kind.TARGET, it) }, onImport = { onImport(Kind.TARGET) },
            onRemove = { vm.deleteCurve(Kind.TARGET, it) }, removable = { !vm.isBuiltInTarget(it) },
        )
    }
    AnimatedVisibility(state.measurement != null && state.target != null) {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend("Target", Palette.Warm, dashed = true)
            Legend("Your IEM", Palette.Muted)
            Legend("With this EQ", Palette.Text)
        }
    }
    val ready = state.measurement != null && state.target != null && !state.fitting
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
            .background(if (state.fitting) rememberShimmer(Palette.Raised, Palette.Signal.copy(alpha = 0.3f))
                else androidx.compose.ui.graphics.SolidColor(if (ready) Palette.Signal.copy(alpha = 0.16f) else Palette.Raised))
            .then(if (ready) Modifier.springClick(onClick = vm::autoFit) else Modifier),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.fitting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Text)
        Text(if (state.fitting) "  Finding the best bands…" else "✦  Auto-fit to this target",
            style = MaterialTheme.typography.labelLarge, color = if (ready || state.fitting) Palette.Signal else Palette.Muted)
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean = false) = Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.width(18.dp).height(8.dp)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(start = 6.dp))
}
