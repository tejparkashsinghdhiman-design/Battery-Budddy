package com.example.batterybuddy

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: an instant on/off switch for the buddy. */
class BuddyTileService : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        val p = Buddy.prefs(this)
        p.edit().putBoolean("show", !p.getBoolean("show", true)).apply()
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (Buddy.prefs(this).getBoolean("show", true)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
