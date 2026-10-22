package com.streamdesk.app

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvKeysTest {
    @Test
    fun upAndDownChangeChannel() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
            assertEquals(TvAction.ChannelUp, TvKeys.map(key))
        for (key in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            assertEquals(TvAction.ChannelDown, TvKeys.map(key))
    }

    @Test
    fun okOpensTheListAndMenuOpensOptions() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER))
            assertEquals(TvAction.Select, TvKeys.map(key))
        assertEquals(TvAction.Menu, TvKeys.map(KeyEvent.KEYCODE_MENU))
        assertEquals(TvAction.ShowInfo, TvKeys.map(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvAction.ShowInfo, TvKeys.map(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun backVolumeAndHomeAreLeftToAndroid() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_A))
            assertNull(TvKeys.map(key))
    }
}
