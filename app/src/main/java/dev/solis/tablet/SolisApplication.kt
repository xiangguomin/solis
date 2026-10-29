package dev.solis.tablet

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import dev.solis.tablet.sync.SolisSyncWorker

class SolisApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        SolisSyncWorker.schedulePeriodicSync(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("solis_photos_cache"))
                    .maxSizeBytes(1024L * 1024L * 1024L) // 1 GB local rolling photo cache
                    .build()
            }
            .build()
    }
}
