package net.chevillot.raintoday.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Un lieu suivi : soit la position GPS (isGps = true), soit un lieu recherché. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val lat: Double,
    val lon: Double,
    /** Zone UWZ renvoyée par le service (ex. UWZFR5196), mise en cache. */
    val areaId: String? = null,
    val isGps: Boolean = false,
) {
    val label: String
        get() = when {
            region.isBlank() && country.isBlank() -> ""
            region.isBlank() || region.equals(country, true) -> country
            else -> "$region, $country"
        }
}

/**
 * Plage horaire d'alerte.
 * daysMask : bit 0 = lundi … bit 6 = dimanche.
 * startMinute/endMinute : minutes depuis minuit, heure locale. end < start = passe minuit.
 */
@Serializable
data class AlertRule(
    val id: String,
    val placeId: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val leadMinutes: Int = 15,
    val enabled: Boolean = true,
    val lastNotifiedAt: Long = 0L,
)

@Serializable
data class AppSettings(
    val favourites: List<Place> = emptyList(),
    val alerts: List<AlertRule> = emptyList(),
    val selectedId: String? = null,
    val gps: Place? = null,
)

/** Réponse brute de RainService.php. Les nombres arrivent parfois en chaînes. */
@Serializable
internal data class RawRainChart(
    val precAngle: Int = 0,
    val domainMax: Int = 100,
    val domainMin: Int = 0,
    val avg: List<Int> = emptyList(),
    val min: List<Int> = emptyList(),
    val max: List<Int> = emptyList(),
    val startMin: Int = -1,
    val endMin: Int = -1,
    val intensity: Double = -1.0,
    val uwzid: String = "",
    val winddir: String = "",
    val startdtg: String = "",
    val creationdtg: String = "",
    val interval: String = "60000",
)

/**
 * Série de prévision de pluie : un échantillon par minute (interval 60000 ms),
 * couvrant environ 15 min de passé et 45 min de futur.
 */
data class RainSeries(
    val areaId: String,
    val start: Instant,
    val intervalSeconds: Long,
    val domainMin: Int,
    val domainMax: Int,
    val avg: List<Int>,
    val min: List<Int>,
    val max: List<Int>,
    val createdAt: Instant,
) {
    val size: Int get() = avg.size
    val lastTime: Instant get() = timeAt((size - 1).coerceAtLeast(0))

    fun timeAt(index: Int): Instant = start.plusSeconds(index * intervalSeconds)

    fun indexAt(instant: Instant): Int {
        if (size == 0) return 0
        val seconds = instant.epochSecond - start.epochSecond
        return (seconds / intervalSeconds).toInt().coerceIn(0, size - 1)
    }

    /** Valeur brute -> 0..1 (le seuil de pluie est 0,25 côté application d'origine). */
    fun fraction(value: Int): Float =
        if (domainMax == 0) 0f else (value - domainMin).toFloat() / domainMax.toFloat()

    fun avgAt(index: Int): Int = avg.getOrElse(index) { domainMin }
    fun minAt(index: Int): Int = min.getOrElse(index) { domainMin }
    fun maxAt(index: Int): Int = max.getOrElse(index) { domainMin }
}

private val API_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** "2026-11-25 11:10:00" en UTC -> Instant. */
fun parseApiDate(value: String): Instant? = runCatching {
    LocalDateTime.parse(value.trim(), API_DATE).toInstant(ZoneOffset.UTC)
}.getOrNull()
