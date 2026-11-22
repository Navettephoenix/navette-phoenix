package com.phoenix.navette28

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ReservationMessaging : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val prefs = preferences(this)
        if (!prefs.getBoolean("enabled", false)) return
        prefs.edit { putString("pending", token) }
        syncPendingToken(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "reservation-status") return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                "reservation-status", "Suivi des réservations", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("showHistory", true)
        val pending = PendingIntent.getActivity(this, 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(20, NotificationCompat.Builder(this, "reservation-status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Le statut de votre demande a changé.")
            .setContentIntent(pending).setAutoCancel(true).build())
    }

    companion object {
        private fun preferences(context: Context) =
            context.getSharedPreferences("reservation-notifications", Context.MODE_PRIVATE)

        fun markEnabled(context: Context) = preferences(context).edit { putBoolean("enabled", true) }

        fun syncPendingToken(context: Context) {
            val prefs = preferences(context)
            val token = prefs.getString("pending", null) ?: return
            val gateway = FirebaseGateway.get()
            if (!gateway.available) return
            gateway.call("refreshReservationNotifications", mapOf("pushToken" to token))
                .addOnSuccessListener {
                    if (prefs.getString("pending", null) == token) prefs.edit { remove("pending") }
                }
        }
    }
}
