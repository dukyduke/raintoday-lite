package net.chevillot.raintoday

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.chevillot.raintoday.data.AlertRule
import net.chevillot.raintoday.data.AppSettings
import net.chevillot.raintoday.data.GPS_ID
import net.chevillot.raintoday.data.MeteogroupApi
import net.chevillot.raintoday.data.Place
import net.chevillot.raintoday.data.RainRepository
import net.chevillot.raintoday.data.RainSeries
import net.chevillot.raintoday.data.SettingsRepository
import net.chevillot.raintoday.data.gpsPlace
import net.chevillot.raintoday.domain.Outlook
import net.chevillot.raintoday.domain.outlook
import net.chevillot.raintoday.platform.Fix
import net.chevillot.raintoday.platform.LocationHelper
import net.chevillot.raintoday.platform.distanceMeters
import net.chevillot.raintoday.work.AlertScheduler
import java.io.IOException
import java.time.Duration
import java.time.Instant

data class UiState(
    val settings: AppSettings = AppSettings(),
    val place: Place? = null,
    val series: RainSeries? = null,
    val outlook: Outlook? = null,
    val loading: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val now: Instant = Instant.now(),
    val updatedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "RainTodayLite"

    private val settingsRepo = SettingsRepository(app)
    private val api = MeteogroupApi()
    private val rainRepo = RainRepository(api, settingsRepo)
    private val location = LocationHelper(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<Place>>(emptyList())
    val results: StateFlow<List<Place>> = _results.asStateFlow()

    private var loadedKey: String? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        AlertScheduler.ensureScheduled(app)
        viewModelScope.launch {
            settingsRepo.flow.collect { settings ->
                val place = when (val id = settings.selectedId) {
                    null -> settings.gps?.takeIf { it.hasCoordinates } ?: settings.favourites.firstOrNull()
                    GPS_ID -> settings.gpsPlace()
                    else -> settings.favourites.firstOrNull { it.id == id }
                        ?: settings.favourites.firstOrNull()
                }
                _state.update { it.copy(settings = settings, place = place) }
                val key = keyOf(place)
                if (place != null && key != loadedKey) {
                    loadedKey = key
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                tick()
            }
        }
    }

    fun refresh() {
        val place = _state.value.place ?: return
        loadJob?.cancel()
        Log.d(TAG, "refresh ${place.name} / zone=${place.areaId}")
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, locating = place.isGps, error = null, lastAttemptAt = Instant.now()) }
            try {
                val target = if (place.isGps) currentPositionPlace(place) else place
                val series = rainRepo.seriesFor(target)
                // Mémorisé avant l'écriture pour que le flux de réglages ne
                // redéclenche pas un second chargement identique.
                loadedKey = keyOf(target)
                if (target != place) settingsRepo.saveGpsPlace(target)
                val now = Instant.now()
                _state.update {
                    it.copy(
                        series = series,
                        outlook = series.outlook(now),
                        updatedAt = now,
                        now = now,
                        loading = false,
                        locating = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update { it.copy(loading = false, locating = false, error = error.friendlyMessage()) }
            }
        }
    }

    /**
     * « Ma position » est résolue à chaque rafraîchissement : dernière position
     * connue si elle a moins de 10 min, sinon fix borné. Les appels réseau
     * (nom + zone UWZ) ne sont refaits que si le point a bougé de plus de 250 m.
     */
    private suspend fun currentPositionPlace(previous: Place): Place {
        val known = runCatching { location.lastKnown() }.getOrNull()
        val fix = when {
            known == null -> runCatching { location.current(timeoutMs = 7_000L) }.getOrNull()
            System.currentTimeMillis() - known.timeMillis < GPS_FRESH_MILLIS -> known
            else -> runCatching { location.current(timeoutMs = 5_000L) }.getOrNull() ?: known
        }

        if (fix == null) {
            if (previous.hasCoordinates) return previous
            throw IOException("Position indisponible : activez la localisation du téléphone")
        }

        val moved = !previous.hasCoordinates ||
            distanceMeters(Fix(previous.lat, previous.lon, 0L), fix) > GPS_MOVE_METERS
        if (!moved && previous.areaId != null) return previous
        return rainRepo.resolveGps(fix.lat, fix.lon)
    }

    private fun keyOf(place: Place?): String? =
        place?.let { "${it.id}|${it.lat}|${it.lon}|${it.areaId}" }

    private fun tick() {
        val current = _state.value
        val now = Instant.now()
        val series = current.series
        _state.update { it.copy(now = now, outlook = series?.outlook(now) ?: it.outlook) }

        if (series == null) return
        val age = Duration.between(series.createdAt, now).toMinutes()
        val sinceAttempt = current.lastAttemptAt?.let { Duration.between(it, now).toMinutes() } ?: 999
        if (age >= 5 && sinceAttempt >= 2 && !current.loading) refresh()
    }

    /** Sélectionne « Ma position » et force sa résolution (bouton, état vide). */
    fun addGpsPlace() {
        viewModelScope.launch {
            if (_state.value.settings.selectedId == GPS_ID) {
                refresh()
            } else {
                settingsRepo.selectPlace(GPS_ID)
            }
        }
    }

    fun selectPlace(id: String) {
        viewModelScope.launch { settingsRepo.selectPlace(id) }
    }

    fun addPlace(place: Place) {
        viewModelScope.launch {
            settingsRepo.upsertFavourite(place)
            _results.value = emptyList()
        }
    }

    fun removePlace(id: String) {
        viewModelScope.launch { settingsRepo.removeFavourite(id) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350L)
            runCatching { api.search(query) }.onSuccess { _results.value = it }
        }
    }

    fun clearSearch() {
        _results.value = emptyList()
    }

    fun saveAlert(rule: AlertRule) {
        viewModelScope.launch { settingsRepo.upsertAlert(rule) }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch { settingsRepo.removeAlert(id) }
    }

    fun setAlertEnabled(rule: AlertRule, enabled: Boolean) {
        viewModelScope.launch { settingsRepo.upsertAlert(rule.copy(enabled = enabled)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun reportError(message: String) = _state.update { it.copy(error = message, loading = false) }

    private fun Throwable.friendlyMessage(): String = when {
        message?.contains("HTTP 4") == true -> "Service indisponible (${message})"
        message?.contains("HTTP 5") == true -> "Service météo en erreur, réessayez plus tard"
        message?.contains("Unable to resolve host") == true -> "Pas de réseau"
        message.isNullOrBlank() -> "Erreur inconnue"
        else -> message!!
    }

    private companion object {
        /** En deçà, la dernière position connue est considérée comme courante. */
        const val GPS_FRESH_MILLIS = 10 * 60_000L

        /** Déplacement en deçà duquel on ne relance pas le géocodage / la zone. */
        const val GPS_MOVE_METERS = 250.0
    }
}
