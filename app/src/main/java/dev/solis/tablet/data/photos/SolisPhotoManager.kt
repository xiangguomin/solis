package dev.solis.tablet.data.photos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Unified Photo Coordinator for Starred / Favorite Photos ONLY:
 * 1. Loads Starred/Favorite photos selected from the user's Google Photos Cloud account via the
 *    Android Cloud Photo Picker (`★ Pick Favorites`) and cached on the tablet.
 * 2. Reads any local MediaStore images explicitly starred as Favorites (`IS_FAVORITE = 1`).
 * 3. Includes the user's Shared Favorites Album (`https://photos.app.goo.gl/58LK2VjMhc6nowMg9`).
 * Strictly NEVER loads unstarred Camera or Screenshot photos.
 */
class SolisPhotoManager(private val context: Context) {
    private val accountRepo = GooglePhotosAccountRepository(context)
    private val sharedAlbumRepo = SharedAlbumPhotoRepository(context)

    suspend fun loadFavoritePhotos(
        sharedAlbumUrl: String = HighlightPhoto.DEFAULT_SHARED_ALBUM_URL
    ): List<HighlightPhoto> {
        val localStarred = queryStarredMediaStorePhotos()
        val accountStarred = accountRepo.loadCachedAccountPhotos()
        val sharedStarred = if (sharedAlbumUrl.isNotBlank()) {
            sharedAlbumRepo.fetchPhotosFromSharedAlbum(sharedAlbumUrl)
                .getOrDefault(emptyList())
                .filter { it.id != "sample-1" }
        } else {
            emptyList()
        }

        return (accountStarred + localStarred + sharedStarred)
            .distinctBy { it.imageUrl }
    }

    suspend fun addFavoritePhotosFromPicker(uris: List<Uri>): List<HighlightPhoto> {
        accountRepo.importFromSystemPhotoPicker(uris)
        return loadFavoritePhotos()
    }

    private fun queryStarredMediaStorePhotos(): List<HighlightPhoto> {
        val photos = mutableListOf<HighlightPhoto>()
        runCatching {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN
            )
            // Strictly only IS_FAVORITE = 1 (Starred photos)
            val selection = "${MediaStore.Images.Media.IS_FAVORITE} = 1"
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Starred Photo"
                    val dateTakenMillis = cursor.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val meta = PhotoMetadataExtractor.extractFromMediaStoreUri(
                        context = context,
                        imageUri = contentUri,
                        dateTakenMillis = dateTakenMillis
                    )
                    photos.add(
                        HighlightPhoto(
                            id = "starred-$id",
                            imageUrl = contentUri.toString(),
                            caption = "★ Favorite",
                            capturedDateText = meta.takenTimeText ?: name,
                            locationText = meta.locationCityState,
                            isPortrait = meta.isPortrait
                        )
                    )
                }
            }
        }
        return photos
    }
}
