package com.thehhr.form

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.DeviceStore
import com.thehhr.form.data.Routine
import com.thehhr.form.data.VaultData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Daily workout reminder on scheduled training days. Off by default; time is device-only. */
object Reminders {
    private const val CH = "reminders"
    const val ACTION_REMIND = "com.thehhr.form.REMIND"

    fun channel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A nudge on days with a scheduled routine" })
    }

    /** The routine to remind about, or null: needs a scheduled, non-empty routine, no workout running, nothing logged today. */
    fun routineToRemind(data: VaultData, workout: ActiveWorkout?, today: LocalDate): Routine? {
        if (workout != null) return null
        val key = today.toString()
        if (data.logs.any { it.date == key }) return null
        val id = data.schedule[today.dayOfWeek.value % 7] ?: return null
        return data.routines.firstOrNull { it.id == id && it.items.isNotEmpty() }
    }

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 10, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** (Re)arms the next reminder; called on app start, boot, update, and after each reminder. */
    fun schedule(ctx: Context, device: DeviceStore) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm.cancel(pi(ctx))
        if (!device.reminderEnabled) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atStartOfDay().plusMinutes(device.reminderMinutes.toLong())
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Exact: an inexact window is deferred by Doze and app-standby buckets (hours for a rarely opened app), which
        // looked like "no reminder". USE_EXACT_ALARM covers 13+; 12 falls back to allow-while-idle if not granted.
        if (android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(ctx))
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(ctx))
    }

    fun cancel(ctx: Context) { ctx.getSystemService(NotificationManager::class.java).cancel(20) }

    fun notify(ctx: Context, routine: Routine) {
        channel(ctx)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_form)
            .setContentTitle("Time to train")
            .setContentText("Today: ${routine.name} · ${routine.items.size} exercises")
            .setAutoCancel(true)
            .setContentIntent(RestNotifier.openApp(ctx))
            .addAction(Notification.Action.Builder(null, "Start workout", RestNotifier.openApp(ctx, MainActivity.ACTION_START_TODAY)).build())
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(20, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = FormStore.get(ctx)
        val pending = goAsync()
        store.launch {
            try {
                // A cold process must load the catalog and vault first; background broadcasts may run up to a minute.
                store.whenReady(timeoutMs = 40_000) { Reminders.routineToRemind(data, workout, LocalDate.now()) }?.let { Reminders.notify(ctx, it) }
                Reminders.schedule(ctx, store.device)
            } finally { pending.finish() }
        }
    }
}

/** Alarms don't survive reboots or app updates; re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(ctx, DeviceStore(ctx))
        }
    }
}
