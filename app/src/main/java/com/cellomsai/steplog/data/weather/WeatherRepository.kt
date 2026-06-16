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
    /**
     * 当日の天気（気圧・降水量・天気コード）を取得する。
     * API キー未設定・位置情報なし・通信失敗のいずれかなら null。
     */
    suspend fun fetchWeather(): WeatherSnapshot? {
        val apiKey = userPreferences.weatherApiKey.first()
        if (apiKey.isEmpty()) return null

        val location = getLocation() ?: return null

        return runCatching {
            val response = api.getCurrentWeather(location.latitude, location.longitude, apiKey)
            // rain フィールドは降水時のみ存在。無い場合は 0.0（= 降水なし）として扱う。
            val precipitation = response.rain?.oneHour
                ?: response.rain?.threeHour
                ?: 0f
            WeatherSnapshot(
                pressure = response.main.pressure,
                precipitationMm = precipitation,
                weatherCode = response.weather.firstOrNull()?.id,
            )
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
