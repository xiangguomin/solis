package dev.solis.tablet.data.gmail

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listens directly to real Gmail (`com.google.android.gm`) notifications arriving on the
 * Lenovo tablet so real important emails appear immediately without fake placeholder data.
 */
class GmailNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshFromActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.packageName == GMAIL_PACKAGE) {
            refreshFromActiveNotifications()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == GMAIL_PACKAGE) {
            refreshFromActiveNotifications()
        }
    }

    private fun refreshFromActiveNotifications() {
        runCatching {
            val sbns = activeNotifications ?: return
            val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
            val parsed = mutableListOf<GmailDigestItem>()

            for (sbn in sbns) {
                if (sbn.packageName != GMAIL_PACKAGE) continue
                val n = sbn.notification ?: continue
                // Skip group summary header notifications
                if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue

                val extras = n.extras ?: continue
                val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val subject = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

                if (sender.isEmpty() && subject.isEmpty()) continue

                val body = bigText.ifEmpty { subject }
                val oneLiner = subject.ifEmpty { body.lineSequence().firstOrNull().orEmpty() }

                parsed.add(
                    GmailDigestItem(
                        threadId = sbn.key ?: "notif-${sbn.id}",
                        category = GmailDigestRepository.inferCategory("$sender $subject $body"),
                        senderName = sender.ifEmpty { "Gmail" },
                        summaryOneLiner = oneLiner,
                        receivedTimeText = timeFmt.format(Date(sbn.postTime)),
                        subject = subject.ifEmpty { oneLiner },
                        senderEmail = subText,
                        fullBodySnippet = body
                    )
                )
            }

            if (parsed.isNotEmpty()) {
                _liveNotificationItems.value = parsed.distinctBy { "${it.senderName}|${it.subject}" }
            }
        }
    }

    companion object {
        private const val GMAIL_PACKAGE = "com.google.android.gm"
        private val _liveNotificationItems = MutableStateFlow<List<GmailDigestItem>>(emptyList())
        val liveNotificationItems: StateFlow<List<GmailDigestItem>> = _liveNotificationItems.asStateFlow()
    }
}
