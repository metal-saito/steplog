package com.cellomsai.steplog.data.weather

import com.google.gson.annotations.SerializedName
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

/**
 * OpenWeatherMap Current Weather API のレスポンス（必要部分のみ）。
 *
 * - [main].pressure … 気圧（hPa）
 * - [weather] … 天気状態の配列。先頭要素の id（コンディションコード）で雨/晴れを判定する
 * - [rain] … 降水量。雨が降っていないときはフィールド自体が省略される（= null）
 */
data class WeatherResponse(
    val main: Main,
    val weather: List<Weather> = emptyList(),
    val rain: Rain? = null,
) {
    data class Main(val pressure: Float)

    data class Weather(
        val id: Int,
        val main: String? = null,
    )

    data class Rain(
        @SerializedName("1h") val oneHour: Float? = null,
        @SerializedName("3h") val threeHour: Float? = null,
    )
}

/**
 * 当日の天気スナップショット。気圧に加えて降水量・天気コードを保持する。
 *
 * @param pressure 気圧（hPa）
 * @param precipitationMm 直近の降水量（mm）。API が雨を返さなかった場合は 0.0（= 晴れ/曇りで降水なし）
 * @param weatherCode OpenWeatherMap の天気コンディションコード（2xx 雷雨 / 3xx 霧雨 / 5xx 雨 / 6xx 雪 / 800 快晴 / 80x 雲）
 */
data class WeatherSnapshot(
    val pressure: Float,
    val precipitationMm: Float,
    val weatherCode: Int?,
)
