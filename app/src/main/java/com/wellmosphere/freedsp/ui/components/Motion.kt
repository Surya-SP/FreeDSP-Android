package com.wellmosphere.freedsp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** The app's one press feel: a quick spring down to 96% and back, with a ripple. Clip the modifier first. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.springClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = ripple(), onLongClick = onLongClick, onClick = onClick)
}

/** A soft band of light sweeping left to right, for "working on it" surfaces. */
@Composable
fun rememberShimmer(base: Color, light: Color): Brush {
    val t by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    return Brush.linearGradient(listOf(base, light, base), start = Offset(t * 900f - 300f, 0f), end = Offset(t * 900f + 300f, 0f))
}

/** 0.35…1 and back, about once every two seconds: a calm "alive" pulse. */
@Composable
fun rememberBreath(): Float {
    val a by rememberInfiniteTransition(label = "breath")
        .animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha")
    return a
}
