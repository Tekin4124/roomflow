package com.roomflow.demo.data.model

import com.roomflow.demo.domain.TimeUtils
import com.google.gson.annotations.SerializedName

data class RoomEventsResponse(
    @SerializedName("events")
    val events: List<RoomEventPayload> = emptyList(),
    @SerializedName("lastUpdated")
    val lastUpdated: String? = null,
    @SerializedName("roomEmail")
    val roomEmail: String? = null,
    @SerializedName("roomName")
    val roomName: String? = null
)

data class RoomEventPayload(
    @SerializedName("subject")
    val subject: String? = null,
    @SerializedName("showAs")
    val showAs: String? = null,
    @SerializedName("start")
    val start: EventDateTimePayload? = null,
    @SerializedName("end")
    val end: EventDateTimePayload? = null,
    @SerializedName("organizer")
    val organizer: OrganizerPayload? = null
) {
    fun toRoomEvent(): RoomEvent? {
        val startMillis = TimeUtils.parseBackendDateTime(start?.dateTime) ?: return null
        val endMillis = TimeUtils.parseBackendDateTime(end?.dateTime) ?: return null
        if (endMillis < startMillis) return null

        val organizerEmail = organizer?.emailAddress?.address?.trim().orEmpty()
        val organizerName = organizer?.emailAddress?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: organizerEmail.ifBlank { "Bilinmiyor" }

        return RoomEvent(
            subject = subject?.trim().orEmpty().ifBlank { "Toplantı" },
            showAs = showAs?.trim().orEmpty().ifBlank { "busy" },
            startMillis = startMillis,
            endMillis = endMillis,
            organizerName = organizerName,
            organizerEmail = organizerEmail
        )
    }
}

data class EventDateTimePayload(
    @SerializedName("dateTime")
    val dateTime: String? = null,
    @SerializedName("timeZone")
    val timeZone: String? = null
)

data class OrganizerPayload(
    @SerializedName("emailAddress")
    val emailAddress: EmailAddressPayload? = null
)

data class EmailAddressPayload(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("address")
    val address: String? = null
)
