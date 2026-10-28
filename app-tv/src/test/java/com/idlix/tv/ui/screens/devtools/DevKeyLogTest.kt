package com.idlix.tv.ui.screens.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for the DevTools key log (owned by the DevTools slice).
 *
 * [DevKeyLog] is pure JVM (StateFlow + synchronized, no Android), so these
 * run as plain unit tests: capacity cap, newest retention, focus-before/after
 * capture, and clear.
 */
class DevKeyLogTest {

    @Before
    fun reset() {
        DevKeyLog.clear()
        DevKeyLog.noteFocus(DevKeyLog.NO_FOCUS)
    }

    @Test
    fun `record appends with focus-before label`() {
        DevKeyLog.noteFocus("action:run-update-check")
        DevKeyLog.record("DOWN")

        val records = DevKeyLog.records.value
        assertEquals(1, records.size)
        assertEquals("DOWN", records[0].name)
        assertEquals("action:run-update-check", records[0].focusBefore)
        assertNull(records[0].focusAfter)
    }

    @Test
    fun `noteFocus patches focus-after on latest pending record`() {
        DevKeyLog.noteFocus("action:run-update-check")
        DevKeyLog.record("DOWN")
        DevKeyLog.noteFocus("action:force-resolver-refresh")

        val records = DevKeyLog.records.value
        assertEquals(1, records.size)
        assertEquals("action:run-update-check", records[0].focusBefore)
        assertEquals("action:force-resolver-refresh", records[0].focusAfter)
    }

    @Test
    fun `buffer caps at 20 keeping newest`() {
        repeat(25) { DevKeyLog.record("DOWN") }

        val records = DevKeyLog.records.value
        assertEquals(DevKeyLog.CAPACITY, records.size)
        // Last 20 retained in order: seqs strictly increasing by 1.
        assertEquals(19L, records.last().seq - records.first().seq)
        assertTrue(records.zipWithNext().all { (a, b) -> b.seq == a.seq + 1 })
    }

    @Test
    fun `clear empties the log`() {
        DevKeyLog.record("UP")
        DevKeyLog.clear()
        assertTrue(DevKeyLog.records.value.isEmpty())
    }

    @Test
    fun `key names cover core remote keys`() {
        val K = androidKeyCodes()
        assertEquals("UP", devKeyName(K.dpadUp))
        assertEquals("DOWN", devKeyName(K.dpadDown))
        assertEquals("LEFT", devKeyName(K.dpadLeft))
        assertEquals("RIGHT", devKeyName(K.dpadRight))
        assertEquals("BACK", devKeyName(K.back))
        assertEquals("MENU", devKeyName(K.menu))
    }

    // android.view.KeyEvent constants without loading the framework class
    // (unitTests return default values; KeyEvent.keyCodeToString is only hit
    // for unknown codes, which these known codes never reach).
    private class androidKeyCodes {
        val dpadUp = 19
        val dpadDown = 20
        val dpadLeft = 21
        val dpadRight = 22
        val back = 4
        val menu = 82
    }
}
