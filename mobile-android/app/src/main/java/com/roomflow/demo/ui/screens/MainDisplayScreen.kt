package com.roomflow.demo.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.data.model.DeviceConfig
import com.roomflow.demo.data.model.RoomEvent
import com.roomflow.demo.domain.TimeUtils
import com.roomflow.demo.ui.components.BrandHeader
import com.roomflow.demo.ui.components.DailyTimeline
import com.roomflow.demo.ui.components.StatusMetric
import com.roomflow.demo.ui.components.StatusPanel
import com.roomflow.demo.ui.theme.AvailableBackground
import com.roomflow.demo.ui.theme.BorderColor
import com.roomflow.demo.ui.theme.AccentRed
import com.roomflow.demo.ui.theme.DarkestNavy
import com.roomflow.demo.ui.theme.ErrorBackground
import com.roomflow.demo.ui.theme.ErrorText
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.OccupiedBackground
import com.roomflow.demo.ui.theme.TextGray
import com.roomflow.demo.ui.theme.TextWhite
import com.roomflow.demo.viewmodel.MeetingUiState
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val panelTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
private val panelLocale: Locale = Locale.forLanguageTag("tr-TR")
private val panelMonthNames = arrayOf(
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
private val panelDayNames = arrayOf(
    "Pazar",
    "Pazartesi",
    "Sal\u0131",
    "\u00c7ar\u015famba",
    "Per\u015fembe",
    "Cuma",
    "Cumartesi"
)
private const val SETUP_HOLD_DURATION_MS = 5_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDisplayScreen(
    uiState: MeetingUiState,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
    onDemoScenario: (String) -> Unit = {}
) {
    val scale = rememberDisplayScale()
    val displayNow = TimeUtils.adjustedNow(uiState.currentTime, uiState.serverTimeOffset)
    val statusMetrics = remember(uiState.events, displayNow) {
        buildStatusMetrics(uiState.events, displayNow)
    }
    val panelColor = if (uiState.isOccupied) OccupiedBackground else AvailableBackground
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF060913)).padding(scale.framePadding)) {
        val stacked = maxWidth < 600.dp
        Column(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(onOpenSetup) {
                    detectTapGestures(onPress = {
                        val released = withTimeoutOrNull(SETUP_HOLD_DURATION_MS) { tryAwaitRelease() } != null
                        if (!released) onOpenSetup()
                    })
                },
                color = panelColor,
                shape = RoundedCornerShape(scale.containerRadius),
                border = BorderStroke(2.dp, BorderColor.copy(alpha = 0.55f))
            ) {
                if (stacked) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        StatusSide(uiState, displayNow, statusMetrics, scale, Modifier.fillMaxWidth().heightIn(min = 480.dp))
                        LeftTimelinePanel(uiState.events, displayNow, scale, Modifier.fillMaxWidth().height(260.dp))
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        LeftTimelinePanel(uiState.events, displayNow, scale, Modifier.width(scale.leftPanelWidth).fillMaxHeight())
                        StatusSide(uiState, displayNow, statusMetrics, scale,
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = 420.dp))
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.activeConfig?.isDemo == true) {
                    listOf("busy" to "Dolu", "available" to "Müsait", "empty" to "Boş takvim").forEach { (value, label) ->
                        androidx.compose.material3.TextButton(onClick = { onDemoScenario(value) }) {
                            Text(label, color = Color.White)
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onOpenSetup) { Text("Ayarlar", color = Color.White) }
            }
            if (uiState.errorMessage != null) {
                ConnectionErrorPill(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun StatusSide(
    uiState: MeetingUiState,
    displayNow: Long,
    metrics: List<StatusMetric>,
    scale: DisplayScale,
    modifier: Modifier
) {
    Column(modifier.background(if (uiState.isOccupied) OccupiedBackground else AvailableBackground)) {
        RightStatusHeader(uiState.activeConfig?.displayRoomName().orEmpty(), displayNow, scale)
        HorizontalDivider(color = Color.Black.copy(alpha = 0.32f))
        StatusPanel(
            isOccupied = uiState.isOccupied,
            currentEvent = uiState.currentEvent,
            nextEvent = uiState.nextEvent,
            nowMillis = displayNow,
            metrics = metrics,
            heroTextSize = scale.heroTextSize,
            organizerTextSize = scale.organizerTextSize,
            compactMode = scale.isCompact,
            showMetrics = scale.showMetrics,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
private fun LeftTimelinePanel(
    events: List<RoomEvent>,
    nowMillis: Long,
    scale: DisplayScale,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0C1017))
            .verticalScroll(rememberScrollState())
            .heightIn(min = 400.dp)
            .padding(horizontal = scale.panelPadding, vertical = scale.panelPadding)
    ) {
        DailyTimeline(
            events = events,
            nowMillis = nowMillis,
            maxItems = scale.timelineMaxItems,
            compactMode = scale.isCompact,
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp, bottom = 18.dp),
            color = BorderColor.copy(alpha = 0.9f)
        )

        BrandHeader(
            symbolSize = scale.brandSymbolSize,
            titleSize = scale.brandTitleSize,
            subtitleSize = scale.brandSubtitleSize
        )
    }
}

@Composable
private fun RightStatusHeader(
    roomName: String,
    nowMillis: Long,
    scale: DisplayScale
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = scale.headerHorizontalPadding, vertical = scale.headerVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = formatPanelDay(nowMillis).uppercase(panelLocale),
            modifier = Modifier.weight(1f),
            color = TextWhite.copy(alpha = 0.62f),
            fontSize = scale.dayLabelSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(roomName, color = TextWhite.copy(alpha = 0.82f), fontSize = scale.roomNameSize,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(TimeUtils.formatClockTime(nowMillis), color = TextWhite, fontSize = scale.clockSize,
                fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(formatPanelDate(nowMillis), color = TextWhite.copy(alpha = 0.66f), fontSize = scale.dateSize,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectionErrorPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = ErrorBackground,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "BA\u011eLANTI HATASI",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = ErrorText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun rememberDisplayScale(): DisplayScale {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    val fontScale = configuration.fontScale
    return remember(width, height, fontScale) {
        when {
            width < 900 || height < 500 -> DisplayScale(
                isCompact = true,
                showMetrics = false,
                framePadding = 4.dp,
                containerRadius = 22.dp,
                leftPanelWidth = (width * 0.30f).coerceIn(180f, 240f).dp,
                panelPadding = 16.dp,
                brandSymbolSize = 18.sp,
                brandTitleSize = 12.sp,
                brandSubtitleSize = 8.sp,
                roomNameSize = 16.sp,
                roomEmailSize = 11.sp,
                clockSize = (if (width < 600) 36f else 32f).div(fontScale.coerceAtLeast(1f)).sp,
                dateSize = 12.sp,
                dayLabelSize = 14.sp,
                headerHorizontalPadding = 12.dp,
                headerVerticalPadding = 8.dp,
                heroTextSize = ((if (width < 600) width - 44f else width * 0.70f - 48f) / 5.5f).coerceIn(40f, if (height < 500) 48f else 72f).div(fontScale.coerceAtLeast(1f)).sp,
                organizerTextSize = 16.sp,
                timelineMaxItems = if (height < 500) 2 else 3
            )

            width < 1380 || height < 820 -> DisplayScale(
                isCompact = true,
                showMetrics = true,
                framePadding = 4.dp,
                containerRadius = 26.dp,
                leftPanelWidth = 340.dp,
                panelPadding = 18.dp,
                brandSymbolSize = 18.sp,
                brandTitleSize = 12.sp,
                brandSubtitleSize = 8.sp,
                roomNameSize = 20.sp,
                roomEmailSize = 12.sp,
                clockSize = 50.sp,
                dateSize = 14.sp,
                dayLabelSize = 18.sp,
                headerHorizontalPadding = 24.dp,
                headerVerticalPadding = 20.dp,
                heroTextSize = 74.sp,
                organizerTextSize = 16.sp,
                timelineMaxItems = 4
            )

            else -> DisplayScale(
                isCompact = false,
                showMetrics = true,
                framePadding = 4.dp,
                containerRadius = 28.dp,
                leftPanelWidth = 500.dp,
                panelPadding = 28.dp,
                brandSymbolSize = 23.sp,
                brandTitleSize = 16.sp,
                brandSubtitleSize = 10.sp,
                roomNameSize = 24.sp,
                roomEmailSize = 15.sp,
                clockSize = 58.sp,
                dateSize = 18.sp,
                dayLabelSize = 22.sp,
                headerHorizontalPadding = 36.dp,
                headerVerticalPadding = 30.dp,
                heroTextSize = 112.sp,
                organizerTextSize = 19.sp,
                timelineMaxItems = 6
            )
        }
    }
}

private data class DisplayScale(
    val isCompact: Boolean,
    val showMetrics: Boolean,
    val framePadding: Dp,
    val containerRadius: Dp,
    val leftPanelWidth: Dp,
    val panelPadding: Dp,
    val brandSymbolSize: TextUnit,
    val brandTitleSize: TextUnit,
    val brandSubtitleSize: TextUnit,
    val roomNameSize: TextUnit,
    val roomEmailSize: TextUnit,
    val clockSize: TextUnit,
    val dateSize: TextUnit,
    val dayLabelSize: TextUnit,
    val headerHorizontalPadding: Dp,
    val headerVerticalPadding: Dp,
    val heroTextSize: TextUnit,
    val organizerTextSize: TextUnit,
    val timelineMaxItems: Int
)

private fun buildStatusMetrics(
    events: List<RoomEvent>,
    nowMillis: Long
): List<StatusMetric> {
    val todayEvents = events.filter { TimeUtils.isSameTurkeyDay(it.startMillis, nowMillis) }
    val completedCount = todayEvents.count { it.endMillis < nowMillis }
    val remainingCount = todayEvents.count { it.endMillis >= nowMillis }
    val occupancyPercentage = TimeUtils.calculateWorkdayOccupancyPercentage(
        events = todayEvents,
        referenceMillis = nowMillis
    )

    return listOf(
        StatusMetric(label = "BUG\u00dcN TOPLAM", value = todayEvents.size.toString()),
        StatusMetric(label = "YAPILAN", value = completedCount.toString()),
        StatusMetric(label = "KALAN", value = remainingCount.toString()),
        StatusMetric(label = "DOLULUK", value = "%$occupancyPercentage")
    )
}

private fun formatPanelDay(millis: Long): String {
    val calendar = Calendar.getInstance(panelTimeZone).apply { timeInMillis = millis }
    return panelDayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
}

private fun formatPanelDate(millis: Long): String {
    val calendar = Calendar.getInstance(panelTimeZone).apply { timeInMillis = millis }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = panelMonthNames[calendar.get(Calendar.MONTH)]
    val year = calendar.get(Calendar.YEAR)
    return "$day $month $year"
}

@Preview(widthDp = 1468, heightDp = 920)
@Composable
private fun MainDisplayScreenPreviewOccupied() {
    val now = System.currentTimeMillis()
    val currentEvent = RoomEvent(
        subject = "Planlama",
        showAs = "busy",
        startMillis = now - 25 * 60_000L,
        endMillis = now + 35 * 60_000L,
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
        MainDisplayScreen(
            uiState = MeetingUiState(
                isBootstrapping = false,
                activeConfig = DeviceConfig(
                    backendUrl = "https://calendar.example.invalid",
                    apiKey = "key",
                    roomName = "Berlin",
                    roomEmail = "team@example.invalid"
                ),
                currentTime = now,
                events = listOf(
                    RoomEvent(
                        subject = "Arge",
                        showAs = "busy",
                        startMillis = now - 2 * 60 * 60_000L,
                        endMillis = now - 90 * 60_000L,
                        organizerName = "Toplantı Ekibi",
                        organizerEmail = "team@example.invalid"
                    ),
                    RoomEvent(
                        subject = "Depo",
                        showAs = "busy",
                        startMillis = now - 90 * 60_000L,
                        endMillis = now - 60 * 60_000L,
                        organizerName = "Toplantı Ekibi",
                        organizerEmail = "team@example.invalid"
                    ),
                    currentEvent,
                    nextEvent
                ),
                currentEvent = currentEvent,
                nextEvent = nextEvent,
                isOccupied = true,
                lastUpdated = now - 60_000L,
                totalToday = 8,
                remaining = 1,
                occupiedHours = "7 sa"
            ),
            onOpenSetup = {}
        )
    }
}

@Preview(widthDp = 1468, heightDp = 920)
@Composable
private fun MainDisplayScreenPreviewAvailable() {
    val now = System.currentTimeMillis()
    val nextEvent = RoomEvent(
        subject = "Yeni \u00dcr\u00fcn Sunumu",
        showAs = "busy",
        startMillis = now + 2 * 60 * 60_000L,
        endMillis = now + 3 * 60 * 60_000L,
        organizerName = "Ürün Ekibi",
        organizerEmail = "team@example.invalid"
    )

    MeetingRoomTheme {
        MainDisplayScreen(
            uiState = MeetingUiState(
                isBootstrapping = false,
                activeConfig = DeviceConfig(
                    backendUrl = "https://calendar.example.invalid",
                    apiKey = "key",
                    roomName = "Berlin",
                    roomEmail = "team@example.invalid"
                ),
                currentTime = now,
                events = listOf(nextEvent),
                nextEvent = nextEvent,
                isOccupied = false,
                lastUpdated = now - 120_000L,
                totalToday = 4,
                remaining = 1,
                occupiedHours = "2 sa"
            ),
            onOpenSetup = {}
        )
    }
}
