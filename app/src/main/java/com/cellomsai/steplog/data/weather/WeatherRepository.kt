package com.cellomsai.steplog.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.cellomsai.steplog.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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

        val location = getLastLocation() ?: return null

        return runCatching {
            api.getCurrentWeather(location.latitude, location.longitude, apiKey).main.pressure
        }.getOrNull()
    }

    private fun getLastLocation(): android.location.Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    }
}
