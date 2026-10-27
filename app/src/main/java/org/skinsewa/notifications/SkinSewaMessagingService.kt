package org.skinsewa.notifications

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.skinsewa.MainActivity
import org.skinsewa.R
import org.skinsewa.SkinSewaApplication

class SkinSewaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? SkinSewaApplication)?.syncFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val caseId = message.data[MainActivity.EXTRA_NOTIFICATION_CASE_ID]?.toLongOrNull() ?: return
        val type = message.data["type"]
        val fallback = when (type) {
            "case_assigned" -> "A new patient case has been assigned to you."
            else -> "You have a new message about a patient case."
        }
        val title = message.notification?.title ?: if (type == "case_assigned") "New case assigned" else "New case message"
        val body = message.notification?.body ?: fallback
        val notificationId = "$type:$caseId".hashCode()
        val openCase = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_CASE_ID, caseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openCase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, SkinSewaApplication.CHANNEL_CASE_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (security: SecurityException) {
            Log.i(TAG, "Notification permission is not currently granted.")
        }
    }

    private companion object {
        const val TAG = "SkinSewaFCM"
    }
}
