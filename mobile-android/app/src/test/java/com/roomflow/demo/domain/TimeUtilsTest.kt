package com.roomflow.demo.domain

import com.roomflow.demo.data.model.RoomEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeUtilsTest {
    @Test
    fun parseBackendDateTime_acceptsGraphDateWithSevenFractionDigits() {
        val millis = TimeUtils.parseBackendDateTime("2026-04-13T09:00:00.0000000")

        assertNotNull(millis)

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul")).apply {
            timeInMillis = millis!!
        }

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, calendar.get(Calendar.MONTH))
        assertEquals(13, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun calculateWorkdayOccupancyPercentage_clips_events_to_working_hours() {
        val referenceMillis = turkeyMillis(hour = 12, minute = 0)
        val events = listOf(
            roomEvent(startHour = 7, startMinute = 30, endHour = 8, endMinute = 30),
            roomEvent(startHour = 12, startMinute = 0, endHour = 13, endMinute = 0),
            roomEvent(startHour = 16, startMinute = 30, endHour = 17, endMinute = 30)
        )

        val occupancyPercentage = TimeUtils.calculateWorkdayOccupancyPercentage(
            events = events,
            referenceMillis = referenceMillis
        )

        assertEquals(22, occupancyPercentage)
    }

    private fun roomEvent(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): RoomEvent {
        return RoomEvent(
            subject = "Test",
            showAs = "busy",
            startMillis = turkeyMillis(startHour, startMinute),
            endMillis = turkeyMillis(endHour, endMinute),
            organizerName = "Test",
            organizerEmail = "team@example.invalid"
        )
    }

    private fun turkeyMillis(hour: Int, minute: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul")).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 13)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
    }
}
