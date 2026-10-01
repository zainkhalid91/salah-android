package com.zainkhalid.salah.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import salah.core.LocationSearch
import salah.core.SavedLocation
import java.util.TimeZone
import kotlin.coroutines.resume

/** One-off coarse location via the platform LocationManager (no Play Services needed). */
object DeviceLocation {

    /** Finds the device and names it. Falls back to the phone's time zone when offline. */
    suspend fun current(context: Context): SavedLocation? {
        val fix = withTimeoutOrNull(20_000) { fix(context) } ?: lastKnown(context) ?: return null
        val named = withContext(Dispatchers.IO) {
            runCatching { LocationSearch.reverse(fix.latitude, fix.longitude, SavedLocation.Source.AUTOMATIC) }.getOrNull()
        }
        return named ?: SavedLocation(
            name = "Current location",
            latitude = fix.latitude,
            longitude = fix.longitude,
            timeZone = TimeZone.getDefault().id,
            source = SavedLocation.Source.AUTOMATIC,
        )
    }

    private fun provider(lm: LocationManager): String? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fix(context: Context): Location? = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = provider(lm)
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
        } else {
            // All four callbacks spelled out: older Android has no default methods here.
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }
}
