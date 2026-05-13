package com.cellomsai.steplog.data.weather

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
    ): WeatherResponse
}

data class WeatherResponse(val main: Main) {
    data class Main(val pressure: Float)
}
