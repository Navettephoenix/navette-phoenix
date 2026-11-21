package com.phoenix.navette28

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ReservationMessaging : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "reservation-status", "Suivi des réservations", NotificationManager.IMPORTANCE_DEFAULT
        ))
        val intent = Intent(this, MainActivity::class.java).putExtra("showHistory", true)
        val pending = PendingIntent.getActivity(this, 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(20, Notification.Builder(this, "reservation-status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Le statut de votre demande a changé.")
            .setContentIntent(pending).setAutoCancel(true).build())
    }
}
