package com.roomflow.demo.data.model

data class RoomEvent(
    val subject: String,
    val showAs: String,
    val startMillis: Long,
    val endMillis: Long,
    val organizerName: String,
    val organizerEmail: String
) {
    val durationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)

    val displaySubject: String
        get() = subject.trim().ifBlank { DEFAULT_SUBJECT }

    companion object {
        private const val DEFAULT_SUBJECT = "Toplantı"
    }
}
