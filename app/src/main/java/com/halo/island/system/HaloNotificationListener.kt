package com.halo.island.system

import android.app.*
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.halo.island.core.PreviewPrivacy
import com.halo.island.halo

data class NotificationPreview(
    val app: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val intent: PendingIntent?,
    val icon: android.graphics.drawable.Icon?,
)

class HaloNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        halo.media.connect()
    }

    override fun onListenerDisconnected() {
        halo.media.disconnect()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        halo.media.disconnect()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        val options = halo.settings.state.value
        if (options.privacy == PreviewPrivacy.OFF || s.packageName == packageName) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        if (s.packageName in options.exclusions.split(',', '\n').map { it.trim() }) return
        val n = s.notification
        if (
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
                n.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        )
            return
        // Android's redacted/private payloads are never expanded, even in full-preview mode.
        val safe =
            n.visibility == Notification.VISIBILITY_PUBLIC &&
                n.category !in
                    setOf(
                        Notification.CATEGORY_MESSAGE,
                        Notification.CATEGORY_EMAIL,
                        Notification.CATEGORY_CALL,
                    )
        val title =
            if (safe && options.privacy == PreviewPrivacy.FULL)
                n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(100)
            else null
        val text =
            if (safe && options.privacy == PreviewPrivacy.FULL)
                n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(160)
            else null
        // An additional local safeguard for OTP-like content; nothing is persisted or logged.
        val sensitive =
            Regex("(?i)\\b(otp|password|verification|security code|passcode)\\b|\\b\\d{4,8}\\b")
                .containsMatchIn("$title $text")
        val app =
            if (options.privacy == PreviewPrivacy.ICON) "Notification"
            else
                runCatching {
                        packageManager
                            .getApplicationLabel(
                                packageManager.getApplicationInfo(s.packageName, 0)
                            )
                            .toString()
                    }
                    .getOrDefault("Notification")
        halo.previewNotification(
            NotificationPreview(
                app,
                s.packageName,
                if (sensitive) null else title,
                if (sensitive) null else text,
                n.contentIntent,
                n.smallIcon,
            )
        )
    }
}
