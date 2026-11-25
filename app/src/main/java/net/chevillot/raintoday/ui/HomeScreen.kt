package net.chevillot.raintoday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.chevillot.raintoday.UiState
import net.chevillot.raintoday.domain.RainLevel
import net.chevillot.raintoday.domain.RainState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZONE: ZoneId = ZoneId.systemDefault()
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE)

@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onAddGps: () -> Unit,
    onGoToPlaces: () -> Unit,
) {
    if (state.place == null) {
        NoPlaceYet(state, onAddGps, onGoToPlaces, onDismissError)
        return
    }

    val series = state.series
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusCard(state)

        state.error?.let { message ->
            ErrorCard(message, onRetry = onRefresh, onDismiss = onDismissError)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prévision minute par minute", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (series != null) {
                    RainChart(
                        series = series,
                        now = state.now,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ChartLabel(TIME_FORMAT.format(series.start))
                        ChartLabel("maintenant", MaterialTheme.colorScheme.primary)
                        ChartLabel(TIME_FORMAT.format(series.lastTime))
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loading) CircularProgressIndicator()
                        else Text(
                            "Aucune donnée de pluie",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        LegendCard(state)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRefresh) { Text("Actualiser") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChartLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun StatusCard(state: UiState) {
    val outlook = state.outlook
    val (title, subtitle) = when (val s = outlook?.state) {
        null -> "Chargement…" to "Récupération de la prévision"
        RainState.Dry -> "Pas de pluie" to "Rien de prévu dans l'heure qui vient"
        is RainState.Raining -> "Il pleut" to "Encore environ ${s.remainingMinutes} min"
        is RainState.Starting -> "Pluie dans ${s.inMinutes} min" to "Environ ${s.durationMinutes} min de pluie"
    }

    val level = outlook?.level ?: RainLevel.NONE
    val wet = outlook?.isWet == true
    val container = when {
        !wet -> MaterialTheme.colorScheme.surfaceVariant
        level == RainLevel.HEAVY -> RainColors.heavy
        level == RainLevel.MODERATE -> RainColors.moderate
        else -> RainColors.light
    }
    val onContainer = when {
        !wet || level == RainLevel.LIGHT -> MaterialTheme.colorScheme.onSurface
        else -> Color.White
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (wet) {
                    val levelText = when (level) {
                        RainLevel.HEAVY -> "Forte pluie"
                        RainLevel.MODERATE -> "Pluie modérée"
                        else -> "Pluie faible"
                    }
                    Surface(
                        color = onContainer.copy(alpha = 0.15f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = levelText,
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                state.updatedAt?.let { updated ->
                    Surface(color = onContainer.copy(alpha = 0.12f), shape = CircleShape) {
                        Text(
                            text = "Actualisé à ${TIME_FORMAT.format(updated)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCard(state: UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Intensité", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(RainColors.light, "Légère")
                LegendDot(RainColors.moderate, "Modérée")
                LegendDot(RainColors.heavy, "Forte")
            }
            Spacer(Modifier.height(12.dp))
            val area = state.place?.areaId ?: state.series?.areaId
            Text(
                text = buildString {
                    append("Données Meteogroup · prévision à 1 min")
                    if (!area.isNullOrBlank()) append(" · zone $area")
                    if (state.place?.isGps == true) append(" · position GPS")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Fermer") }
                TextButton(onClick = onRetry) { Text("Réessayer") }
            }
        }
    }
}

@Composable
private fun NoPlaceYet(
    state: UiState,
    onAddGps: () -> Unit,
    onGoToPlaces: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Aucun lieu suivi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ajoutez votre position ou un lieu favori pour voir la pluie arriver.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddGps, enabled = !state.loading) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Utiliser ma position")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGoToPlaces) {
            Icon(Icons.Outlined.Place, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Chercher un lieu")
        }
        if (state.loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(message, onRetry = onAddGps, onDismiss = onDismissError)
        }
    }
}
