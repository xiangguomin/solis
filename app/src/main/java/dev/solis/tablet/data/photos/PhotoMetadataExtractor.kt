package dev.solis.tablet.data.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracts photo taken time, reverse-geocoded location (`City, State`), and portrait/landscape
 * orientation from EXIF metadata or image bounds.
 */
object PhotoMetadataExtractor {

    private val exifDateParser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.US)

    data class ExtractedMetadata(
        val takenTimeText: String?,
        val locationCityState: String?,
        val isPortrait: Boolean = false
    )

    fun extractFromFile(context: Context, file: File): ExtractedMetadata {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            parseExif(
                context = context,
                exif = exif,
                fallbackMillis = file.lastModified(),
                rawWidth = bounds.outWidth,
                rawHeight = bounds.outHeight
            )
        }.getOrDefault(ExtractedMetadata(null, null, false))
    }

    fun extractFromStream(
        context: Context,
        inputStream: java.io.InputStream,
        fallbackMillis: Long = 0L
    ): ExtractedMetadata {
        return runCatching {
            val exif = ExifInterface(inputStream)
            parseExif(
                context = context,
                exif = exif,
                fallbackMillis = fallbackMillis
            )
        }.getOrDefault(ExtractedMetadata(formatEpochMillis(fallbackMillis), null, false))
    }

    fun extractFromMediaStoreUri(
        context: Context,
        imageUri: Uri,
        dateTakenMillis: Long
    ): ExtractedMetadata {
        return runCatching {
            // Require original URI via MediaStore.setRequireOriginal so GPS tags are preserved
            val originalUri = runCatching { MediaStore.setRequireOriginal(imageUri) }
                .getOrDefault(imageUri)

            var rawWidth = 0
            var rawHeight = 0
            runCatching {
                context.contentResolver.openInputStream(originalUri)?.use { boundsStream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(boundsStream, null, bounds)
                    rawWidth = bounds.outWidth
                    rawHeight = bounds.outHeight
                }
            }

            context.contentResolver.openInputStream(originalUri)?.use { stream ->
                val exif = ExifInterface(stream)
                parseExif(
                    context = context,
                    exif = exif,
                    fallbackMillis = dateTakenMillis,
                    rawWidth = rawWidth,
                    rawHeight = rawHeight
                )
            } ?: ExtractedMetadata(formatEpochMillis(dateTakenMillis), null, rawHeight > rawWidth)
        }.getOrElse {
            ExtractedMetadata(formatEpochMillis(dateTakenMillis), null, false)
        }
    }

    private fun parseExif(
        context: Context,
        exif: ExifInterface,
        fallbackMillis: Long,
        rawWidth: Int = 0,
        rawHeight: Int = 0
    ): ExtractedMetadata {
        val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

        val takenTimeText = if (!rawDate.isNullOrBlank()) {
            runCatching {
                val parsed = exifDateParser.parse(rawDate)
                if (parsed != null) formatEpochMillis(parsed.time) else rawDate
            }.getOrDefault(rawDate)
        } else if (fallbackMillis > 0L) {
            formatEpochMillis(fallbackMillis)
        } else {
            null
        }

        val latLong = FloatArray(2)
        val hasGps = exif.getLatLong(latLong)
        val cityState = if (hasGps) {
            reverseGeocodeCityState(context, latLong[0].toDouble(), latLong[1].toDouble())
        } else {
            null
        }

        val exifW = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 } ?: rawWidth
        val exifH = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 } ?: rawHeight
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotated90 = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

        val displayWidth = if (rotated90) exifH else exifW
        val displayHeight = if (rotated90) exifW else exifH
        val isPortrait = displayHeight > displayWidth && displayWidth > 0

        return ExtractedMetadata(
            takenTimeText = takenTimeText,
            locationCityState = cityState,
            isPortrait = isPortrait
        )
    }

    fun formatEpochMillis(millis: Long): String? {
        if (millis <= 0L) return null
        return runCatching {
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(displayDateFormatter)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeCityState(
        context: Context,
        lat: Double,
        lon: Double
    ): String? {
        if (lat == 0.0 && lon == 0.0) return null
        val fromDeviceGeocoder = runCatching {
            val geocoder = Geocoder(context, Locale.US)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull() ?: return@runCatching null
            val city = addr.locality ?: addr.subAdminArea ?: addr.featureName
            val state = addr.adminArea ?: addr.countryName
            when {
                !city.isNullOrBlank() && !state.isNullOrBlank() -> "$city, $state"
                !city.isNullOrBlank() -> city
                !state.isNullOrBlank() -> state
                else -> null
            }
        }.getOrNull()

        if (!fromDeviceGeocoder.isNullOrBlank()) {
            return fromDeviceGeocoder
        }

        // Fallback HTTP reverse-geocoder in case the Lenovo ZUI ROM Geocoder service returns null
        return runCatching {
            val url = java.net.URL(
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            conn.inputStream.bufferedReader().use { reader ->
                val json = org.json.JSONObject(reader.readText())
                val city = json.optString("city").ifBlank { json.optString("locality") }
                val state = json.optString("principalSubdivision").ifBlank { json.optString("countryName") }
                when {
                    city.isNotBlank() && state.isNotBlank() -> "$city, $state"
                    city.isNotBlank() -> city
                    state.isNotBlank() -> state
                    else -> null
                }
            }
        }.getOrNull()
    }
}
