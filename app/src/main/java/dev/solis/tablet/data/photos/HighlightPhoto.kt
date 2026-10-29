package dev.solis.tablet.data.photos

data class HighlightPhoto(
    val id: String,
    val imageUrl: String,
    val caption: String?,
    val capturedDateText: String?,
    val locationText: String?,
    val isPortrait: Boolean = false
) {
    companion object {
        // Shared album link configured from user request: https://photos.app.goo.gl/58LK2VjMhc6nowMg9
        const val DEFAULT_SHARED_ALBUM_URL = "https://photos.app.goo.gl/58LK2VjMhc6nowMg9"

        fun sample() = HighlightPhoto(
            id = "sample-1",
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1920&q=85",
            caption = "Google Photos Highlights",
            capturedDateText = "Autumn Highlights",
            locationText = "Yosemite Valley, CA"
        )
    }
}
