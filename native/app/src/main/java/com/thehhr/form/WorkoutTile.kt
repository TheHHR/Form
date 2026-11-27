package com.thehhr.form

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.thehhr.form.data.WorkoutLogic

/**
 * Quick Settings tile: active while a workout runs (subtitle = rest left or current exercise) and opens it;
 * otherwise inactive with today's routine and starts it. Works without the app open: the store loads the vault.
 */
class WorkoutTile : TileService() {
    private val store get() = FormStore.get(this)

    override fun onStartListening() = render()

    override fun onClick() {
        val w = store.workout
        val action = when {
            w != null -> MainActivity.ACTION_OPEN_WORKOUT
            store.status == Status.Ready && store.todaysRoutine() != null -> MainActivity.ACTION_START_TODAY
            else -> MainActivity.ACTION_OPEN_WORKOUT
        }
        val intent = LinkActivity.intent(this, action)
        val open = { if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun render() {
        val tile = qsTile ?: return
        val w = store.workout
        tile.icon = Icon.createWithResource(this, R.drawable.ic_start)
        if (w != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = w.routineName
            val now = System.currentTimeMillis()
            val rest = w.rest?.takeIf { it.pausedRemaining == null && it.endsAt > now && !w.paused }
            val routine = store.data.routines.firstOrNull { it.id == w.routineId }
            tile.subtitle = when {
                w.paused -> "Paused"
                // The tile only re-renders on state changes, so show when rest ends rather than a stale countdown.
                rest != null -> "${if (rest.type == "set") "Set" else "Rest"} until ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(rest.endsAt))}"
                else -> routine?.let { WorkoutLogic.nextTarget(w, it) }?.let { com.thehhr.form.ui.exTitle(store.exercise(it.exerciseId)) } ?: "Tap to finish"
            }
            tile.stateDescription = "Workout in progress"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_workout)
            val today = if (store.status == Status.Ready) store.todaysRoutine() else null
            tile.subtitle = today?.let { "Start ${it.name}" } ?: "Open"
            tile.stateDescription = null
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to re-render the tile when workout state changes (no-op if it isn't added). */
        fun refresh(ctx: Context) = runCatching { requestListeningState(ctx, ComponentName(ctx, WorkoutTile::class.java)) }
    }
}
