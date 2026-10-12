package com.roomflow.demo.domain

import com.roomflow.demo.data.model.RoomEvent

data class MeetingDerivedState(
    val currentEvent: RoomEvent? = null,
    val nextEvent: RoomEvent? = null,
    val isOccupied: Boolean = false,
    val totalToday: Int = 0,
    val remaining: Int = 0,
    val occupiedHours: String = "0 sa"
)

object MeetingStateCalculator {
    fun derive(events: List<RoomEvent>, nowMillis: Long): MeetingDerivedState {
        val orderedEvents = events.sortedBy { it.startMillis }
        val currentEvent = orderedEvents.firstOrNull {
            nowMillis >= it.startMillis && nowMillis < it.endMillis
        }
        val nextEvent = orderedEvents.firstOrNull { it.startMillis > nowMillis }
        val todayEvents = orderedEvents.filter {
            TimeUtils.isSameTurkeyDay(it.startMillis, nowMillis)
        }

        return MeetingDerivedState(
            currentEvent = currentEvent,
            nextEvent = nextEvent,
            isOccupied = currentEvent != null,
            totalToday = todayEvents.size,
            remaining = todayEvents.count { it.endMillis >= nowMillis },
            occupiedHours = TimeUtils.formatOccupiedHours(
                todayEvents.sumOf { it.durationMillis }
            )
        )
    }
}
