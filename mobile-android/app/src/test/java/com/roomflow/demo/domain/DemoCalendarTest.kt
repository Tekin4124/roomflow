package com.roomflow.demo.domain

import org.junit.Assert.*
import org.junit.Test

class DemoCalendarTest {
    private val anchor = 1_800_000_000_000L
    @Test fun busyHasCurrentAndFutureMeeting() {
        val state = MeetingStateCalculator.derive(DemoCalendar.events("busy", anchor), anchor)
        assertTrue(state.isOccupied)
        assertEquals("Tasarım toplantısı", state.currentEvent?.subject)
        assertEquals("Ürün değerlendirmesi", state.nextEvent?.subject)
    }
    @Test fun availableHasFutureMeetingAndEmptyHasNone() {
        val available = MeetingStateCalculator.derive(DemoCalendar.events("available", anchor), anchor)
        assertFalse(available.isOccupied)
        assertNotNull(available.nextEvent)
        assertTrue(DemoCalendar.events("empty", anchor).isEmpty())
    }
    @Test fun meetingIsNoLongerOccupiedAtItsExactEnd() {
        val meetings = DemoCalendar.events("busy", anchor)
        assertFalse(MeetingStateCalculator.derive(meetings, anchor + 25 * 60_000L).isOccupied)
    }
}
