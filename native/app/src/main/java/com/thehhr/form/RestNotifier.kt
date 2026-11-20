package com.thehhr.form

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.ui.exTitle
import kotlinx.coroutines.launch

/** True while MainActivity is on screen (the in-app rest cue handles alerts then). */
object AppVisibility { @Volatile var visible = false }

/**
 * Ongoing workout notification (lock screen + shade) that counts the rest down, with +30s / Skip / Log set,
 * plus an exact alarm that buzzes when rest ends while the screen is off. No foreground service needed.
 */
object RestNotifier {
    private const val CH_WORKOUT = "workout"
    private const val CH_REST_DONE = "rest_done"
    private const val ID_WORKOUT = 1
    private const val ID_REST_DONE = 2
    const val ACTION_EXTEND = "com.thehhr.form.REST_EXTEND"
    const val ACTION_SKIP = "com.thehhr.form.REST_SKIP"
    const val ACTION_LOG = "com.thehhr.form.LOG_SET"
    const val ACTION_ALARM = "com.thehhr.form.REST_ALARM"
    const val ACTION_SET_DONE = "com.thehhr.form.SET_DONE"
    const val ACTION_PAUSE = "com.thehhr.form.PAUSE"
    const val ID_LIVE = ID_WORKOUT

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WORKOUT, "Active workout", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Rest countdown and quick actions while you train"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_REST_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a rest period ends"
            setSound(null, null) // the app beeps itself, honouring the "Rest sound" setting
            enableVibration(false) // the app vibrates itself with its own pattern
        })
        Reminders.channel(ctx)
    }

    private fun broadcast(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, RestReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun openApp(ctx: Context, action: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, action.hashCode(), Intent(ctx, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The live workout notification (Android 16 Live Update when the system allows promotion; a normal ongoing
     * notification on older Android and other skins, e.g. Samsung). One clock: elapsed workout time, or the
     * rest / set-timer countdown while one runs. Current exercise in the text; Pause/Resume plus up to two actions.
     */
    fun build(ctx: Context, store: FormStore): Notification? {
        val w = store.workout ?: return null
        channels(ctx)
        val now = System.currentTimeMillis()
        val routine = store.data.routines.firstOrNull { it.id == w.routineId }
        val next = routine?.let { WorkoutLogic.nextTarget(w, it) }
        val current = next?.let { exTitle(store.exercise(it.exerciseId)) }
        val rest = w.rest?.takeIf { it.pausedRemaining == null && !w.paused && it.endsAt > now }
        val counted = routine?.let { r -> WorkoutLogic.entries(w, r).filter { it.item.exerciseId !in w.skipped } }.orEmpty()
        val done = counted.count { w.complete(it.item.exerciseId) }

        val b = NotificationCompat.Builder(ctx, CH_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_form)
            .setContentTitle(w.routineName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .setColorized(false) // promotion requires a non-colorized notification
            .setRequestPromotedOngoing(true) // Live Update on Android 16+, ignored before
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (w.paused) "Resume" else "Pause", broadcast(ctx, ACTION_PAUSE, 6))
        when {
            w.paused -> b.setContentText("Paused · $done/${counted.size} done").setShowWhen(false).setShortCriticalText("Paused")
            rest?.type == "set" -> {
                val ex = rest.exerciseId?.let { exTitle(store.exercise(it)) } ?: "Set"
                b.setContentText("Set ${(rest.setIndex ?: 0) + 1} · $ex").setShortCriticalText("Set")
                    .setWhen(rest.endsAt).setUsesChronometer(true).setChronometerCountDown(true)
                    .addAction(0, "Done", broadcast(ctx, ACTION_SET_DONE, 5))
            }
            rest != null -> {
                b.setContentText((if (rest.warmup) "Warm-up" else "Rest") + (current?.let { " · next: $it" } ?: "")).setShortCriticalText("Rest")
                    .setWhen(rest.endsAt).setUsesChronometer(true).setChronometerCountDown(true)
                    .addAction(0, "+30s", broadcast(ctx, ACTION_EXTEND, 1))
                    .addAction(0, "Skip", broadcast(ctx, ACTION_SKIP, 2))
            }
            else -> {
                b.setContentText(current?.let { "$it · $done/${counted.size} done" } ?: "All done · tap to finish").setShortCriticalText("Gym")
                    .setWhen(now - w.elapsedMs(now)).setUsesChronometer(true)
                if (next != null) b.addAction(0, "Log set", broadcast(ctx, ACTION_LOG, 4))
            }
        }
        return b.build()
    }

    fun sync(ctx: Context, store: FormStore) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val alarmPi = broadcast(ctx, ACTION_ALARM, 3)
        val w = store.workout
        if (w == null) {
            alarm.cancel(alarmPi)
            if (WorkoutService.running) ctx.stopService(Intent(ctx, WorkoutService::class.java))
            nm.cancel(ID_WORKOUT)
            return
        }
        // Start the foreground service once; afterwards just re-post the same notification id.
        if (WorkoutService.running) build(ctx, store)?.let { runCatching { nm.notify(ID_WORKOUT, it) } }
        else try {
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, WorkoutService::class.java))
        } catch (e: IllegalStateException) {
            // Background start not allowed (e.g. a cold receiver): fall back to a plain ongoing notification.
            build(ctx, store)?.let { runCatching { nm.notify(ID_WORKOUT, it) } }
        }

        val rest = w.rest?.takeIf { it.pausedRemaining == null && !w.paused }
        val now = System.currentTimeMillis()
        alarm.cancel(alarmPi)
        if (rest != null && rest.endsAt > now) {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rest.endsAt, alarmPi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rest.endsAt, alarmPi)
        }
    }

    fun restOver(ctx: Context, store: FormStore, title: String = "Rest over") {
        val w = store.workout ?: return
        if (AppVisibility.visible) return
        Haptics.restEnd(ctx, store.data.prefs.restSound)
        val routine = store.data.routines.firstOrNull { it.id == w.routineId }
        val next = routine?.let { WorkoutLogic.nextTarget(w, it) }?.let { exTitle(store.exercise(it.exerciseId)) }
        val n = Notification.Builder(ctx, CH_REST_DONE)
            .setSmallIcon(R.drawable.ic_stat_form)
            .setContentTitle(title)
            .setContentText(next?.let { "Up next: $it" } ?: "Back to it")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID_REST_DONE, n) }
    }
}

/** Notification actions and the rest-over alarm. Not exported: only our own PendingIntents reach it. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = FormStore.get(ctx)
        val pending = goAsync()
        store.launch {
            try {
                when (intent.action) {
                    RestNotifier.ACTION_ALARM -> {
                        val r = store.workout?.rest
                        if (r?.type == "set") {
                            // Set timer ran out: check the set (which starts the normal rest), then alert.
                            store.whenReady { completeSetTimer(r.endsAt) }
                            RestNotifier.restOver(ctx, store, title = "Set ${(r.setIndex ?: 0) + 1} done")
                        } else RestNotifier.restOver(ctx, store)
                    }
                    RestNotifier.ACTION_SET_DONE -> store.whenReady { completeSetTimer() }
                    RestNotifier.ACTION_PAUSE -> store.whenReady { togglePause() }
                    RestNotifier.ACTION_EXTEND -> store.whenReady { extendRest(30) }
                    RestNotifier.ACTION_SKIP -> store.whenReady { skipRest() }
                    RestNotifier.ACTION_LOG -> store.whenReady { logNextSet() }
                }
            } finally { pending.finish() }
        }
    }
}
