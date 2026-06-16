package com.cellomsai.steplog.data.weather

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Forecast API。APIキー不要・無料で、過去日（最大 92 日）の
 * 日次降水量 `precipitation_sum`（mm）を取得できる。降水量の履歴補完に使用する。
 *
 * エンドポイント: https://api.open-meteo.com/v1/forecast
 */
interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getDailyPrecipitation(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "precipitation_sum",
        @Query("past_days") pastDays: Int = 92,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponse
}

data class OpenMeteoResponse(
    val daily: Daily? = null,
) {
    data class Daily(
        val time: List<String> = emptyList(),
        @SerializedName("precipitation_sum") val precipitationSum: List<Float?> = emptyList(),
    )
}
