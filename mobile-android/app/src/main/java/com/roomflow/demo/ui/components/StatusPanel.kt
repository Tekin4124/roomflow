package com.roomflow.demo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.data.model.RoomEvent
import com.roomflow.demo.domain.TimeUtils
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.TextWhite
import java.util.Calendar
import java.util.TimeZone

private val statusTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
private val statusMonthNames = arrayOf(
    "Ocak",
    "\u015eubat",
    "Mart",
    "Nisan",
    "May\u0131s",
    "Haziran",
    "Temmuz",
    "A\u011fustos",
    "Eyl\u00fcl",
    "Ekim",
    "Kas\u0131m",
    "Aral\u0131k"
)
private val statusPanelLineColor = Color.Black.copy(alpha = 0.32f)

@Composable
fun StatusPanel(
    isOccupied: Boolean,
    currentEvent: RoomEvent?,
    nextEvent: RoomEvent?,
    nowMillis: Long,
    metrics: List<StatusMetric>,
    heroTextSize: TextUnit,
    organizerTextSize: TextUnit,
    compactMode: Boolean = false,
    showMetrics: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (isOccupied && currentEvent != null) {
        OccupiedPanel(
            currentEvent = currentEvent,
            nextEvent = nextEvent,
            nowMillis = nowMillis,
            metrics = metrics,
            heroTextSize = heroTextSize,
            organizerTextSize = organizerTextSize,
            compactMode = compactMode,
            showMetrics = showMetrics,
            modifier = modifier
        )
    } else {
        AvailablePanel(
            nextEvent = nextEvent,
            nowMillis = nowMillis,
            metrics = metrics,
            heroTextSize = heroTextSize,
            organizerTextSize = organizerTextSize,
            compactMode = compactMode,
            showMetrics = showMetrics,
            modifier = modifier
        )
    }
}

data class StatusMetric(
    val label: String,
    val value: String
)

