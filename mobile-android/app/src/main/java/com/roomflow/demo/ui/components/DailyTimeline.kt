package com.roomflow.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.data.model.RoomEvent
import com.roomflow.demo.domain.TimeUtils
import com.roomflow.demo.ui.theme.AvailableAccentBar
import com.roomflow.demo.ui.theme.AccentRed
import com.roomflow.demo.ui.theme.TextGray
import com.roomflow.demo.ui.theme.TextWhite
import kotlin.math.ceil

@Composable
fun DailyTimeline(
    events: List<RoomEvent>,
    nowMillis: Long,
    maxItems: Int,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val todayEvents = remember(events, nowMillis, maxItems) {
        selectTimelineEvents(events, nowMillis, maxItems)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
    ) {
        Text(
            text = "G\u00dcN\u00dcN AKI\u015eI",
            color = AccentRed,
            fontSize = if (compactMode) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.2.sp
        )

        if (todayEvents.isEmpty()) {
            EmptyTimelineCard(compactMode = compactMode)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 10.dp)
            ) {
                todayEvents.forEach { event ->
                    TimelineItem(
                        event = event,
                        nowMillis = nowMillis,
                        compactMode = compactMode
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TimelineItem(
    event: RoomEvent,
    nowMillis: Long,
    compactMode: Boolean
) {
    val state = timelineState(event, nowMillis)
    val backgroundColor = when (state) {
        TimelineState.Past -> Color(0xFF141922)
        TimelineState.Active -> Color(0xFF311A1E)
        TimelineState.Upcoming -> Color(0xFF151A24)
    }
    val accentColor = when (state) {
        TimelineState.Past -> Color.Transparent
        TimelineState.Active -> AccentRed
        TimelineState.Upcoming -> AvailableAccentBar.copy(alpha = 0.12f)
    }
    val contentAlpha = if (state == TimelineState.Past) 0.45f else 1f
    val padding = if (compactMode) 12.dp else 16.dp
    val timeWidth = if (compactMode) 56.dp else 74.dp
    val lineHeight = if (compactMode) 34.dp else 52.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha),
        color = backgroundColor,
        shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp)
    ) {
        if (compactMode) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${TimeUtils.formatClockTime(event.startMillis)}–${TimeUtils.formatClockTime(event.endMillis)}",
                        modifier = Modifier.weight(1f), color = TextWhite.copy(alpha = 0.74f), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    TimelineBadge(timelineBadgeText(event, state, nowMillis), state == TimelineState.Active, true)
                }
                Text(event.displaySubject, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(event.organizerName, color = TextGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding, vertical = padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(timeWidth),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = TimeUtils.formatClockTime(event.startMillis),
                    color = TextWhite.copy(alpha = 0.78f),
                    fontSize = if (compactMode) 14.sp else 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = TimeUtils.formatClockTime(event.endMillis),
                    color = TextWhite.copy(alpha = 0.48f),
                    fontSize = if (compactMode) 14.sp else 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(lineHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(if (compactMode) 12.dp else 16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = event.displaySubject,
                    color = TextWhite,
                    fontSize = if (compactMode) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.organizerName,
                    color = TextGray.copy(alpha = 0.95f),
                    fontSize = if (compactMode) 12.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            TimelineBadge(
                text = timelineBadgeText(event, state, nowMillis),
                isActive = state == TimelineState.Active,
                compactMode = compactMode
            )
        }
    }
    }
}

@Composable
private fun TimelineBadge(
    text: String,
    isActive: Boolean,
    compactMode: Boolean
) {
    Surface(
        color = if (isActive) AccentRed else TextWhite.copy(alpha = 0.06f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (compactMode) 8.dp else 10.dp,
                vertical = if (compactMode) 6.dp else 7.dp
            ),
            color = if (isActive) TextWhite else TextGray,
            fontSize = if (compactMode) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyTimelineCard(compactMode: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF141922),
        shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp)
    ) {
        Text(
            text = "Bug\u00fcn i\u00e7in g\u00f6r\u00fcn\u00fcr toplant\u0131 yok.",
            modifier = Modifier.padding(
                horizontal = if (compactMode) 14.dp else 18.dp,
                vertical = if (compactMode) 14.dp else 18.dp
            ),
            color = TextGray,
            fontSize = if (compactMode) 13.sp else 15.sp
        )
    }
}

private fun selectTimelineEvents(
    events: List<RoomEvent>,
    nowMillis: Long,
    maxItems: Int
): List<RoomEvent> {
    val todayEvents = events
        .filter { TimeUtils.isSameTurkeyDay(it.startMillis, nowMillis) }
        .sortedBy { it.startMillis }

    if (todayEvents.size <= maxItems) {
        return todayEvents
    }

    val focusIndex = todayEvents.indexOfFirst { nowMillis <= it.endMillis }
        .takeIf { it >= 0 }
        ?: todayEvents.lastIndex
    var startIndex = (focusIndex - 2).coerceAtLeast(0)
    val endIndex = (startIndex + maxItems).coerceAtMost(todayEvents.size)
    startIndex = (endIndex - maxItems).coerceAtLeast(0)

    return todayEvents.subList(startIndex, endIndex)
}

private fun timelineState(
    event: RoomEvent,
    nowMillis: Long
): TimelineState {
    return when {
        nowMillis > event.endMillis -> TimelineState.Past
        nowMillis >= event.startMillis -> TimelineState.Active
        else -> TimelineState.Upcoming
    }
}

private fun timelineBadgeText(
    event: RoomEvent,
    state: TimelineState,
    nowMillis: Long
): String {
    return when (state) {
        TimelineState.Past -> "Bitti"
        TimelineState.Active -> "\u015eimdi"
        TimelineState.Upcoming -> {
            val minutesUntilStart = ceil((event.startMillis - nowMillis) / 60_000.0)
                .toInt()
                .coerceAtLeast(1)

            if (minutesUntilStart < 60) {
                "${minutesUntilStart} dk"
            } else {
                val hoursUntilStart = ceil(minutesUntilStart / 60.0).toInt()
                "${hoursUntilStart} sa"
            }
        }
    }
}

private enum class TimelineState {
    Past,
    Active,
    Upcoming
}
