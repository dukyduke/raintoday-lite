package net.chevillot.raintoday.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Lieux suivis, sélection courante et règles d'alerte (un seul blob JSON atomique). */
class SettingsRepository(private val context: Context) {

    private val key = stringPreferencesKey("app_settings")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[key]?.let(::decode) ?: AppSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun decode(value: String): AppSettings =
        runCatching { json.decodeFromString<AppSettings>(value) }.getOrDefault(AppSettings())

    private suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let(::decode) ?: AppSettings()
            prefs[key] = json.encodeToString(transform(current))
        }
    }

    suspend fun selectPlace(id: String?) = update { it.copy(selectedId = id) }

    suspend fun upsertFavourite(place: Place) = update { settings ->
        settings.copy(
            favourites = settings.favourites.filterNot { it.id == place.id } + place,
            selectedId = place.id,
        )
    }

    suspend fun removeFavourite(id: String) = update { settings ->
        settings.copy(
            favourites = settings.favourites.filterNot { it.id == id },
            selectedId = if (settings.selectedId == id) settings.gps?.id else settings.selectedId,
        )
    }

    suspend fun saveGpsPlace(place: Place) = update { it.copy(gps = place) }

    /** Mémorise la zone UWZ résolue pour éviter un appel à chaque rafraîchissement. */
    suspend fun updateAreaId(placeId: String, areaId: String) = update { settings ->
        settings.copy(
            favourites = settings.favourites.map { if (it.id == placeId) it.copy(areaId = areaId) else it },
            gps = settings.gps?.let { if (it.id == placeId) it.copy(areaId = areaId) else it },
        )
    }

    suspend fun upsertAlert(rule: AlertRule) = update { settings ->
        settings.copy(alerts = settings.alerts.filterNot { it.id == rule.id } + rule)
    }

    suspend fun removeAlert(id: String) = update { settings ->
        settings.copy(alerts = settings.alerts.filterNot { it.id == id })
    }

    suspend fun markNotified(id: String, atEpochMillis: Long) = update { settings ->
        settings.copy(alerts = settings.alerts.map { if (it.id == id) it.copy(lastNotifiedAt = atEpochMillis) else it })
    }

    fun resolvePlace(settings: AppSettings, placeId: String?): Place? = when {
        placeId == null -> null
        placeId == GPS_ID -> settings.gps
        else -> settings.favourites.firstOrNull { it.id == placeId }
    }

    companion object {
        const val GPS_ID = "__gps__"
    }
}
