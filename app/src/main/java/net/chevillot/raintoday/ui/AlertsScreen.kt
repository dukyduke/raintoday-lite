package net.chevillot.raintoday.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.chevillot.raintoday.UiState
import net.chevillot.raintoday.data.AlertRule
import net.chevillot.raintoday.data.Place
import net.chevillot.raintoday.data.allPlaces
import net.chevillot.raintoday.domain.DAY_LETTERS
import net.chevillot.raintoday.domain.daysLabel
import net.chevillot.raintoday.work.AlertScheduler
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

private val ZONE: ZoneId = ZoneId.systemDefault()

private fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
fun AlertsScreen(
    state: UiState,
    onSave: (AlertRule) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (AlertRule, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<AlertRule?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    val places = state.settings.allPlaces()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showDialog = true
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Ajouter") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!notificationsGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Autorisez les notifications",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sans notification, les alertes ne peuvent pas s'afficher.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            ) { Text("Autoriser") }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Plages horaires d'alerte",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { AlertScheduler.runNow(context) },
                        enabled = state.settings.alerts.isNotEmpty(),
                    ) { Text("Tester maintenant") }
                }
            }

            if (places.isEmpty()) {
                item {
                    Text(
                        "Ajoutez d'abord un lieu (onglet Lieux) pour créer une plage d'alerte.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.settings.alerts.isEmpty()) {
                item {
                    Text(
                        "Aucune plage. Exemple : « en semaine 07:00–08:30 » pour être prévenu " +
                            "si de la pluie est attendue pendant votre trajet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.settings.alerts, key = { it.id }) { rule ->
                val place = places.firstOrNull { it.id == rule.placeId }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editing = rule
                            showDialog = true
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    place == null -> "Lieu supprimé"
                                    place.isGps -> "Ma position"
                                    else -> place.name
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "${rule.daysLabel()} · ${hhmm(rule.startMinute)}–${hhmm(rule.endMinute)}" +
                                    " · prévenir ${rule.leadMinutes} min avant",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule, it) },
                        )
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showDialog) {
        AlertRuleDialog(
            initial = editing,
            places = places,
            defaultPlaceId = state.settings.selectedId ?: places.firstOrNull()?.id,
            onDismiss = { showDialog = false },
            onSave = { rule ->
                onSave(rule)
                showDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertRuleDialog(
    initial: AlertRule?,
    places: List<Place>,
    defaultPlaceId: String?,
    onDismiss: () -> Unit,
    onSave: (AlertRule) -> Unit,
) {
    val context = LocalContext.current
    var placeId by remember { mutableStateOf(initial?.placeId ?: defaultPlaceId) }
    var daysMask by remember { mutableIntStateOf(initial?.daysMask ?: 0b0011111) }
    var startMinute by remember { mutableIntStateOf(initial?.startMinute ?: 7 * 60) }
    var endMinute by remember { mutableIntStateOf(initial?.endMinute ?: 8 * 60 + 30) }
    var leadMinutes by remember { mutableIntStateOf(initial?.leadMinutes ?: 15) }
    var placeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle plage d'alerte" else "Modifier la plage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { placeMenu = true }) {
                        val chosen = places.firstOrNull { it.id == placeId }
                        Text(
                            when {
                                chosen == null -> "Choisir un lieu"
                                chosen.isGps -> "Ma position"
                                else -> chosen.name
                            }
                        )
                    }
                    DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                        places.forEach { place ->
                            DropdownMenuItem(
                                text = { Text(if (place.isGps) "Ma position" else place.name) },
                                onClick = {
                                    placeId = place.id
                                    placeMenu = false
                                },
                            )
                        }
                    }
                }

                Text("Jours", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { index, letter ->
                        FilterChip(
                            selected = daysMask and (1 shl index) != 0,
                            onClick = { daysMask = daysMask xor (1 shl index) },
                            label = { Text(letter) },
                        )
                    }
                }

                Text("Plage horaire", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SuggestionChip(
                        onClick = {
                            startMinute = 7 * 60
                            endMinute = 9 * 60
                        },
                        label = { Text("Matin 7–9") },
                    )
                    SuggestionChip(
                        onClick = {
                            startMinute = 17 * 60
                            endMinute = 19 * 60
                        },
                        label = { Text("Soir 17–19") },
                    )
                    SuggestionChip(
                        onClick = {
                            val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                            startMinute = nowMinutes
                            endMinute = (nowMinutes + 120) % (24 * 60)
                            leadMinutes = 0
                        },
                        label = { Text("Maintenant") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> startMinute = hour * 60 + minute },
                                startMinute / 60,
                                startMinute % 60,
                                true,
                            ).show()
                        }
                    ) { Text("Début ${hhmm(startMinute)}") }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> endMinute = hour * 60 + minute },
                                endMinute / 60,
                                endMinute % 60,
                                true,
                            ).show()
                        }
                    ) { Text("Fin ${hhmm(endMinute)}") }
                }

                Text(
                    "Prévenir jusqu'à $leadMinutes min avant le début",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = leadMinutes.toFloat(),
                    onValueChange = { value -> leadMinutes = (value / 5f).roundToInt() * 5 },
                    valueRange = 0f..30f,
                    steps = 5,
                )

                Text(
                    "L'alerte se déclenche si de la pluie est prévue pendant la plage " +
                        "(prévision à 1 min, contrôle toutes les 15 min en arrière-plan).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = placeId != null && daysMask != 0,
                onClick = {
                    val id = placeId ?: return@TextButton
                    onSave(
                        AlertRule(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            placeId = id,
                            daysMask = daysMask,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            leadMinutes = leadMinutes,
                            enabled = initial?.enabled ?: true,
                            lastNotifiedAt = initial?.lastNotifiedAt ?: 0L,
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
