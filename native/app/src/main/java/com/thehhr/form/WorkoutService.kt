package com.thehhr.form

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps the live workout notification in a foreground service while a workout runs, so the system treats it
 * as an ongoing activity (Live Updates on Android 16, kept alive on aggressive OEM skins).
 * Updates after start go through NotificationManager.notify on the same id (see RestNotifier.sync).
 */
class WorkoutService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = FormStore.get(this)
        RestNotifier.channels(this)
        val notification = RestNotifier.build(this, store)
        // startForeground must happen right away on every start, even when the workout just ended,
        // or the system kills the app (ForegroundServiceDidNotStartInTimeException).
        val shown = notification ?: NotificationCompat.Builder(this, "workout").setSmallIcon(R.drawable.ic_stat_form)
            .setContentTitle("Workout").setSilent(true).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, RestNotifier.ID_LIVE, shown, type)
        running = true
        if (notification == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object { @Volatile var running = false }
}
