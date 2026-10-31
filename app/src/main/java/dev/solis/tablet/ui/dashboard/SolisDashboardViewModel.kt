package dev.solis.tablet.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.solis.tablet.data.calendar.CalendarEvent
import dev.solis.tablet.data.calendar.DeviceCalendarRepository
import dev.solis.tablet.data.gmail.GmailDigestRepository
import dev.solis.tablet.data.gmail.GmailNotificationListener
import dev.solis.tablet.data.local.SolisConfig
import dev.solis.tablet.data.local.SolisPreferencesRepository
import dev.solis.tablet.data.photos.HighlightPhoto
import dev.solis.tablet.data.photos.SolisPhotoManager
import dev.solis.tablet.data.weather.OpenMeteoWeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

class SolisDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = SolisPreferencesRepository(application)
    private val weatherRepo = OpenMeteoWeatherRepository()
    private val calendarRepo = DeviceCalendarRepository(application)
    private val gmailRepo = GmailDigestRepository()
    private val photoManager = SolisPhotoManager(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var photoPool: List<HighlightPhoto> = emptyList()
    private var currentPhotoIndex: Int = 0
    private var currentConfig: SolisConfig = SolisConfig()
    private var slideshowJob: Job? = null

    /**
     * Real-time ContentObserver registered on CalendarContract.CONTENT_URI so any new/edited
     * Google Calendar event synced to the Lenovo tablet updates the dashboard instantly.
     */
    private val calendarContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            refreshCalendar()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refreshCalendar()
        }
    }
    private var isCalendarObserverRegistered = false

    /**
     * Real-time ContentObserver on MediaStore.Images.Media.EXTERNAL_CONTENT_URI so whenever
     * the user stars/favorites a photo on the tablet, Solis picks it up immediately.
     */
    private val photosContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            refreshPhotos()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refreshPhotos()
        }
    }
    private var isPhotosObserverRegistered = false

    init {
        observePreferences()
        observeLiveGmailNotifications()
        registerInstantCalendarObserver()
        registerInstantPhotosObserver()
        restartPhotoSlideshowLoop()
        startPeriodicPhotoPoolSyncLoop()
        startPeriodicGmailAndWeatherSync()
        startBurnInProtectionAndNightCheckLoop()
    }

    private fun registerInstantPhotosObserver() {
        val context = getApplication<Application>()
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm && !isPhotosObserverRegistered) {
            runCatching {
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    photosContentObserver
                )
                isPhotosObserverRegistered = true
            }
        }
    }

    private fun observeLiveGmailNotifications() {
        viewModelScope.launch {
            GmailNotificationListener.liveNotificationItems.collectLatest { liveItems ->
                if (liveItems.isNotEmpty() && _uiState.value.gmailDigest.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            gmailDigest = liveItems,
                            gmailStatusText = "Live Gmail Notifications • Every 15m"
                        )
                    }
                }
            }
        }
    }

    private fun registerInstantCalendarObserver() {
        val context = getApplication<Application>()
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm && !isCalendarObserverRegistered) {
            runCatching {
                context.contentResolver.registerContentObserver(
                    CalendarContract.CONTENT_URI,
                    true,
                    calendarContentObserver
                )
                isCalendarObserverRegistered = true
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isCalendarObserverRegistered) {
            runCatching {
                getApplication<Application>().contentResolver.unregisterContentObserver(
                    calendarContentObserver
                )
            }
            isCalendarObserverRegistered = false
        }
        if (isPhotosObserverRegistered) {
            runCatching {
                getApplication<Application>().contentResolver.unregisterContentObserver(
                    photosContentObserver
                )
            }
            isPhotosObserverRegistered = false
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefsRepo.configFlow.collectLatest { config ->
                val urlChanged = config.sharedAlbumUrl != currentConfig.sharedAlbumUrl
                val intervalChanged = config.photoIntervalSeconds != currentConfig.photoIntervalSeconds
                val locationChanged = config.latitude != currentConfig.latitude ||
                    config.longitude != currentConfig.longitude ||
                    config.locationName != currentConfig.locationName

                currentConfig = config
                _uiState.update {
                    it.copy(
                        layoutMode = config.layoutMode,
                        isLightMode = config.isLightMode,
                        config = config,
                        batteryPercent = readBatteryPercent()
                    )
                }

                if (urlChanged || photoPool.isEmpty()) {
                    refreshPhotos()
                }
                if (intervalChanged) {
                    restartPhotoSlideshowLoop()
                }
                if (locationChanged) {
                    refreshWeather()
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            refreshPhotos()
            refreshWeather()
            refreshCalendar()
            refreshGmailDigest()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    batteryPercent = readBatteryPercent()
                )
            }
        }
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPerm && !isCalendarObserverRegistered) {
                registerInstantCalendarObserver()
            }

            val events = if (hasPerm) {
                calendarRepo.queryUpcomingEvents(hoursAhead = 168) // Next 7 days scrollable
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    upcomingEvents = events,
                    hasCalendarPermission = hasPerm
                )
            }
        }
    }

    /**
     * Google Nest Display Photo Refresh & Delta-Sync Logic:
     * - Initial load: Shuffles the photo pool into a randomized deck so every session feels fresh.
     * - Background / ContentObserver refresh:
     *   1. Prunes any photos that were unstarred or removed from the album.
     *   2. Detects newly starred/added photos (`newPhotos`) and injects them immediately after
     *      `currentPhotoIndex` so newly starred photos appear on the very next 60s slide transition!
     *   3. Preserves the currently displayed photo so background syncs never cause a jarring jump.
     */
    fun refreshPhotos(forceReshuffle: Boolean = false) {
        viewModelScope.launch {
            registerInstantPhotosObserver()
            val allPhotos = photoManager.loadFavoritePhotos(
                sharedAlbumUrl = currentConfig.sharedAlbumUrl,
                onlySelected = false
            )
            _uiState.update { it.copy(allManageablePhotos = allPhotos) }

            val loaded = allPhotos.filter { it.isSelected }
            if (loaded.isEmpty()) return@launch

            if (photoPool.isEmpty() || forceReshuffle) {
                photoPool = if (loaded.size > 2) loaded.shuffled() else loaded
                currentPhotoIndex = 0
                updateCurrentPhotoState()
                return@launch
            }

            val currentUrl = photoPool.getOrNull(currentPhotoIndex)?.imageUrl
            val loadedByUrl = loaded.associateBy { it.imageUrl }
            val existingUrls = photoPool.map { it.imageUrl }.toSet()

            // Keep existing shuffled order for photos still in the pool (with updated metadata)
            val retained = photoPool.mapNotNull { loadedByUrl[it.imageUrl] }.toMutableList()
            // Newly starred or newly added album photos since last sync
            val newlyAdded = loaded.filter { it.imageUrl !in existingUrls }

            if (retained.isEmpty()) {
                photoPool = loaded.shuffled()
                currentPhotoIndex = 0
            } else {
                val anchorIdx = retained.indexOfFirst { it.imageUrl == currentUrl }
                    .takeIf { it >= 0 } ?: 0
                if (newlyAdded.isNotEmpty()) {
                    // Inject newly starred photos right after the current photo so they show next!
                    retained.addAll(anchorIdx + 1, newlyAdded)
                    photoPool = retained
                    // Immediately advance if we were on the fallback sample photo
                    if (currentUrl == HighlightPhoto.sample().imageUrl) {
                        currentPhotoIndex = (anchorIdx + 1).coerceAtMost(photoPool.lastIndex)
                    } else {
                        currentPhotoIndex = anchorIdx
                    }
                } else {
                    photoPool = retained
                    currentPhotoIndex = anchorIdx
                }
            }
            updateCurrentPhotoState()
        }
    }

    /**
     * Updates the current single photo (always 1 photo at a time, no collage).
     */
    private fun updateCurrentPhotoState() {
        if (photoPool.isEmpty()) return
        val primary = photoPool[currentPhotoIndex]
        _uiState.update {
            it.copy(
                currentPhoto = primary,
                secondaryPairedPhoto = null,
                totalPhotosCount = photoPool.size,
                currentPhotoIndex = currentPhotoIndex + 1
            )
        }
    }

    private suspend fun refreshWeather() {
        weatherRepo.fetchForecast(
            latitude = currentConfig.latitude,
            longitude = currentConfig.longitude,
            locationName = currentConfig.locationName
        ).onSuccess { forecast ->
            _uiState.update { it.copy(weather = forecast) }
        }
    }

    fun refreshGmailDigest() {
        viewModelScope.launch {
            var token = currentConfig.gmailOAuthToken
            if (token.isBlank()) {
                when (val res = gmailRepo.requestDeviceGoogleAuthToken(getApplication())) {
                    is GmailDigestRepository.TokenResult.Success -> {
                        token = res.token
                        _uiState.update {
                            it.copy(
                                gmailConsentIntent = null,
                                gmailStatusText = "${res.email} • Every 15m"
                            )
                        }
                    }
                    is GmailDigestRepository.TokenResult.NeedsConsent -> {
                        _uiState.update {
                            it.copy(
                                gmailConsentIntent = res.consentIntent,
                                gmailStatusText = "Tap to Authorize ${res.email}"
                            )
                        }
                    }
                    is GmailDigestRepository.TokenResult.Error -> {
                        _uiState.update {
                            it.copy(
                                gmailStatusText = "Live Gmail Sync"
                            )
                        }
                    }
                }
            }

            val items = gmailRepo.fetchDailyDigest(
                gmailOAuthToken = token,
                geminiApiKey = currentConfig.geminiApiKey
            )
            _uiState.update { it.copy(gmailDigest = items) }
        }
    }

    fun onPhotosPickedFromAccount(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val prevCount = _uiState.value.allManageablePhotos.size
            _uiState.update {
                it.copy(photoImportStatus = "⏳ Importing ${uris.size} photo(s) from Google Photos...")
            }
            val allUpdated = photoManager.addFavoritePhotosFromPicker(uris)
            val addedCount = (allUpdated.size - prevCount).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    allManageablePhotos = allUpdated,
                    photoImportStatus = "✓ Imported $addedCount new photo(s) from Google Photos!"
                )
            }
            val selected = allUpdated.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                photoPool = selected
                currentPhotoIndex = 0
                updateCurrentPhotoState()
                restartPhotoSlideshowLoop()
            }
        }
    }

    fun applyPhotoSelection(selectedIds: Set<String>) {
        viewModelScope.launch {
            photoManager.updateSelectedPhotos(selectedIds)
            refreshPhotos(forceReshuffle = true)
            restartPhotoSlideshowLoop()
        }
    }

    fun deleteUnselectedPhotos(keepIds: Set<String>) {
        viewModelScope.launch {
            photoManager.deleteUnselectedPhotos(keepIds)
            refreshPhotos(forceReshuffle = true)
            restartPhotoSlideshowLoop()
        }
    }

    private fun advanceToNextSlideInDeck() {
        if (photoPool.isEmpty()) return
        val nextIdx = currentPhotoIndex + 1
        if (nextIdx >= photoPool.size) {
            // Re-shuffle deck at the end of a full cycle (Nest Hub style), avoiding back-to-back repeat
            if (photoPool.size > 2) {
                val lastShownUrl = photoPool.last().imageUrl
                val reshuffled = photoPool.shuffled().toMutableList()
                if (reshuffled.first().imageUrl == lastShownUrl) {
                    val swap = reshuffled[0]
                    reshuffled[0] = reshuffled[reshuffled.lastIndex]
                    reshuffled[reshuffled.lastIndex] = swap
                }
                photoPool = reshuffled
            }
            currentPhotoIndex = 0
        } else {
            currentPhotoIndex = nextIdx
        }
        updateCurrentPhotoState()
    }

    /**
     * Swipe left -> next favorite photo; resets the auto-switch timer so it doesn't jump immediately.
     */
    fun nextPhoto() {
        if (photoPool.isEmpty()) return
        advanceToNextSlideInDeck()
        restartPhotoSlideshowLoop()
    }

    /**
     * Swipe right -> previous favorite photo; resets the auto-switch timer.
     */
    fun previousPhoto() {
        if (photoPool.isEmpty()) return
        currentPhotoIndex = if (currentPhotoIndex - 1 < 0) {
            photoPool.lastIndex
        } else {
            currentPhotoIndex - 1
        }
        updateCurrentPhotoState()
        restartPhotoSlideshowLoop()
    }

    fun toggleLayoutMode() {
        viewModelScope.launch {
            val next = if (_uiState.value.layoutMode == DashboardLayoutMode.FULL_BLEED_OVERLAY) {
                DashboardLayoutMode.SPLIT_BENTO
            } else {
                DashboardLayoutMode.FULL_BLEED_OVERLAY
            }
            prefsRepo.setLayoutMode(next)
        }
    }

    fun toggleThemeMode() {
        viewModelScope.launch {
            prefsRepo.setLightMode(!_uiState.value.isLightMode)
        }
    }

    fun saveSettings(
        sharedAlbumUrl: String,
        photoIntervalSeconds: Int,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geminiApiKey: String,
        gmailOAuthToken: String
    ) {
        viewModelScope.launch {
            prefsRepo.updateSettings(
                sharedAlbumUrl = sharedAlbumUrl,
                photoIntervalSeconds = photoIntervalSeconds,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                geminiApiKey = geminiApiKey,
                gmailOAuthToken = gmailToken(gmailOAuthToken)
            )
            refreshAll()
        }
    }

    private fun gmailToken(raw: String) = raw.trim()

    private fun restartPhotoSlideshowLoop() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (true) {
                val intervalMs = (currentConfig.photoIntervalSeconds.coerceAtLeast(10)) * 1000L
                delay(intervalMs)
                if (photoPool.size > 1) {
                    advanceToNextSlideInDeck()
                }
            }
        }
    }

    /**
     * Background Photo Pool Sync Loop (Nest Display Cloud / Live Album polling):
     * Polls the Shared / Live Album and local Favorites every 3 minutes in the background so
     * newly added or starred photos are queued into the deck automatically.
     */
    private fun startPeriodicPhotoPoolSyncLoop() {
        viewModelScope.launch {
            while (true) {
                delay(3 * 60 * 1000L) // Every 3 minutes
                refreshPhotos(forceReshuffle = false)
            }
        }
    }

    /**
     * Refreshes Gmail Digest & Weather every 15 minutes (Calendar updates instantly via
     * ContentObserver + a 15-min safety refresh so time-relative "Upcoming" window stays current).
     */
    private fun startPeriodicGmailAndWeatherSync() {
        viewModelScope.launch {
            refreshAll()
            while (true) {
                delay(15 * 60 * 1000L) // Every 15 minutes
                refreshPhotos()
                refreshGmailDigest()
                refreshWeather()
                refreshCalendar()
                _uiState.update { it.copy(batteryPercent = readBatteryPercent()) }
            }
        }
    }

    private fun startBurnInProtectionAndNightCheckLoop() {
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L) // Every 5 minutes shift pixels slightly
                val hour = LocalTime.now().hour
                val isNight = hour >= currentConfig.nightDimStartHour ||
                    hour < currentConfig.nightDimEndHour
                _uiState.update {
                    it.copy(
                        isNightDimmed = isNight,
                        pixelShiftOffsetX = Random.nextInt(-6, 7),
                        pixelShiftOffsetY = Random.nextInt(-6, 7),
                        batteryPercent = readBatteryPercent()
                    )
                }
            }
        }
    }

    private fun readBatteryPercent(): Int {
        val bm = getApplication<Application>()
            .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
    }
}
