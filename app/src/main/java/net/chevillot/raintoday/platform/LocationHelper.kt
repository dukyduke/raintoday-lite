package net.chevillot.raintoday.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Position GPS/NETWORK sans dépendance à Google Play Services. */
class LocationHelper(private val context: Context) {

    private val manager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        val lm = manager ?: return null
        val providers = enabledProviders(lm)
        if (providers.isEmpty()) return null

        val last = providers
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) return last

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val fresh = withTimeoutOrNull(12_000L) { awaitCurrent(lm, providers.first()) }
            if (fresh != null) return fresh
        }
        return last
    }

    private fun enabledProviders(lm: LocationManager): List<String> = buildList {
        if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrent(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            runCatching {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { signal.cancel() }
        }
}
