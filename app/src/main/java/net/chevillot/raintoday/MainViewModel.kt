package net.chevillot.raintoday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.chevillot.raintoday.data.AlertRule
import net.chevillot.raintoday.data.AppSettings
import net.chevillot.raintoday.data.MeteogroupApi
import net.chevillot.raintoday.data.Place
import net.chevillot.raintoday.data.RainRepository
import net.chevillot.raintoday.data.RainSeries
import net.chevillot.raintoday.data.SettingsRepository
import net.chevillot.raintoday.domain.Outlook
import net.chevillot.raintoday.domain.outlook
import net.chevillot.raintoday.platform.LocationHelper
import net.chevillot.raintoday.work.AlertScheduler
import java.time.Duration
import java.time.Instant

data class UiState(
    val settings: AppSettings = AppSettings(),
    val place: Place? = null,
    val series: RainSeries? = null,
    val outlook: Outlook? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val now: Instant = Instant.now(),
    val updatedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

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
                val place = settingsRepo.resolvePlace(settings, settings.selectedId)
                    ?: settings.gps
                    ?: settings.favourites.firstOrNull()
                _state.update { it.copy(settings = settings, place = place) }
                val key = place?.let { "${it.id}|${it.lat}|${it.lon}|${it.areaId}" }
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
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, lastAttemptAt = Instant.now()) }
            runCatching { rainRepo.seriesFor(place) }
                .onSuccess { series ->
                    val now = Instant.now()
                    _state.update {
                        it.copy(
                            series = series,
                            outlook = series.outlook(now),
                            updatedAt = now,
                            now = now,
                            loading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
        }
    }

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

    fun addGpsPlace() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val fix = withTimeoutOrNull(25_000L) { location.current() }
            if (fix == null) {
                _state.update {
                    it.copy(loading = false, error = "Position indisponible : activez la localisation du téléphone")
                }
                return@launch
            }
            runCatching { rainRepo.resolveGps(fix.latitude, fix.longitude) }
                .onSuccess { place ->
                    settingsRepo.saveGpsPlace(place)
                    settingsRepo.selectPlace(SettingsRepository.GPS_ID)
                    _state.update { it.copy(loading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
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
}
