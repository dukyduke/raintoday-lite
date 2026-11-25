package net.chevillot.raintoday.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.chevillot.raintoday.data.RainSeries
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Graphe de la prévision minute par minute.
 *
 * - axe horizontal : heure locale, graduée toutes les 15 min ;
 * - axe vertical : intensité de 0 à 80 % de l'échelle du service (25 % = seuil de
 *   pluie), avec les repères « faible / modérée / forte » ;
 * - repère « maintenant » avec son heure, et zone passée légèrement grisée.
 */
@Composable
fun RainChart(
    series: RainSeries,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rainColor = colors.primary
    val gridColor = colors.onSurface.copy(alpha = 0.10f)
    val pastColor = colors.onSurface.copy(alpha = 0.04f)
    val nowColor = colors.onSurface.copy(alpha = 0.55f)
    val axisColor = colors.onSurfaceVariant
    val densityScope = LocalDensity.current

    val labelPaint = remember(axisColor, densityScope) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(densityScope) { 9.dp.toPx() }
            color = axisColor.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }
    val nowPaint = remember(rainColor, densityScope) {
        AndroidPaint().apply {
            isAntiAlias = true
            textSize = with(densityScope) { 10.dp.toPx() }
            color = rainColor.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val zone = ZoneId.systemDefault()
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val gutterLeft = with(densityScope) { 42.dp.toPx() }
    val gutterBottom = with(densityScope) { 16.dp.toPx() }
    val gutterTop = with(densityScope) { 14.dp.toPx() }
    val textGap = with(densityScope) { 4.dp.toPx() }

    Canvas(modifier) {
        if (series.size < 2) return@Canvas

        val plotLeft = gutterLeft
        val plotRight = size.width
        val plotTop = gutterTop
        val plotBottom = size.height - gutterBottom
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val scale = 0.8f
        fun xAt(index: Int): Float = plotLeft + plotWidth * index / (series.size - 1).toFloat()
        fun yAt(fraction: Float): Float =
            plotBottom - (fraction.coerceIn(0f, 1f) / scale).coerceAtMost(1f) * plotHeight

        fun xAtTime(instant: Instant): Float {
            val span = series.intervalSeconds * (series.size - 1)
            if (span <= 0) return plotLeft
            val ratio = (instant.epochSecond - series.start.epochSecond).toFloat() / span.toFloat()
            return plotLeft + plotWidth * ratio.coerceIn(0f, 1f)
        }

        val nowIndex = series.indexAt(now)
        val nowX = xAt(nowIndex)
        val canvas = drawContext.canvas.nativeCanvas

        // Zone passée (avant « maintenant »)
        if (nowX > plotLeft) {
            drawRect(
                color = pastColor,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(nowX - plotLeft, plotHeight),
            )
        }

        // Grille horizontale + libellés d'intensité
        val levels = listOf(0.25f to "faible", 0.50f to "modérée", 0.75f to "forte")
        for ((fraction, label) in levels) {
            val y = yAt(fraction)
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
            val width = labelPaint.measureText(label)
            canvas.drawText(label, plotLeft - textGap - width, y + labelPaint.textSize / 3f, labelPaint)
        }
        drawLine(gridColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1f)

        // Graduations temporelles toutes les 15 min, alignées sur l'heure ronde
        var tick: ZonedDateTime = series.start.atZone(zone).withSecond(0).withNano(0)
        val minutesToQuarter = (15 - tick.minute % 15) % 15
        tick = tick.plusMinutes(minutesToQuarter.toLong())
        while (!tick.isAfter(series.lastTime.atZone(zone))) {
            val x = xAtTime(tick.toInstant())
            drawLine(gridColor, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1f)
            val label = timeFormat.format(tick)
            val width = labelPaint.measureText(label)
            val clampedX = (x - width / 2f).coerceIn(plotLeft, (plotRight - width).coerceAtLeast(plotLeft))
            canvas.drawText(label, clampedX, size.height - textGap / 2f, labelPaint)
            tick = tick.plusMinutes(15)
        }

        // Bande min/max
        val band = Path()
        for (i in 0 until series.size) {
            val x = xAt(i)
            val y = yAt(series.fraction(series.maxAt(i)))
            if (i == 0) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (i in series.size - 1 downTo 0) {
            band.lineTo(xAt(i), yAt(series.fraction(series.minAt(i))))
        }
        band.close()
        drawPath(band, color = rainColor.copy(alpha = 0.16f))

        // Courbe moyenne
        val line = Path()
        for (i in 0 until series.size) {
            val x = xAt(i)
            val y = yAt(series.fraction(series.avgAt(i)))
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        drawPath(
            path = line,
            color = rainColor,
            style = Stroke(width = 2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Repère « maintenant » + pastille sur la courbe + heure
        drawLine(
            color = nowColor,
            start = Offset(nowX, plotTop),
            end = Offset(nowX, plotBottom),
            strokeWidth = 1.2f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        val currentFraction = series.fraction(series.avgAt(nowIndex))
        drawCircle(rainColor, radius = 3.2f * density, center = Offset(nowX, yAt(currentFraction)))

        val nowLabel = "maintenant ${timeFormat.format(now.atZone(zone))}"
        val nowLabelWidth = nowPaint.measureText(nowLabel)
        val nowLabelX = (nowX - nowLabelWidth / 2f)
            .coerceIn(plotLeft, (plotRight - nowLabelWidth).coerceAtLeast(plotLeft))
        canvas.drawText(nowLabel, nowLabelX, plotTop - textGap / 2f, nowPaint)
    }
}

/** Durée couverte par la série (pour la légende). */
fun RainSeries.coverage(): Duration = Duration.ofSeconds(intervalSeconds * (size - 1).toLong())
