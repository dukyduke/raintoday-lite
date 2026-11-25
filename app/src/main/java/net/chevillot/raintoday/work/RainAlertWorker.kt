package net.chevillot.raintoday.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.chevillot.raintoday.data.MeteogroupApi
import net.chevillot.raintoday.data.Place
import net.chevillot.raintoday.data.RainRepository
import net.chevillot.raintoday.data.SettingsRepository
import net.chevillot.raintoday.domain.covers
import net.chevillot.raintoday.domain.rainBetween
import net.chevillot.raintoday.domain.windowOn
import net.chevillot.raintoday.platform.Fix
import net.chevillot.raintoday.platform.LocationHelper
import net.chevillot.raintoday.platform.Notifications
import net.chevillot.raintoday.platform.distanceMeters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Contrôle périodique (15 min minimum, contrainte WorkManager) des plages d'alerte
 * actives : notifie si de la pluie est attendue pendant la plage.
 */
class RainAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        val api = MeteogroupApi()
        val rain = RainRepository(api, settings)

        val state = settings.current()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()

        for (rule in state.alerts.filter { it.enabled && it.covers(now, zone) }) {
            val stored = settings.resolvePlace(state, rule.placeId) ?: continue
            // Une règle sur « Ma position » doit utiliser la position du moment,
            // pas celle mémorisée quand la règle a été créée.
            val place = if (stored.isGps) currentGpsPlace(rain, settings, stored) else stored
            val series = runCatching { rain.seriesFor(place) }.getOrNull() ?: continue

            val day = now.atZone(zone).toLocalDate()
            val (from, to) = rule.windowOn(day, zone)
            val checkFrom = maxOf(from, now.minusSeconds(300))
            val hit = series.rainBetween(checkFrom, to) ?: continue

            val sinceLastNotification = now.toEpochMilli() - rule.lastNotifiedAt
            if (sinceLastNotification < DEDUPE_MILLIS) continue

            val minutes = Duration.between(now, hit).toMinutes().coerceAtLeast(0)
            val time = TIME_FORMAT.withZone(zone).format(maxOf(hit, now))
            val title = if (minutes <= 0) "Il pleut sur ${place.name}" else "Pluie dans $minutes min à ${place.name}"
            val text = if (minutes <= 0) {
                "De la pluie est prévue pendant votre plage ${rule.timeLabel()}."
            } else {
                "Pluie prévue vers $time (plage ${rule.timeLabel()})."
            }
            Notifications.notify(applicationContext, rule.id.hashCode(), title, text)
            settings.markNotified(rule.id, now.toEpochMilli())
        }
        return Result.success()
    }

    private fun net.chevillot.raintoday.data.AlertRule.timeLabel(): String =
        "%02d:%02d–%02d:%02d".format(startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60)

    /** Position courante pour une alerte « Ma position » (bornée, silencieuse en cas d'échec). */
    private suspend fun currentGpsPlace(
        rain: RainRepository,
        settings: SettingsRepository,
        previous: Place,
    ): Place {
        val helper = LocationHelper(applicationContext)
        val fix = runCatching { helper.current(timeoutMs = 7_000L) }.getOrNull() ?: return previous
        val moved = !previous.hasCoordinates ||
            distanceMeters(Fix(previous.lat, previous.lon, 0L), fix) > 250.0
        if (!moved && previous.areaId != null) return previous
        val resolved = runCatching { rain.resolveGps(fix.lat, fix.lon) }.getOrNull() ?: return previous
        settings.saveGpsPlace(resolved)
        return resolved
    }

    companion object {
        private const val DEDUPE_MILLIS = 90 * 60_000L
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
