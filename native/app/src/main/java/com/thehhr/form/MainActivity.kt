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
        // The app is dark-only: light status/nav bar icons regardless of the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        RestNotifier.channels(this)
        publishShortcuts()
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

    /** Shortcut, widget and reminder actions; queued until the vault is loaded. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_TODAY -> store.launch {
                if (store.whenReady { startToday() } == false) store.message = "Nothing scheduled today"
            }
            ACTION_LOG_WATER -> store.launch {
                store.whenReady { addWater(today(), 250.0); message = "+250 ml · ${jsRound(data.history[today()]?.water ?: 0.0)} ml today" }
            }
        }
    }

    private fun publishShortcuts() {
        fun shortcut(id: String, label: String, icon: Int, action: String) = ShortcutInfo.Builder(this, id)
            .setShortLabel(label).setIcon(Icon.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action)).build()
        // Dynamic, not static XML: static shortcuts hard-code the package, and prod/beta ids differ.
        runCatching {
            getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(
                shortcut("start", "Start today's workout", R.drawable.ic_start, ACTION_START_TODAY),
                shortcut("water", "Log 250 ml water", R.drawable.ic_water, ACTION_LOG_WATER),
            )
        }
    }

    override fun onResume() { super.onResume(); AppVisibility.visible = true; store.onResume() }
    override fun onPause() { AppVisibility.visible = false; store.onPause(); super.onPause() }

    companion object {
        const val ACTION_START_TODAY = "com.thehhr.form.START_TODAY"
        const val ACTION_LOG_WATER = "com.thehhr.form.LOG_WATER"
    }
}
