package com.streamdesk.app

import android.view.KeyEvent

/**
 * The remote's keys for the TV home screen. The channel grid is driven from here, not by Android's focus search: the
 * home screen keeps its own "selected channel", moves it with the arrow keys and plays it with OK. (Focus search left
 * some boxes stuck on the tab bar, where OK did nothing.) Dialogs and menus are separate windows, so they get their
 * keys first and never pass through here.
 */
object HomeKeys {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
}

/** Where the selected channel goes next. [cols] columns, [n] channels; null means the top row, where ▲ leaves for the tabs. */
object GridMoves {
    fun down(cur: Int, cols: Int, n: Int): Int =
        if (cur / cols < (n - 1) / cols) minOf(cur + cols, n - 1) else cur

    fun up(cur: Int, cols: Int): Int? = if (cur < cols) null else cur - cols

    fun left(cur: Int, cols: Int): Int = if (cur % cols > 0) cur - 1 else cur

    fun right(cur: Int, cols: Int, n: Int): Int = if (cur % cols < cols - 1 && cur + 1 < n) cur + 1 else cur
}
