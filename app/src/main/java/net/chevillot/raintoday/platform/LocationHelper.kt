package net.chevillot.raintoday.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Fix(
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
    val accuracyMeters: Float = 0f,
)

/** Distance en mètres entre deux positions (formule de haversine). */
fun distanceMeters(a: Fix, b: Fix): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}

/**
 * Position sans dépendance à Google Play Services.
 *
 * Ordre de priorité : cache mémoire → dernière position connue du système →
 * course entre fournisseurs (réseau d'abord, il répond en une seconde ; GPS en
 * parallèle s'il finit plus vite). On ne renvoie jamais « rien » quand une
 * position ancienne est disponible : une zone de pluie à quelques kilomètres
 * près reste correcte, alors qu'un échec bloque l'utilisateur.
 */
class LocationHelper(private val context: Context) {

    @Volatile
    private var cached: Fix? = null

    private fun manager(): LocationManager? = context.getSystemService(LocationManager::class.java)

    /** Dernière position connue, même ancienne ; alimente aussi le cache en mémoire. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Fix? {
        val lm = manager() ?: return cached
        val best = providers(lm)
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (best != null) {
            val fix = Fix(best.latitude, best.longitude, best.time, best.accuracy)
            val previous = cached
            if (previous == null || fix.timeMillis >= previous.timeMillis) cached = fix
        }
        return cached
    }

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 7_000L, maxAgeMs: Long = 60_000L): Fix? {
        val cachedFix = lastKnown()
        if (cachedFix != null && System.currentTimeMillis() - cachedFix.timeMillis <= maxAgeMs) {
            return cachedFix
        }
        val lm = manager() ?: return cachedFix

        val fresh = withTimeoutOrNull(timeoutMs) { race(lm) }
        if (fresh != null) {
            cached = fresh
            return fresh
        }
        return cachedFix
    }

    /** Réseau et passif d'abord (rapides), GPS ensuite : la zone UWZ n'exige pas mieux. */
    private fun providers(lm: LocationManager): List<String> = buildList {
        if (enabled(lm, LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        if (enabled(lm, LocationManager.PASSIVE_PROVIDER)) add(LocationManager.PASSIVE_PROVIDER)
        if (enabled(lm, LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
    }

    private fun enabled(lm: LocationManager, provider: String): Boolean =
        runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)

    /** Premier fournisseur qui répond gagne ; les autres sont annulés. */
    private suspend fun race(lm: LocationManager): Fix? = coroutineScope {
        val available = providers(lm)
        if (available.isEmpty()) return@coroutineScope null

        val result = CompletableDeferred<Fix?>()
        val jobs = available.map { provider ->
            launch {
                requestOnce(lm, provider)?.let { fix -> result.complete(fix) }
            }
        }
        launch {
            jobs.joinAll()
            result.complete(null)
        }
        val fix = result.await()
        jobs.forEach { it.cancel() }
        fix
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(lm: LocationManager, provider: String): Fix? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestOnceModern(lm, provider)
        } else {
            requestOnceLegacy(lm, provider)
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnceModern(lm: LocationManager, provider: String): Fix? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            runCatching {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location?.toFix())
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { signal.cancel() }
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestOnceLegacy(lm: LocationManager, provider: String): Fix? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location.toFix())
                }

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            runCatching { lm.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
                .onFailure { if (continuation.isActive) continuation.resume(null) }
            continuation.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }

    private fun Location.toFix(): Fix = Fix(latitude, longitude, time, accuracy)
}
