package dev.solis.tablet.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.solis.tablet.data.photos.HighlightPhoto
import dev.solis.tablet.ui.dashboard.DashboardLayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.solisDataStore by preferencesDataStore(name = "solis_preferences")

data class SolisConfig(
    val layoutMode: DashboardLayoutMode = DashboardLayoutMode.FULL_BLEED_OVERLAY,
    val sharedAlbumUrl: String = HighlightPhoto.DEFAULT_SHARED_ALBUM_URL,
    val photoIntervalSeconds: Int = 60,
    val locationName: String = "San Jose, CA",
    val latitude: Double = 37.3382,
    val longitude: Double = -121.8863,
    val geminiApiKey: String = "",
    val gmailOAuthToken: String = "",
    val nightDimStartHour: Int = 23, // 11 PM
    val nightDimEndHour: Int = 6     // 6 AM
)

class SolisPreferencesRepository(private val context: Context) {

    private object Keys {
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SHARED_ALBUM_URL = stringPreferencesKey("shared_album_url")
        val PHOTO_INTERVAL_SEC = intPreferencesKey("photo_interval_sec")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GMAIL_OAUTH_TOKEN = stringPreferencesKey("gmail_oauth_token")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
    }

    val configFlow: Flow<SolisConfig> = context.solisDataStore.data.map { prefs ->
        val modeStr = prefs[Keys.LAYOUT_MODE] ?: DashboardLayoutMode.FULL_BLEED_OVERLAY.name
        val layoutMode = runCatching { DashboardLayoutMode.valueOf(modeStr) }
            .getOrDefault(DashboardLayoutMode.FULL_BLEED_OVERLAY)

        SolisConfig(
            layoutMode = layoutMode,
            sharedAlbumUrl = prefs[Keys.SHARED_ALBUM_URL] ?: HighlightPhoto.DEFAULT_SHARED_ALBUM_URL,
            photoIntervalSeconds = prefs[Keys.PHOTO_INTERVAL_SEC] ?: 60,
            locationName = prefs[Keys.LOCATION_NAME] ?: "San Jose, CA",
            latitude = prefs[Keys.LATITUDE] ?: 37.3382,
            longitude = prefs[Keys.LONGITUDE] ?: -121.8863,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            gmailOAuthToken = prefs[Keys.GMAIL_OAUTH_TOKEN] ?: "",
            nightDimStartHour = prefs[Keys.NIGHT_START_HOUR] ?: 23,
            nightDimEndHour = prefs[Keys.NIGHT_END_HOUR] ?: 6
        )
    }

    suspend fun setLayoutMode(mode: DashboardLayoutMode) {
        context.solisDataStore.edit { it[Keys.LAYOUT_MODE] = mode.name }
    }

    suspend fun updateSettings(
        sharedAlbumUrl: String,
        photoIntervalSeconds: Int,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geminiApiKey: String,
        gmailOAuthToken: String
    ) {
        context.solisDataStore.edit { prefs ->
            prefs[Keys.SHARED_ALBUM_URL] = sharedAlbumUrl.trim()
            prefs[Keys.PHOTO_INTERVAL_SEC] = photoIntervalSeconds.coerceIn(10, 3600)
            prefs[Keys.LOCATION_NAME] = locationName.trim()
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.GEMINI_API_KEY] = geminiApiKey.trim()
            prefs[Keys.GMAIL_OAUTH_TOKEN] = gmailOAuthToken.trim()
        }
    }
}
