package dev.solis.tablet.data.gmail

import kotlinx.serialization.Serializable

@Serializable
enum class DigestCategory(val badgeLabel: String, val colorHex: Long) {
    ACTION_REQUIRED("Action", 0xFFF28482),
    DELIVERY("Delivery", 0xFF4EA8DE),
    FINANCE("Finance", 0xFFFFC857),
    TRAVEL("Travel", 0xFF9B5DE5),
    PERSONAL("Personal", 0xFF52B788)
}

@Serializable
data class GmailDigestItem(
    val threadId: String,
    val category: DigestCategory,
    val senderName: String,
    val summaryOneLiner: String,
    val receivedTimeText: String,
    val subject: String = summaryOneLiner,
    val senderEmail: String = "",
    val fullBodySnippet: String = summaryOneLiner
)
