package com.streamdesk.app

import android.content.Context

/** "Open on the last channel": on a TV, starting the app can go straight to what you watched last (off by default). */
object Resume {
    private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)

    // Off unless turned on in ⋮: the app opens on its channels. (A new key, so TVs that had it on start with it off.)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("open_last_channel", false)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("open_last_channel", on).apply()

    /** The channels to play and where to start: the last one watched, inside the Sport list if it belongs there. */
    fun queue(last: Channel, lists: List<List<Channel>>): Pair<List<Channel>, Int> {
        for (list in lists) {
            val i = list.indexOfFirst { it.url == last.url }
            if (i >= 0) return list to i
        }
        return listOf(last) to 0
    }
}
