package net.chevillot.raintoday.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import net.chevillot.raintoday.data.RainSeries
import java.time.Instant

/**
 * Graphe de la prévision minute par minute : bande min/max, courbe moyenne,
 * repère « maintenant ». L'échelle verticale sature à 80 % de l'intensité max.
 */
@Composable
fun RainChart(
    series: RainSeries,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val rainColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val nowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val width = size.width
        val height = size.height
        val scale = 0.8f

        fun xAt(index: Int): Float = width * index / (series.size - 1).toFloat()
        fun yAt(fraction: Float): Float =
            height - (fraction.coerceIn(0f, 1f) / scale).coerceAtMost(1f) * height

        // Grille horizontale (25 / 50 / 75 %)
        for (level in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, yAt(level)),
                end = Offset(width, yAt(level)),
                strokeWidth = 1f,
            )
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
            style = Stroke(
                width = 2.5f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Repère « maintenant »
        val nowX = xAt(series.indexAt(now))
        drawLine(
            color = nowColor,
            start = Offset(nowX, 0f),
            end = Offset(nowX, height),
            strokeWidth = 1.2f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}