@Composable
private fun OccupiedPanel(
    currentEvent: RoomEvent,
    nextEvent: RoomEvent?,
    nowMillis: Long,
    metrics: List<StatusMetric>,
    heroTextSize: TextUnit,
    organizerTextSize: TextUnit,
    compactMode: Boolean,
    showMetrics: Boolean,
    modifier: Modifier = Modifier
) {
    val padding = if (compactMode) 12.dp else 36.dp
    val heroSubtitleSize = if (compactMode) 14.sp else 16.sp
    val cardGap = if (compactMode) 10.dp else 18.dp
    val cardTopOffset = if (compactMode) 0.dp else 34.dp
    val occupiedHeroSize = heroTextSize
    val occupiedSubtitleSize = (heroSubtitleSize.value * 1.30f).sp
    val summaryRowHeight = if (compactMode) 158.dp else 258.dp

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Hero label — naturally sized, no centering, no weight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = padding, end = padding, top = padding, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ME\u015eGUL",
                color = TextWhite,
                fontSize = occupiedHeroSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-5).sp,
                textAlign = TextAlign.Center
            )

        }

        // Cards expand to fill all remaining space between hero and metrics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = summaryRowHeight)
                .padding(start = padding, end = padding, top = cardGap + cardTopOffset),
            horizontalArrangement = Arrangement.spacedBy(cardGap)
        ) {
            CurrentMeetingCard(
                event = currentEvent,
                organizerTextSize = organizerTextSize,
                compactMode = compactMode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            if (nextEvent != null) {
                NextMeetingSummaryCard(
                    nextEvent = nextEvent,
                    nowMillis = nowMillis,
                    organizerTextSize = organizerTextSize,
                    compactMode = compactMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            } else {
                NeutralSummaryCard(
                    title = "SONRAKİ TOPLANTI",
                    headline = "Başka toplantı yok",
                    supporting = "",
                    compactMode = compactMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showMetrics) {
            HorizontalDivider(color = statusPanelLineColor)
            StatusMetricsStrip(
                metrics = metrics,
                compactMode = compactMode,
                modifier = Modifier.padding(horizontal = padding, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun AvailablePanel(
    nextEvent: RoomEvent?,
    nowMillis: Long,
    metrics: List<StatusMetric>,
    heroTextSize: TextUnit,
    organizerTextSize: TextUnit,
    compactMode: Boolean,
    showMetrics: Boolean,
    modifier: Modifier = Modifier
) {
    val padding = if (compactMode) 12.dp else 36.dp
    val heroSubtitleSize = if (compactMode) 14.sp else 16.sp
    val cardGap = if (compactMode) 10.dp else 18.dp
    val cardTopOffset = if (compactMode) 0.dp else 34.dp
    val availableHeroSize = heroTextSize
    val summaryRowHeight = if (compactMode) 158.dp else 258.dp

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Hero label — naturally sized, no centering, no weight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = padding, end = padding, top = padding, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "M\u00dcSA\u0130T",
                color = TextWhite,
                fontSize = availableHeroSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-5).sp,
                textAlign = TextAlign.Center
            )

        }

        // Cards expand to fill all remaining space between hero and metrics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = summaryRowHeight)
                .padding(start = padding, end = padding, top = cardGap + cardTopOffset),
            horizontalArrangement = Arrangement.spacedBy(cardGap)
        ) {
            if (nextEvent != null) {
                OpenWindowCard(
                    nextEvent = nextEvent,
                    nowMillis = nowMillis,
                    compactMode = compactMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                NextMeetingSummaryCard(
                    nextEvent = nextEvent,
                    nowMillis = nowMillis,
                    organizerTextSize = organizerTextSize,
                    compactMode = compactMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            } else {
                NeutralSummaryCard(
                    title = "GÜNÜN TAKVİMİ",
                    headline = if (metrics.firstOrNull()?.value == "0") "Bugün toplantı yok" else "Bugün başka toplantı yok",
                    supporting = "Günün geri kalanında oda serbest.",
                    compactMode = compactMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showMetrics) {
            HorizontalDivider(color = statusPanelLineColor)
            StatusMetricsStrip(
                metrics = metrics,
                compactMode = compactMode,
                modifier = Modifier.padding(horizontal = padding, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun CurrentMeetingCard(
    event: RoomEvent,
    organizerTextSize: TextUnit,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    SummaryCard(
        modifier = modifier,
        compactMode = compactMode,
        title = "AKT\u0130F TOPLANTI",
        topRight = formatCompactDuration(event.durationMillis)
    ) {
        Text(
            text = event.displaySubject,
            color = TextWhite,
            fontSize = if (compactMode) 22.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Planlayan: ${event.organizerName}",
            color = TextWhite.copy(alpha = 0.74f),
            fontSize = if (compactMode) organizerTextSize else organizerTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = TimeUtils.formatEventRange(event.startMillis, event.endMillis),
            color = TextWhite,
            fontSize = if (compactMode) 17.sp else 21.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NextMeetingSummaryCard(
    nextEvent: RoomEvent,
    nowMillis: Long,
    organizerTextSize: TextUnit,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    SummaryCard(
        modifier = modifier,
        compactMode = compactMode,
        title = "SONRAKİ TOPLANTI"
    ) {
        Text(
            text = formatNextEventHeadline(nextEvent.startMillis, nowMillis),
            color = TextWhite,
            fontSize = if (compactMode) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Planlayan: ${nextEvent.organizerName}",
            color = TextWhite.copy(alpha = 0.74f),
            fontSize = if (compactMode) organizerTextSize else organizerTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = nextEvent.subject,
            color = TextWhite.copy(alpha = 0.78f),
            fontSize = if (compactMode) 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OpenWindowCard(
    nextEvent: RoomEvent,
    nowMillis: Long,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    SummaryCard(modifier = modifier, compactMode = compactMode, title = "BOŞ SÜRE") {
        Text(
            text = formatCompactDuration((nextEvent.startMillis - nowMillis).coerceAtLeast(0L)),
            color = TextWhite,
            fontSize = if (compactMode) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Sonraki toplantıya kadar", color = TextWhite.copy(alpha = 0.74f),
            fontSize = if (compactMode) 14.sp else 18.sp)
    }
}

@Composable
private fun NeutralSummaryCard(
    title: String,
    headline: String,
    supporting: String,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    SummaryCard(
        modifier = modifier,
        compactMode = compactMode,
        title = title
    ) {
        Text(
            text = headline,
            color = TextWhite,
            fontSize = if (compactMode) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (compactMode) 24.sp else 30.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (supporting.isNotBlank()) {
        Text(
            text = supporting,
            color = TextWhite.copy(alpha = 0.74f),
            fontSize = if (compactMode) 16.sp else 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    compactMode: Boolean,
    modifier: Modifier = Modifier,
    topRight: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(if (compactMode) 14.dp else 16.dp),
        border = BorderStroke(1.dp, statusPanelLineColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactMode) 16.dp else 24.dp, vertical = if (compactMode) 16.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextWhite.copy(alpha = 0.48f),
                    fontSize = if (compactMode) 10.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp
                )
                if (!topRight.isNullOrBlank()) {
                    Text(
                        text = topRight,
                        color = TextWhite.copy(alpha = 0.58f),
                        fontSize = if (compactMode) 10.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun StatusMetricsStrip(
    metrics: List<StatusMetric>,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (metrics.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
    ) {
        metrics.forEach { metric ->
            Surface(
                modifier = Modifier.weight(1f),
                color = Color.Black.copy(alpha = 0.10f),
                shape = RoundedCornerShape(if (compactMode) 12.dp else 14.dp),
                border = BorderStroke(1.dp, statusPanelLineColor)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (compactMode) 10.dp else 18.dp,
                        vertical = if (compactMode) 12.dp else 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = metric.label,
                        color = TextWhite.copy(alpha = 0.48f),
                        fontSize = if (compactMode) 9.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = metric.value,
                        color = TextWhite,
                        fontSize = if (compactMode) 24.sp else 42.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun formatCompactDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 && minutes == 0L -> "$hours SA"
        hours > 0 -> "${hours} SA ${minutes} DK"
        else -> "$minutes DK"
    }
}

private fun formatNextEventHeadline(eventMillis: Long, nowMillis: Long): String {
    val eventCalendar = Calendar.getInstance(statusTimeZone).apply { timeInMillis = eventMillis }
    val nowCalendar = Calendar.getInstance(statusTimeZone).apply { timeInMillis = nowMillis }
    val timeLabel = TimeUtils.formatClockTime(eventMillis)

    return if (
        eventCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
        eventCalendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR)
    ) {
        timeLabel
    } else {
        "${eventCalendar.get(Calendar.DAY_OF_MONTH)} ${statusMonthNames[eventCalendar.get(Calendar.MONTH)]} $timeLabel"
    }
}

private fun formatRemainingLabel(eventMillis: Long, nowMillis: Long): String {
    val remainingMillis = (eventMillis - nowMillis).coerceAtLeast(0L)
    if (remainingMillis < 60_000L) {
        return "Toplant\u0131 birazdan ba\u015fl\u0131yor"
    }

    val totalMinutes = remainingMillis / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 && hours > 0 -> "$days g\u00fcn $hours saat kald\u0131"
        days > 0 -> "$days g\u00fcn kald\u0131"
        hours > 0 && minutes > 0 -> "$hours saat $minutes dk kald\u0131"
        hours > 0 -> "$hours saat kald\u0131"
        else -> "$minutes dk kald\u0131"
    }
}

@Preview(widthDp = 930, heightDp = 680)
@Composable
private fun StatusPanelPreview() {
    val now = System.currentTimeMillis()
    val currentEvent = RoomEvent(
        subject = "Planlama",
        showAs = "busy",
        startMillis = now - 20 * 60_000L,
        endMillis = now + 40 * 60_000L,
        organizerName = "Toplantı Ekibi",
        organizerEmail = "team@example.invalid"
    )
    val nextEvent = RoomEvent(
        subject = "Depo",
        showAs = "busy",
        startMillis = now + 10 * 60 * 60_000L,
        endMillis = now + 11 * 60 * 60_000L,
        organizerName = "Toplantı Ekibi",
        organizerEmail = "team@example.invalid"
    )

    MeetingRoomTheme {
        Surface(color = Color(0xFF951313)) {
            StatusPanel(
                isOccupied = true,
                currentEvent = currentEvent,
                nextEvent = nextEvent,
                nowMillis = now,
                metrics = listOf(
                    StatusMetric("BUG\u00dcN TOPLAM", "8"),
                    StatusMetric("YAPILAN", "7"),
                    StatusMetric("KALAN", "1"),
                    StatusMetric("DOLULUK", "%0")
                ),
                heroTextSize = 140.sp,
                organizerTextSize = 18.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
            )
        }
    }
}
