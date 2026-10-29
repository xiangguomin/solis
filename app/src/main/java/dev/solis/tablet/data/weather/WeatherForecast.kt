package dev.solis.tablet.data.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherForecast(
    val locationName: String,
    val currentTempF: Int,
    val conditionLabel: String,
    val highF: Int,
    val lowF: Int,
    val humidityPercent: Int,
    val daySegments: List<DaySegmentForecast> = defaultSegments(),
    val upcomingDays: List<DailyForecast> = emptyList()
) {
    @Serializable
    data class DaySegmentForecast(
        val label: String, // "now", "morn", "aft", "eve", "night"
        val tempF: Int,
        val weatherCode: Int
    )

    @Serializable
    data class DailyForecast(
        val dayName: String,
        val conditionLabel: String,
        val highF: Int,
        val lowF: Int,
        val precipProbability: Int
    ) {
        val icon: String get() = conditionToIcon(conditionLabel)
    }

    val currentIcon: String get() = conditionToIcon(conditionLabel)

    companion object {
        fun conditionToIcon(label: String): String {
            val lower = label.lowercase()
            return when {
                "thunder" in lower || "storm" in lower -> "⛈️"
                "snow" in lower || "blizzard" in lower || "ice" in lower -> "❄️"
                "rain" in lower || "shower" in lower || "drizzle" in lower -> "🌧️"
                "fog" in lower || "mist" in lower || "haze" in lower -> "🌫️"
                "partly" in lower || "mostly clear" in lower -> "⛅"
                "cloud" in lower || "overcast" in lower -> "☁️"
                else -> "☀️"
            }
        }
        fun defaultSegments() = listOf(
            DaySegmentForecast("now", 68, 1),
            DaySegmentForecast("morn", 58, 1),
            DaySegmentForecast("aft", 72, 0),
            DaySegmentForecast("eve", 64, 2),
            DaySegmentForecast("night", 55, 0)
        )

        fun sample() = WeatherForecast(
            locationName = "San Jose, CA",
            currentTempF = 68,
            conditionLabel = "Mostly Clear",
            highF = 74,
            lowF = 54,
            humidityPercent = 45,
            daySegments = defaultSegments(),
            upcomingDays = listOf(
                DailyForecast("Sat", "Sunny", 74, 53, 0),
                DailyForecast("Sun", "Partly Cloudy", 69, 51, 15),
                DailyForecast("Mon", "Sunny", 73, 52, 5),
                DailyForecast("Tue", "Clear", 76, 55, 0)
            )
        )
    }
}
