package dev.solis.tablet.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Fetches live current weather + hourly day segments (now, morn, aft, eve, night) + 5-day forecast
 * from Open-Meteo API.
 */
class OpenMeteoWeatherRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
        locationName: String
    ): Result<WeatherForecast> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,weather_code" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&temperature_unit=fahrenheit&timezone=auto"

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body?.string() ?: error("Empty response")
                parseOpenMeteoJson(JSONObject(body), locationName)
            }
        }
    }

    private fun parseOpenMeteoJson(root: JSONObject, locationName: String): WeatherForecast {
        val current = root.getJSONObject("current")
        val currentTemp = current.getDouble("temperature_2m").toInt()
        val currentCode = current.getInt("weather_code")

        val hourly = root.optJSONObject("hourly")
        val hourlyTemps = hourly?.optJSONArray("temperature_2m")
        val hourlyCodes = hourly?.optJSONArray("weather_code")

        // Extract morn (8am = index 8), aft (2pm = index 14), eve (6pm = index 18), night (10pm = index 22)
        val segments = if (hourlyTemps != null && hourlyCodes != null && hourlyTemps.length() >= 24) {
            listOf(
                WeatherForecast.DaySegmentForecast("now", currentTemp, currentCode),
                WeatherForecast.DaySegmentForecast("morn", hourlyTemps.getDouble(8).toInt(), hourlyCodes.getInt(8)),
                WeatherForecast.DaySegmentForecast("aft", hourlyTemps.getDouble(14).toInt(), hourlyCodes.getInt(14)),
                WeatherForecast.DaySegmentForecast("eve", hourlyTemps.getDouble(18).toInt(), hourlyCodes.getInt(18)),
                WeatherForecast.DaySegmentForecast("night", hourlyTemps.getDouble(22).toInt(), hourlyCodes.getInt(22))
            )
        } else {
            WeatherForecast.defaultSegments()
        }

        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxTemps = daily.getJSONArray("temperature_2m_max")
        val minTemps = daily.getJSONArray("temperature_2m_min")
        val precipProbs = daily.optJSONArray("precipitation_probability_max")

        val upcoming = mutableListOf<WeatherForecast.DailyForecast>()
        val count = minOf(times.length(), 6)
        for (i in 1 until count) {
            val dateStr = times.getString(i)
            val dayName = runCatching {
                LocalDate.parse(dateStr).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
            }.getOrDefault(dateStr.takeLast(5))

            upcoming.add(
                WeatherForecast.DailyForecast(
                    dayName = dayName,
                    conditionLabel = wmoCodeToLabel(codes.getInt(i)),
                    highF = maxTemps.getDouble(i).toInt(),
                    lowF = minTemps.getDouble(i).toInt(),
                    precipProbability = precipProbs?.optInt(i, 0) ?: 0
                )
            )
        }

        return WeatherForecast(
            locationName = locationName,
            currentTempF = currentTemp,
            conditionLabel = wmoCodeToLabel(currentCode),
            highF = maxTemps.getDouble(0).toInt(),
            lowF = minTemps.getDouble(0).toInt(),
            humidityPercent = current.getInt("relative_humidity_2m"),
            daySegments = segments,
            upcomingDays = upcoming.ifEmpty { WeatherForecast.sample().upcomingDays }
        )
    }

    private fun wmoCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}
