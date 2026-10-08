package com.solarpulse.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.stacked
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.compose.common.shape.rounded
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/*
 * Thin wrappers around Vico 2.1 so screens only pass data + formatters. Charts are drawn
 * left-to-right in every language (like the web's dir="ltr" chart containers).
 */

/** One data series: x values (numeric) and y values of the same length. */
data class Series(val xs: List<Double>, val ys: List<Double>, val color: Color, val dashed: Boolean = false)

@Composable
private fun rememberMarker(valueText: (Double) -> String): CartesianMarker {
    val formatter = rememberUpdatedState(valueText)
    val label = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface,
        textSize = 12.sp,
        padding = insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(
            fill = fill(MaterialTheme.colorScheme.surface),
            shape = CorneredShape.rounded(all = 8.dp),
            strokeFill = fill(MaterialTheme.colorScheme.outlineVariant),
            strokeThickness = 1.dp,
        ),
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = remember {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val values = targets.flatMap { t ->
                    when (t) {
                        is LineCartesianLayerMarkerTarget -> t.points.map { it.entry.y }
                        is ColumnCartesianLayerMarkerTarget -> t.columns.map { it.entry.y }
                        else -> emptyList()
                    }
                }
                values.joinToString(" · ") { formatter.value(it) }
            }
        },
        guideline = rememberAxisGuidelineComponent(),
    )
}

@Composable
private fun formatter(f: (Double) -> String): CartesianValueFormatter {
    val current = rememberUpdatedState(f)
    return remember { CartesianValueFormatter { _, value, _ -> current.value(value) } }
}

/**
 * Line / area chart. [minX]/[maxX] pin the x range (e.g. 0–24 h for a daily curve);
 * [labelSpacing] shows every n-th x label.
 */
@Composable
fun LineChart(
    series: List<Series>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    area: Boolean = true,
    minX: Double? = null,
    maxX: Double? = null,
    minY: Double? = null,
    labelSpacing: Int = 1,
    markerText: (Double) -> String = yLabel,
    height: Int = 200,
) {
    val data = series.filter { it.xs.isNotEmpty() && it.xs.size == it.ys.size }
    if (data.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            lineSeries { data.forEach { s -> series(s.xs, s.ys) } }
        }
    }
    val lines = data.map { s ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(s.color)),
            stroke = if (s.dashed) LineCartesianLayer.LineStroke.Dashed() else LineCartesianLayer.LineStroke.Continuous(),
            areaFill = if (area && !s.dashed) {
                LineCartesianLayer.AreaFill.single(
                    fill(ShaderProvider.verticalGradient(arrayOf(s.color.copy(alpha = 0.38f), s.color.copy(alpha = 0f)))),
                )
            } else {
                null
            },
            pointConnector = LineCartesianLayer.PointConnector.cubic(),
        )
    }
    val range = remember(minX, maxX, minY) { CartesianLayerRangeProvider.fixed(minX = minX, maxX = maxX, minY = minY) }
    val layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(lines),
        rangeProvider = range,
    )
    ChartHost(
        layers = { rememberCartesianChartFor(layer, xLabel, yLabel, labelSpacing, markerText) },
        producer = producer,
        modifier = modifier.fillMaxWidth().height(height.dp),
    )
}

/** Column chart over categories 0..n-1; [stacked] stacks several series (paid / open revenue). */
@Composable
fun ColumnChart(
    series: List<List<Double>>,
    colors: List<Color>,
    xLabel: (Int) -> String,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    labelSpacing: Int = 1,
    markerText: (Double) -> String = yLabel,
    height: Int = 200,
) {
    val data = series.filter { it.isNotEmpty() }
    if (data.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            columnSeries { data.forEach { s -> series(s) } }
        }
    }
    val columns = data.indices.map { i ->
        rememberLineComponent(
            fill = fill(colors[i % colors.size]),
            thickness = if (data.first().size > 20) 6.dp else 14.dp,
            shape = CorneredShape.rounded(all = 4.dp),
        )
    }
    val layer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
        mergeMode = { if (stacked) ColumnCartesianLayer.MergeMode.stacked() else ColumnCartesianLayer.MergeMode.Grouped() },
    )
    val xFormat: (Double) -> String = { v -> xLabel(v.toInt()) }
    ChartHost(
        layers = { rememberCartesianChartFor(layer, xFormat, yLabel, labelSpacing, markerText) },
        producer = producer,
        modifier = modifier.fillMaxWidth().height(height.dp),
    )
}

@Composable
private fun rememberCartesianChartFor(
    layer: com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayer<*>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    labelSpacing: Int,
    markerText: (Double) -> String,
): com.patrykandpatrick.vico.core.cartesian.CartesianChart {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    return rememberCartesianChart(
        layer,
        startAxis = VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = labelColor, textSize = 10.sp),
            valueFormatter = formatter(yLabel),
            itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 4 }) },
            line = null,
            tick = null,
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = labelColor, textSize = 10.sp),
            valueFormatter = formatter(xLabel),
            itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            guideline = null,
            tick = null,
        ),
        marker = rememberMarker(markerText),
    )
}

@Composable
private fun ChartHost(
    layers: @Composable () -> com.patrykandpatrick.vico.core.cartesian.CartesianChart,
    producer: CartesianChartModelProducer,
    modifier: Modifier,
) {
    // Charts read left-to-right in every language.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CartesianChartHost(
            chart = layers(),
            modelProducer = producer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            animateIn = true,
        )
    }
}
