package com.thehhr.form

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.today
import com.thehhr.form.ui.FormApp
import com.thehhr.form.ui.Media

class MainActivity : ComponentActivity() {
    private val store by lazy { FormStore.get(this) }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Media.hasVideos = assets.list("videos")?.isNotEmpty() == true

        enableEdgeToEdge() // FormApp re-applies the bar style when the in-app theme differs from the system one
        RestNotifier.channels(this)
        publishShortcuts()
        // Generated widget-picker preview on Android 15+ (rate-limited by the system, so once per launch is fine).
        if (Build.VERSION.SDK_INT >= 35) store.launch { runCatching { androidx.glance.appwidget.GlanceAppWidgetManager(this@MainActivity).setWidgetPreviews(FormWidget::class) } }
        // Rest alerts and reminders need this on Android 13+; ask once.
        if (Build.VERSION.SDK_INT >= 33 && !store.device.notificationsAsked) {
            store.device.notificationsAsked = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { FormApp(store) }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Shortcut, widget, tile, notification and reminder actions; vault work waits until it is loaded. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_TODAY -> {
                store.pendingDestination = "Workout"
                store.launch { if (store.whenReady { startToday() } == false) store.message = "Nothing scheduled today. Pick a routine." }
            }
            ACTION_OPEN_WORKOUT -> store.pendingDestination = "Workout"
            ACTION_START_ROUTINE -> {
                store.pendingDestination = "Workout"
                val id = intent.getStringExtra(EXTRA_ROUTINE)
                store.launch { store.whenReady { data.routines.firstOrNull { it.id == id }?.let(::startWorkout) } }
            }
            ACTION_LOG_FOOD -> store.pendingDestination = "Fuel:log"
            ACTION_OPEN_ROUTINES -> store.pendingDestination = "Plans"
            ACTION_OPEN_PROGRESS -> store.pendingDestination = "Progress"
            ACTION_LOG_WATER -> store.launch {
                store.whenReady { addWater(today(), 250.0); message = "+250 ml · ${jsRound(data.history[today()]?.water ?: 0.0)} ml today" }
            }
        }
    }

    private fun publishShortcuts() {
        fun shortcut(id: String, short: String, long: String, icon: Int, action: String, rank: Int) = ShortcutInfo.Builder(this, id)
            .setShortLabel(short).setLongLabel(long).setRank(rank).setIcon(Icon.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action)).build()
        // Dynamic, not static XML: static shortcuts hard-code the package, and prod/beta ids differ.
        runCatching {
            getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(
                shortcut("start", "Start workout", "Start today's workout", R.drawable.ic_start, ACTION_START_TODAY, 0),
                shortcut("food", "Log food", "Log a meal", R.drawable.ic_food, ACTION_LOG_FOOD, 1),
                shortcut("routines", "Routines", "Open routines", R.drawable.ic_routines, ACTION_OPEN_ROUTINES, 2),
                shortcut("progress", "Progress", "View progress", R.drawable.ic_progress, ACTION_OPEN_PROGRESS, 3),
                shortcut("water", "Log water", "Log 250 ml water", R.drawable.ic_water, ACTION_LOG_WATER, 4),
            )
        }
    }

    override fun onResume() { super.onResume(); AppVisibility.visible = true; store.onResume() }
    override fun onPause() { AppVisibility.visible = false; store.onPause(); super.onPause() }

    companion object {
        const val ACTION_START_TODAY = "com.thehhr.form.START_TODAY"
        const val ACTION_LOG_WATER = "com.thehhr.form.LOG_WATER"
        const val ACTION_OPEN_WORKOUT = "com.thehhr.form.OPEN_WORKOUT"
        const val ACTION_LOG_FOOD = "com.thehhr.form.LOG_FOOD"
        const val ACTION_OPEN_ROUTINES = "com.thehhr.form.OPEN_ROUTINES"
        const val ACTION_OPEN_PROGRESS = "com.thehhr.form.OPEN_PROGRESS"
        const val ACTION_START_ROUTINE = "com.thehhr.form.START_ROUTINE"
        const val EXTRA_ROUTINE = "routineId"
    }
}
