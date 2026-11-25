package net.chevillot.raintoday.data

import java.io.IOException

/** Récupération d'une série de pluie pour un lieu, avec mise en cache de sa zone UWZ. */
class RainRepository(
    private val api: MeteogroupApi,
    private val settings: SettingsRepository,
) {
    suspend fun seriesFor(place: Place): RainSeries {
        val areaId = place.areaId ?: api.lookupArea(place.lat, place.lon)
            ?.also { settings.updateAreaId(place.id, it) }
            ?: throw IOException("Zone de pluie introuvable pour ${place.name}")
        return api.rainChart(areaId)
    }

    /** Position GPS -> lieu nommé (géocodage inverse) + zone UWZ. */
    suspend fun resolveGps(lat: Double, lon: Double): Place {
        val near = runCatching { api.nearest(lat, lon) }.getOrNull()
        val areaId = api.lookupArea(lat, lon)
        return Place(
            id = SettingsRepository.GPS_ID,
            name = near?.name?.takeIf { it.isNotBlank() } ?: "Ma position",
            region = near?.region.orEmpty(),
            country = near?.country.orEmpty(),
            lat = lat,
            lon = lon,
            areaId = areaId,
            isGps = true,
        )
    }
}
