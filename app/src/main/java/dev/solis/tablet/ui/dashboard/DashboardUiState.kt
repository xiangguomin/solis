package dev.solis.tablet.ui.dashboard

import android.content.Intent
import dev.solis.tablet.data.calendar.CalendarEvent
import dev.solis.tablet.data.gmail.GmailDigestItem
import dev.solis.tablet.data.local.SolisConfig
import dev.solis.tablet.data.photos.HighlightPhoto
import dev.solis.tablet.data.weather.WeatherForecast

enum class DashboardLayoutMode {
    FULL_BLEED_OVERLAY, // DAKboard classic: Full-screen photo with translucent glass widgets
    SPLIT_BENTO         // Split-screen: 55% Photo frame on left, 45% Dark Bento dashboard on right
}

data class DashboardUiState(
    val layoutMode: DashboardLayoutMode = DashboardLayoutMode.FULL_BLEED_OVERLAY,
    val isNightDimmed: Boolean = false,
    val isSyncing: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val batteryPercent: Int = 100,
    val pixelShiftOffsetX: Int = 0,
    val pixelShiftOffsetY: Int = 0,
    val currentPhoto: HighlightPhoto = HighlightPhoto.sample(),
    val secondaryPairedPhoto: HighlightPhoto? = null,
    val currentPhotoIndex: Int = 1,
    val totalPhotosCount: Int = 1,
    val weather: WeatherForecast = WeatherForecast.sample(),
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val gmailDigest: List<GmailDigestItem> = emptyList(),
    val gmailConsentIntent: Intent? = null,
    val gmailStatusText: String = "Tap to Authorize Gmail",
    val config: SolisConfig = SolisConfig()
)
