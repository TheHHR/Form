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

    fun sync(ctx: Context, store: FormStore) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val alarmPi = broadcast(ctx, ACTION_ALARM, 3)
        val w = store.workout
        if (w == null) {
            nm.cancel(ID_WORKOUT); alarm.cancel(alarmPi); return
        }
        channels(ctx)
        val now = System.currentTimeMillis()
        val routine = store.data.routines.firstOrNull { it.id == w.routineId }
        val next = routine?.let { WorkoutLogic.nextTarget(w, it) }
        val nextName = next?.let { exTitle(store.exercise(it.exerciseId)) }
        val rest = w.rest?.takeIf { it.pausedRemaining == null && !w.paused }
        val counted = routine?.let { r -> WorkoutLogic.entries(w, r).filter { it.item.exerciseId !in w.skipped } }.orEmpty()
        val done = counted.count { w.complete(it.item.exerciseId) }

        val b = Notification.Builder(ctx, CH_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_form)
            .setContentTitle(w.routineName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .setShowWhen(true)
            .setUsesChronometer(!w.paused)
        when {
            w.paused -> b.setContentText("Paused · $done/${counted.size} exercises").setShowWhen(false)
            rest != null && rest.endsAt > now && rest.type == "set" -> {
                val ex = rest.exerciseId?.let { exTitle(store.exercise(it)) } ?: "Set"
                b.setContentText("Set ${(rest.setIndex ?: 0) + 1} · $ex")
                    .setWhen(rest.endsAt).setChronometerCountDown(true)
                    .addAction(Notification.Action.Builder(null, "Done", broadcast(ctx, ACTION_SET_DONE, 5)).build())
                    .addAction(Notification.Action.Builder(null, "Cancel", broadcast(ctx, ACTION_SKIP, 2)).build())
            }
            rest != null && rest.endsAt > now -> {
                val label = when { rest.warmup -> "Warm-up"; rest.type == "exercise" -> "Rest before next exercise"; else -> "Rest" }
                b.setContentText(label + (nextName?.let { " · next: $it" } ?: ""))
                    .setWhen(rest.endsAt).setChronometerCountDown(true)
                    .addAction(Notification.Action.Builder(null, "+30s", broadcast(ctx, ACTION_EXTEND, 1)).build())
                    .addAction(Notification.Action.Builder(null, "Skip", broadcast(ctx, ACTION_SKIP, 2)).build())
            }
            else -> {
                b.setContentText("$done/${counted.size} done" + (nextName?.let { " · up next: $it" } ?: ""))
                    .setWhen(now - w.elapsedMs(now))
                if (next != null) b.addAction(Notification.Action.Builder(null, "Log set", broadcast(ctx, ACTION_LOG, 4)).build())
            }
        }
        runCatching { nm.notify(ID_WORKOUT, b.build()) } // throws only without POST_NOTIFICATIONS on some OEMs

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
                    RestNotifier.ACTION_EXTEND -> store.whenReady { extendRest(30) }
                    RestNotifier.ACTION_SKIP -> store.whenReady { skipRest() }
                    RestNotifier.ACTION_LOG -> store.whenReady { logNextSet() }
                }
            } finally { pending.finish() }
        }
    }
}
