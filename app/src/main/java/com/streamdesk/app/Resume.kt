package com.streamdesk.app

import android.content.Context

/** "Open on the last channel": on a TV, starting the app goes straight to what you watched last. */
object Resume {
    private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("open_last", true)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("open_last", on).apply()

    /** The channels to play and where to start: the last one watched, inside the Sport list if it belongs there. */
    fun queue(last: Channel, lists: List<List<Channel>>): Pair<List<Channel>, Int> {
        for (list in lists) {
            val i = list.indexOfFirst { it.url == last.url }
            if (i >= 0) return list to i
        }
        return listOf(last) to 0
    }
}
