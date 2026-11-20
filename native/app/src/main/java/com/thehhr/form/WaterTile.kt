package com.thehhr.form

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.today

/** Quick Settings tile: one tap logs 250 ml of water for today. */
class WaterTile : TileService() {
    private val store get() = FormStore.get(this)

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (isLocked) unlockAndRun { add() } else add()
    }

    private fun add() {
        store.launch {
            val ml = store.whenReady { addWater(today(), 250.0); data.history[today()]?.water }
            // Toast, not the in-app snackbar: the app may not be open.
            Toast.makeText(this@WaterTile, if (ml != null) "+250 ml · ${jsRound(ml)} ml today" else "Open Form to pick your vault folder first", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_water)
        tile.label = getString(R.string.tile_water)
        val ml = if (store.status == Status.Ready) store.data.history[today()]?.water ?: 0.0 else null
        tile.subtitle = ml?.let { "${jsRound(it)} ml today" } ?: "+250 ml"
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
