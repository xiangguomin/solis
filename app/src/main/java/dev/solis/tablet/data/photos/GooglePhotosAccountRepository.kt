package dev.solis.tablet.data.photos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages retrieving highlighted photos from the user's own Google Photos account via:
 * 1. Android Cloud Media / Photo Picker (`PickMultipleVisualMedia`) — directly accesses the signed-in
 *    Google Photos app on the Lenovo tablet (including Favorites ⭐ and Highlights albums) and persists
 *    selected photos into local tablet storage so they never expire.
 * 2. Official Google Photos Picker REST API (`https://photospicker.googleapis.com/v1/sessions`) —
 *    creates an authenticated picker session with OAuth scope `photospicker.mediaitems.readonly`
 *    and caches downloaded high-res images locally (since Google's baseUrls expire after 60 mins).
 */
class GooglePhotosAccountRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val localHighlightsDir: File by lazy {
        File(context.filesDir, "google_photos_highlights").apply { mkdirs() }
    }
    private val manifestFile: File by lazy {
        File(localHighlightsDir, "highlights_manifest.json")
    }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class CachedPhotoEntry(
        val id: String,
        val localFilePath: String,
        val caption: String?,
        val capturedDateText: String?,
        val locationText: String?,
        val isPortrait: Boolean = false,
        val isSelected: Boolean = true
    )

    /**
     * Loads all locally persisted Google Photos highlights from the tablet's internal storage.
     */
    suspend fun loadCachedAccountPhotos(onlySelected: Boolean = true): List<HighlightPhoto> =
        withContext(Dispatchers.IO) {
            if (!manifestFile.exists()) return@withContext emptyList()
            runCatching {
                val entries = json.decodeFromString<List<CachedPhotoEntry>>(manifestFile.readText())
                var manifestUpdated = false
                val cleanedEntries = mutableListOf<CachedPhotoEntry>()

                val photos = entries.mapNotNull { entry ->
                    val file = File(entry.localFilePath)
                    if (file.exists()) {
                        val hadFakeLocation = entry.locationText == "San Jose, CA" ||
                            entry.locationText == "Google Photos"
                        val resolvedLocation = if (hadFakeLocation) {
                            manifestUpdated = true
                            PhotoMetadataExtractor.extractFromFile(context, file).locationCityState
                        } else {
                            entry.locationText
                        }
                        val updatedEntry = if (hadFakeLocation) {
                            entry.copy(locationText = resolvedLocation)
                        } else {
                            entry
                        }
                        cleanedEntries.add(updatedEntry)

                        if (onlySelected && !updatedEntry.isSelected) {
                            null
                        } else {
                            HighlightPhoto(
                                id = updatedEntry.id,
                                imageUrl = file.toURI().toString(),
                                caption = updatedEntry.caption,
                                capturedDateText = updatedEntry.capturedDateText,
                                locationText = updatedEntry.locationText,
                                isPortrait = updatedEntry.isPortrait,
                                isSelected = updatedEntry.isSelected
                            )
                        }
                    } else null
                }

                if (manifestUpdated) {
                    saveManifestEntries(cleanedEntries)
                }
                photos
            }.getOrDefault(emptyList())
        }

    /**
     * Updates which cached photos are selected/enabled for the slideshow (supports Select All / Unselect All).
     */
    suspend fun updateSelectedPhotos(selectedIds: Set<String>) = withContext(Dispatchers.IO) {
        val entries = loadManifestEntries()
        val updated = entries.map { it.copy(isSelected = it.id in selectedIds) }
        saveManifestEntries(updated)
    }

    /**
     * Permanently deletes unselected photos from local cache storage when the user chooses to remove them.
     */
    suspend fun deleteUnselectedPhotos(keepIds: Set<String>) = withContext(Dispatchers.IO) {
        val entries = loadManifestEntries()
        val remaining = mutableListOf<CachedPhotoEntry>()
        for (entry in entries) {
            if (entry.id in keepIds) {
                remaining.add(entry.copy(isSelected = true))
            } else {
                runCatching { File(entry.localFilePath).delete() }
            }
        }
        saveManifestEntries(remaining)
    }

    /**
     * Copies photos chosen from the user's Google Photos account via Android's Cloud Photo Picker
     * (`PickMultipleVisualMedia`) into permanent local storage on the Lenovo tablet.
     */
    suspend fun importFromSystemPhotoPicker(uris: List<Uri>): List<HighlightPhoto> =
        withContext(Dispatchers.IO) {
            val existing = loadManifestEntries().toMutableList()

            uris.forEachIndexed { idx, uri ->
                runCatching {
                    val id = "acct-${UUID.randomUUID().toString().take(8)}"
                    val destFile = File(localHighlightsDir, "$id.jpg")
                    // Request original unredacted bytes when possible so EXIF GPS is preserved
                    val sourceUri = runCatching {
                        android.provider.MediaStore.setRequireOriginal(uri)
                    }.getOrDefault(uri)

                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0) {
                        val meta = PhotoMetadataExtractor.extractFromFile(context, destFile)
                        existing.add(
                            CachedPhotoEntry(
                                id = id,
                                localFilePath = destFile.absolutePath,
                                caption = "★ Favorite",
                                capturedDateText = meta.takenTimeText ?: "Unknown Date",
                                locationText = meta.locationCityState,
                                isPortrait = meta.isPortrait
                            )
                        )
                    }
                }
            }

            saveManifestEntries(existing)
            loadCachedAccountPhotos()
        }

    /**
     * Official Google Photos Picker REST API (`photospicker.googleapis.com/v1`):
     * Step 1: Creates a new Picker Session for the authenticated user (`photospicker.mediaitems.readonly`).
     * Returns the `pickerUri` that can be opened on the tablet or via QR code on the user's phone.
     */
    suspend fun createCloudPickerSession(oauthAccessToken: String): Result<PickerSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://photospicker.googleapis.com/v1/sessions")
                    .header("Authorization", "Bearer $oauthAccessToken")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Picker API HTTP ${response.code}" }
                    val obj = JSONObject(response.body?.string() ?: "{}")
                    PickerSession(
                        sessionId = obj.getString("id"),
                        pickerUri = obj.getString("pickerUri"),
                        mediaItemsSet = obj.optBoolean("mediaItemsSet", false)
                    )
                }
            }
        }

    /**
     * Step 2: Once the user finishes selecting their Favorites/Highlights in the Picker session,
     * fetches the `PickedMediaItem` list and downloads high-res copies (`=w1920-h1200-no`) locally
     * before the 60-minute baseUrl expiry window.
     */
    suspend fun syncMediaItemsFromPickerSession(
        oauthAccessToken: String,
        sessionId: String
    ): Result<List<HighlightPhoto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://photospicker.googleapis.com/v1/mediaItems?sessionId=$sessionId&pageSize=100")
                .header("Authorization", "Bearer $oauthAccessToken")
                .get()
                .build()

            val entries = loadManifestEntries().toMutableList()

            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Picker mediaItems HTTP ${response.code}" }
                val root = JSONObject(response.body?.string() ?: "{}")
                val items = root.optJSONArray("mediaItems") ?: return@use

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getString("id")
                    val createTime = item.optString("createTime", "").take(10)
                    val mediaFile = item.getJSONObject("mediaFile")
                    val baseUrl = mediaFile.getString("baseUrl")
                    val downloadUrl = "$baseUrl=w1920-h1200-no"

                    val destFile = File(localHighlightsDir, "picker_$id.jpg")
                    if (!destFile.exists()) {
                        downloadPhotoWithAuth(downloadUrl, oauthAccessToken, destFile)
                    }

                    if (destFile.exists() && entries.none { it.id == id }) {
                        entries.add(
                            CachedPhotoEntry(
                                id = id,
                                localFilePath = destFile.absolutePath,
                                caption = "Google Photos Highlight",
                                capturedDateText = createTime.ifEmpty { "My Highlights" },
                                locationText = "Google Photos"
                            )
                        )
                    }
                }
            }

            saveManifestEntries(entries)
            loadCachedAccountPhotos()
        }
    }

    private fun downloadPhotoWithAuth(url: String, token: String, destFile: File) {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                resp.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun loadManifestEntries(): List<CachedPhotoEntry> {
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CachedPhotoEntry>>(manifestFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun saveManifestEntries(entries: List<CachedPhotoEntry>) {
        manifestFile.writeText(json.encodeToString(entries))
    }

    data class PickerSession(
        val sessionId: String,
        val pickerUri: String,
        val mediaItemsSet: Boolean
    )
}
