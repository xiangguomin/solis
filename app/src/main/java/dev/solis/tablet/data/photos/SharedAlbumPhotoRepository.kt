package dev.solis.tablet.data.photos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves shared Google Photos album / Live Album links (e.g. https://photos.app.goo.gl/58LK2VjMhc6nowMg9)
 * into high-resolution slideshow items with accurate portrait/landscape dimensions, capture timestamps,
 * and EXIF GPS location (`City, State`) when location sharing is enabled on the album.
 */
class SharedAlbumPhotoRepository(
    private val context: Context? = null,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    // Matches Google Photos AF_initDataCallback media entries: ["https://lh3.googleusercontent.com/pw/...",width,height
    private val structuredPhotoRegex = Regex(
        """\["(https://lh3\.googleusercontent\.com/pw/[A-Za-z0-9_-]+)",(\d+),(\d+)[^\]]*\],(\d{12,13})"""
    )
    private val fallbackDimensionRegex = Regex(
        """\["(https://lh3\.googleusercontent\.com/pw/[A-Za-z0-9_-]+)",(\d+),(\d+)"""
    )
    private val photoUrlRegex =
        Regex("""https://lh3\.googleusercontent\.com/pw/[A-Za-z0-9_-]+""")
    private val exifLocationCache = mutableMapOf<String, PhotoMetadataExtractor.ExtractedMetadata>()

    suspend fun fetchPhotosFromSharedAlbum(
        sharedAlbumUrl: String = HighlightPhoto.DEFAULT_SHARED_ALBUM_URL
    ): Result<List<HighlightPhoto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(sharedAlbumUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .build()

            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Failed to load shared album: HTTP ${response.code}" }
                val html = response.body?.string().orEmpty()

                val structuredPhotos = mutableListOf<HighlightPhoto>()
                val seenBaseUrls = mutableSetOf<String>()

                structuredPhotoRegex.findAll(html).forEach { match ->
                    val baseUrl = match.groupValues[1]
                    val width = match.groupValues[2].toIntOrNull() ?: 0
                    val height = match.groupValues[3].toIntOrNull() ?: 0
                    val timestampMillis = match.groupValues[4].toLongOrNull() ?: 0L
                    if (width >= 200 && height >= 200 && seenBaseUrls.add(baseUrl)) {
                        val exifMeta = fetchOriginalExifHeader(baseUrl, timestampMillis)
                        structuredPhotos.add(
                            HighlightPhoto(
                                id = "shared-${structuredPhotos.size}",
                                imageUrl = "$baseUrl=w1920-h1920",
                                caption = "★ Live Album",
                                capturedDateText = exifMeta?.takenTimeText
                                    ?: PhotoMetadataExtractor.formatEpochMillis(timestampMillis)
                                    ?: "Google Photos Album",
                                locationText = exifMeta?.locationCityState,
                                isPortrait = height > width
                            )
                        )
                    }
                }

                if (structuredPhotos.isEmpty()) {
                    fallbackDimensionRegex.findAll(html).forEach { match ->
                        val baseUrl = match.groupValues[1]
                        val width = match.groupValues[2].toIntOrNull() ?: 0
                        val height = match.groupValues[3].toIntOrNull() ?: 0
                        if (width >= 200 && height >= 200 && seenBaseUrls.add(baseUrl)) {
                            structuredPhotos.add(
                                HighlightPhoto(
                                    id = "shared-${structuredPhotos.size}",
                                    imageUrl = "$baseUrl=w1920-h1920",
                                    caption = "★ Live Album",
                                    capturedDateText = "Google Photos Album",
                                    locationText = null,
                                    isPortrait = height > width
                                )
                            )
                        }
                    }
                }

                if (structuredPhotos.isNotEmpty()) {
                    structuredPhotos
                } else {
                    val distinctUrls = photoUrlRegex.findAll(html)
                        .map { "${it.value}=w1920-h1920" }
                        .distinct()
                        .toList()

                    distinctUrls.mapIndexed { idx, url ->
                        HighlightPhoto(
                            id = "shared-$idx",
                            imageUrl = url,
                            caption = "★ Live Album",
                            capturedDateText = "Google Photos Highlight",
                            locationText = null
                        )
                    }.ifEmpty { listOf(HighlightPhoto.sample()) }
                }
            }
        }
    }

    /**
     * Fetches the first 64KB of `$baseUrl=d` (original download) to read EXIF GPS & DateTimeOriginal
     * when "Share photo location" is enabled on the Google Photos shared album.
     */
    private fun fetchOriginalExifHeader(
        baseUrl: String,
        fallbackMillis: Long
    ): PhotoMetadataExtractor.ExtractedMetadata? {
        val ctx = context ?: return null
        exifLocationCache[baseUrl]?.let { return it }
        return runCatching {
            val req = Request.Builder()
                .url("$baseUrl=d")
                .header("Range", "bytes=0-65535")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val stream = resp.body?.byteStream() ?: return@use null
                PhotoMetadataExtractor.extractFromStream(ctx, stream, fallbackMillis).also {
                    exifLocationCache[baseUrl] = it
                }
            }
        }.getOrNull()
    }
}
