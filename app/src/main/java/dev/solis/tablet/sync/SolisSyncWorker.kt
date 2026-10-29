package dev.solis.tablet.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.solis.tablet.data.local.SolisPreferencesRepository
import dev.solis.tablet.data.photos.SolisPhotoManager
import dev.solis.tablet.data.weather.OpenMeteoWeatherRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that keeps Google Photos shared album highlights and weather
 * fresh even if the app is running in Daydream/background mode.
 */
class SolisSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val prefs = SolisPreferencesRepository(applicationContext).configFlow.first()
            SolisPhotoManager(applicationContext).loadFavoritePhotos(prefs.sharedAlbumUrl)
            OpenMeteoWeatherRepository().fetchForecast(
                latitude = prefs.latitude,
                longitude = prefs.longitude,
                locationName = prefs.locationName
            )
            Result.success()
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val WORK_NAME = "solis_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SolisSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
