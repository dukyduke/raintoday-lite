package net.chevillot.raintoday.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.chevillot.raintoday.MainViewModel

private enum class Tab(val label: String) {
    HOME("Pluie"),
    PLACES("Lieux"),
    ALERTS("Alertes"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RainTodayApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val context = LocalContext.current

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.addGpsPlace()
        } else {
            viewModel.reportError("Sans accès à la position, utilisez la recherche de lieu.")
        }
    }

    val addGps: () -> Unit = {
        if (hasLocationPermission(context)) viewModel.addGpsPlace()
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val current = state.place
                        Text(
                            text = when {
                                current == null -> "Rain Today Lite"
                                current.isGps -> "Ma position"
                                else -> current.name
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = if (current?.isGps == true) {
                            listOf(
                                if (current.hasCoordinates) current.name else "position du téléphone",
                                current.areaId.orEmpty(),
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                        } else {
                            current?.label.orEmpty()
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (tab == Tab.HOME && state.place != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Rafraîchir")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    Tab.HOME -> Icons.Outlined.Cloud
                                    Tab.PLACES -> Icons.Outlined.Place
                                    Tab.ALERTS -> Icons.Outlined.Notifications
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onDismissError = viewModel::clearError,
                    onAddGps = addGps,
                    onGoToPlaces = { tab = Tab.PLACES },
                )
                Tab.PLACES -> PlacesScreen(
                    state = state,
                    onAddGps = addGps,
                    onSelect = viewModel::selectPlace,
                    onAddPlace = viewModel::addPlace,
                    onRemovePlace = viewModel::removePlace,
                    onSearch = viewModel::search,
                    onClearSearch = viewModel::clearSearch,
                    results = viewModel.results.collectAsStateWithLifecycle().value,
                )
                Tab.ALERTS -> AlertsScreen(
                    state = state,
                    onSave = viewModel::saveAlert,
                    onDelete = viewModel::deleteAlert,
                    onToggle = viewModel::setAlertEnabled,
                )
            }
        }
    }
}
