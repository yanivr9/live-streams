package com.streamdesk.app

import android.content.Context

/** "Open on the last channel": on a TV, starting the app goes straight to what you watched last. */
object Resume {
    private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("open_last", true)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("open_last", on).apply()

    /** The channels to play and where to start: the last one watched, inside the Sport list if it belongs there. */
    fun queue(last: Channel, sport: List<Channel>): Pair<List<Channel>, Int> {
        val i = sport.indexOfFirst { it.url == last.url }
        return if (i >= 0) sport to i else listOf(last) to 0
    }
}
