package com.roomflow.demo.domain

import com.roomflow.demo.data.model.RoomEvent

object DemoCalendar {
    fun events(scenario: String, anchor: Long): List<RoomEvent> {
        require(scenario in listOf("busy", "available", "empty"))
        if (scenario == "empty") return emptyList()
        fun event(subject: String, start: Int, end: Int) = RoomEvent(subject, "busy", anchor + start * 60_000L, anchor + end * 60_000L, "Toplantı Ekibi", "team@example.invalid")
        val result = mutableListOf(event("Haftalık planlama", -90, -60), event("Ürün değerlendirmesi", 45, 90))
        if (scenario == "busy") result.add(event("Tasarım toplantısı", -10, 25))
        return result.sortedBy { it.startMillis }
    }
}
