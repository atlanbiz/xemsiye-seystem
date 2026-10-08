package com.solarpulse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.solarpulse.app.ui.theme.SolarTheme

/** Circular progress ring with centred content (web `Ring`). [value] is 0..100. */
@Composable
fun Ring(
    value: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val target = (value.coerceIn(0.0, 100.0) / 100.0).toFloat()
    val progress by animateFloatAsState(target, animationSpec = tween(900, easing = FastOutSlowInEasing), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(color, -90f, 360f * progress, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Tiny trend line (web `Sparkline`); mirrors itself in RTL like the rest of the layout. */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull() ?: 0.0
        val min = values.minOrNull() ?: 0.0
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val w = size.width
        val h = size.height
        val pad = 2.dp.toPx()
        fun x(i: Int): Float {
            val t = i / (values.size - 1).toFloat()
            return if (rtl) w - t * w else t * w
        }
        fun y(v: Double): Float = (h - pad - ((v - min) / span) * (h - 2 * pad)).toFloat()
        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(x(values.size - 1), h)
                lineTo(x(0), h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        }
        drawPath(line, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Horizontal health/efficiency bar with a percentage (web `HealthBar`). */
@Composable
fun HealthBar(value: Double, modifier: Modifier = Modifier, label: String? = null) {
    val color = com.solarpulse.app.ui.healthColor(value)
    val p by animateFloatAsState((value / 100).toFloat().coerceIn(0f, 1f), tween(700), label = "health")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label ?: "${value.toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
        )
    }
}

/** Animated shimmer for skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    this.background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x * 600f, 0f),
            end = Offset(x * 600f + 400f, 200f),
        ),
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())
}

/** Generic loading skeleton: hero + KPI tiles + cards. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(150.dp), 26.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(84.dp))
            SkeletonBlock(Modifier.weight(1f).height(84.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(84.dp))
            SkeletonBlock(Modifier.weight(1f).height(84.dp))
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(220.dp), 22.dp)
        SkeletonBlock(Modifier.fillMaxWidth().height(160.dp), 22.dp)
    }
}

/** Friendly empty state with an icon in a soft circle. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

/** Donut chart (share by site type). */
@Composable
fun Donut(values: List<Double>, colors: List<Color>, modifier: Modifier = Modifier, stroke: Dp = 22.dp) {
    val total = values.sum().takeIf { it > 0 } ?: 1.0
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val sw = stroke.toPx()
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        val gap = if (values.count { it > 0 } > 1) 2f else 0f
        values.forEachIndexed { i, v ->
            val sweep = (360f * (v / total).toFloat()) * anim.value
            if (sweep > gap) drawArc(colors[i % colors.size], start + gap / 2, sweep - gap, false, tl, Size(d, d), style = Stroke(sw))
            start += sweep
        }
    }
}

/** Small coloured legend dot + label. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Haptic tick for key actions (save, mark paid, swipe). */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

/** Muted text colour helper. */
@Composable
fun mutedColor(): Color = SolarTheme.colors.subtle
