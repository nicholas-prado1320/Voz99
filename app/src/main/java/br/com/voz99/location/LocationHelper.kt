package br.com.voz99.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentAddress(): String? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val location = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                try {
                    lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } ?: return null
        return reverseGeocode(location)
            ?: "Localização atual (${String.format(Locale.US, "%.5f", location.latitude)}, ${String.format(Locale.US, "%.5f", location.longitude)})"
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val a = Geocoder(context, Locale("pt", "BR"))
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull() ?: return@withContext null
            listOfNotNull(a.thoroughfare, a.subThoroughfare, a.subLocality, a.locality, a.adminArea)
                .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
        } catch (_: Exception) { null }
    }
}
