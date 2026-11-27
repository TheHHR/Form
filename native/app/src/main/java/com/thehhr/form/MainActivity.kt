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
        // Handle the launch action unless this exact intent was already handled before a rotation or process restart
        // (a restored activity also gets new shortcut/notification intents, which must not be dropped).
        val key = intent?.toUri(0)
        val fromRecents = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!fromRecents && savedInstanceState?.getString(HANDLED) != key) handle(intent)
        handledKey = key
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); handledKey = intent.toUri(0) // a later rotation must not replay it
        handle(intent)
    }

    /** Shortcut, widget, tile, notification and reminder actions; vault work waits until it is loaded. */
    private fun handle(intent: Intent?) = handleAction(store, intent)

    private fun publishShortcuts() {
        fun shortcut(id: String, short: String, long: String, icon: Int, action: String, rank: Int) = ShortcutInfo.Builder(this, id)
            .setShortLabel(short).setLongLabel(long).setRank(rank).setIcon(Icon.createWithResource(this, icon))
            .setIntent(LinkActivity.intent(this, action)).build()
        // Dynamic, not static XML: static shortcuts hard-code the package, and prod/beta ids differ.
        runCatching {
            getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(
                shortcut("start", "Start workout", "Start today's workout", R.drawable.sc_start, ACTION_START_TODAY, 0),
                shortcut("food", "Log food", "Log a meal", R.drawable.sc_food, ACTION_LOG_FOOD, 1),
                shortcut("routines", "Routines", "Open routines", R.drawable.sc_routines, ACTION_OPEN_ROUTINES, 2),
                shortcut("progress", "Progress", "View progress", R.drawable.sc_progress, ACTION_OPEN_PROGRESS, 3),
                shortcut("water", "Log water", "Log 250 ml water", R.drawable.ic_water, ACTION_LOG_WATER, 4),
            )
        }
    }

    private var handledKey: String? = null
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString(HANDLED, handledKey) }

    override fun onResume() { super.onResume(); AppVisibility.visible = true; store.onResume() }
    override fun onPause() { AppVisibility.visible = false; store.onPause(); super.onPause() }

    companion object {
        /** Shortcut, widget, tile, notification and reminder actions; vault work waits until it is loaded. */
        fun handleAction(store: FormStore, intent: Intent?) {
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
        private const val HANDLED = "handled_intent"
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
