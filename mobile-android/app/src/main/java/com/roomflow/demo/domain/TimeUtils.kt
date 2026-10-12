package com.roomflow.demo.domain

import com.roomflow.demo.data.model.RoomEvent
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

object TimeUtils {
    private val turkeyTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val backendDateRegex =
        Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})?$""")

    private val monthNames = arrayOf(
        "Ocak",
        "Şubat",
        "Mart",
        "Nisan",
        "Mayıs",
        "Haziran",
        "Temmuz",
        "Ağustos",
        "Eylül",
        "Ekim",
        "Kasım",
        "Aralık"
    )

    private val dayNames = arrayOf(
        "Pazar",
        "Pazartesi",
        "Salı",
        "Çarşamba",
        "Perşembe",
        "Cuma",
        "Cumartesi"
    )

    fun parseBackendDateTime(dateTime: String?): Long? {
        val rawValue = dateTime?.trim().orEmpty()
        if (rawValue.isBlank()) return null

        val match = backendDateRegex.matchEntire(rawValue) ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val hour = match.groupValues[4].toInt()
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].ifBlank { "0" }.toInt()
        val millisecond = parseMilliseconds(match.groupValues[7])
        val offsetToken = match.groupValues[8].ifBlank { null }

        return if (offsetToken == null) {
            buildMillis(
                timeZone = turkeyTimeZone,
                year = year,
                month = month,
                day = day,
                hour = hour,
                minute = minute,
                second = second,
                millisecond = millisecond
            )
        } else {
            buildMillis(
                timeZone = utcTimeZone,
                year = year,
                month = month,
                day = day,
                hour = hour,
                minute = minute,
                second = second,
                millisecond = millisecond
            ) - parseOffsetMinutes(offsetToken) * 60_000L
        }
    }

    fun adjustedNow(currentTimeMillis: Long, serverTimeOffset: Long): Long {
        return currentTimeMillis + serverTimeOffset
    }

    fun formatClockTime(millis: Long): String {
        val calendar = calendarFor(millis)
        return "${calendar.get(Calendar.HOUR_OF_DAY).twoDigits()}:${calendar.get(Calendar.MINUTE).twoDigits()}"
    }

    fun formatDisplayDate(millis: Long): String {
        val calendar = calendarFor(millis)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = monthNames[calendar.get(Calendar.MONTH)]
        val year = calendar.get(Calendar.YEAR)
        val dayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        return "$day $month $year · $dayName"
    }

    fun formatDayName(millis: Long): String {
        val calendar = calendarFor(millis)
        return dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun formatEventRange(startMillis: Long, endMillis: Long): String {
        return "${formatClockTime(startMillis)} – ${formatClockTime(endMillis)}"
    }

    fun formatDuration(durationMillis: Long): String {
        val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours} sa ${minutes} dk"
            hours > 0 -> "${hours} sa"
            else -> "${minutes} dk"
        }
    }

    fun formatOccupiedHours(durationMillis: Long): String {
        val totalMinutes = (durationMillis / 60_000.0).coerceAtLeast(0.0)
        if (totalMinutes <= 0.0) return "0 sa"
        if (totalMinutes < 60.0) return "${totalMinutes.roundToInt()} dk"

        val roundedHours = ((totalMinutes / 60.0) * 10.0).roundToInt() / 10.0
        return if (roundedHours % 1.0 == 0.0) {
            "${roundedHours.toInt()} sa"
        } else {
            "${roundedHours.toString().trimEnd('0').trimEnd('.')} sa"
        }
    }

    fun calculateWorkdayOccupancyPercentage(
        events: List<RoomEvent>,
        referenceMillis: Long,
        workdayStartHour: Int = 8,
        workdayEndHour: Int = 17
    ): Int {
        val workdayStartMillis = workdayBoundaryMillis(referenceMillis, workdayStartHour)
        val workdayEndMillis = workdayBoundaryMillis(referenceMillis, workdayEndHour)
        val workdayDurationMillis = (workdayEndMillis - workdayStartMillis).coerceAtLeast(1L)

        val occupiedMillis = events
            .filter { isSameTurkeyDay(it.startMillis, referenceMillis) || isSameTurkeyDay(it.endMillis, referenceMillis) }
            .sumOf { event ->
                overlapDurationMillis(
                    startMillis = event.startMillis,
                    endMillis = event.endMillis,
                    rangeStartMillis = workdayStartMillis,
                    rangeEndMillis = workdayEndMillis
                )
            }

        return ((occupiedMillis.toDouble() / workdayDurationMillis.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun formatNextEventTime(eventMillis: Long, nowMillis: Long): String {
        return if (isSameTurkeyDay(eventMillis, nowMillis)) {
            formatClockTime(eventMillis)
        } else {
            val calendar = calendarFor(eventMillis)
            "${calendar.get(Calendar.DAY_OF_MONTH)} ${monthNames[calendar.get(Calendar.MONTH)]} ${formatClockTime(eventMillis)}"
        }
    }

    fun formatLastUpdated(lastUpdatedMillis: Long?): String {
        return lastUpdatedMillis?.let(::formatClockTime) ?: "--:--"
    }

    fun isSameTurkeyDay(firstMillis: Long, secondMillis: Long): Boolean {
        val first = calendarFor(firstMillis)
        val second = calendarFor(secondMillis)
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun workdayBoundaryMillis(referenceMillis: Long, hourOfDay: Int): Long {
        val calendar = calendarFor(referenceMillis)
        return buildMillis(
            timeZone = turkeyTimeZone,
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            hour = hourOfDay,
            minute = 0,
            second = 0,
            millisecond = 0
        )
    }

    private fun overlapDurationMillis(
        startMillis: Long,
        endMillis: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): Long {
        val overlapStart = maxOf(startMillis, rangeStartMillis)
        val overlapEnd = minOf(endMillis, rangeEndMillis)
        return (overlapEnd - overlapStart).coerceAtLeast(0L)
    }

    private fun buildMillis(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, millisecond)
        }.timeInMillis
    }

    private fun calendarFor(millis: Long): Calendar {
        return Calendar.getInstance(turkeyTimeZone).apply {
            timeInMillis = millis
        }
    }

    private fun parseMilliseconds(rawMillis: String): Int {
        if (rawMillis.isBlank()) return 0

        return when (rawMillis.length) {
            1 -> rawMillis.toInt() * 100
            2 -> rawMillis.toInt() * 10
            else -> rawMillis.take(3).toInt()
        }
    }

    private fun parseOffsetMinutes(offsetToken: String): Int {
        if (offsetToken == "Z") return 0

        val sign = if (offsetToken.startsWith("-")) -1 else 1
        val normalized = offsetToken.substring(1).replace(":", "")
        val hours = normalized.substring(0, 2).toInt()
        val minutes = normalized.substring(2, 4).toInt()
        return sign * (hours * 60 + minutes)
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')
}
