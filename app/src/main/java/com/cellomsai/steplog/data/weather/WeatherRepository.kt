package com.cellomsai.steplog.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.cellomsai.steplog.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApiService,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
) {
    suspend fun fetchPressure(): Float? {
        val apiKey = userPreferences.weatherApiKey.first()
        if (apiKey.isEmpty()) return null

        val location = getLocation() ?: return null

        return runCatching {
            api.getCurrentWeather(location.latitude, location.longitude, apiKey).main.pressure
        }.getOrNull()
    }

    private suspend fun getLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val lm = context.getSystemService(LocationManager::class.java) ?: return null

        // キャッシュがあればすぐ返す
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { return it }
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { return it }
        lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let { return it }

        // キャッシュなし → 5 秒以内に取得できれば使う
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location) {}
                    }
                    override fun onProviderDisabled(provider: String) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(null) {}
                    }
                }
                runCatching {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure {
                    if (cont.isActive) cont.resume(null) {}
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }
}
