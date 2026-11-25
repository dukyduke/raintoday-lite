package net.chevillot.raintoday.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.chevillot.raintoday.UiState
import net.chevillot.raintoday.data.Place
import net.chevillot.raintoday.data.allPlaces

@Composable
fun PlacesScreen(
    state: UiState,
    onAddGps: () -> Unit,
    onSelect: (String) -> Unit,
    onAddPlace: (Place) -> Unit,
    onRemovePlace: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    results: List<Place>,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val places = state.settings.allPlaces()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    onSearch(value)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Chercher un lieu") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddGps, enabled = !state.loading && !state.locating) {
                if (state.locating) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.locating) "Recherche de la position…" else "Actualiser ma position")
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (results.isNotEmpty()) {
                item {
                    Text(
                        "Résultats",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(results, key = { "result-${it.id}-${it.lat}" }) { candidate ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAddPlace(candidate)
                                query = ""
                                onClearSearch()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    candidate.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Outlined.Add, contentDescription = "Ajouter")
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }

            item {
                Text(
                    "Lieux suivis",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (places.isEmpty()) {
                item {
                    Text(
                        "Aucun lieu : utilisez la position GPS ou la recherche ci-dessus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(places, key = { it.id }) { place ->
                val selected = place.id == state.settings.selectedId
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .clickable { onSelect(place.id) }
                            .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(place.id) })
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (place.isGps) "Ma position" else place.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (place.isGps) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.MyLocation,
                                        contentDescription = "Position GPS",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(16.dp).height(16.dp),
                                    )
                                }
                            }
                            val detail = if (place.isGps) {
                                listOf(
                                    if (place.hasCoordinates) place.name else "position du téléphone",
                                    place.areaId.orEmpty(),
                                    place.updatedAt.frTime(),
                                )
                            } else {
                                listOf(place.label, place.areaId.orEmpty())
                            }.filter { it.isNotBlank() }.joinToString(" · ")
                            Text(
                                detail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (place.isGps) {
                            IconButton(onClick = onAddGps, enabled = !state.locating) {
                                if (state.locating) {
                                    CircularProgressIndicator(
                                        Modifier.width(18.dp).height(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.MyLocation,
                                        contentDescription = "Actualiser ma position",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { onRemovePlace(place.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Supprimer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
