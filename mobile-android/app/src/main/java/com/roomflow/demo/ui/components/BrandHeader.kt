package com.roomflow.demo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.TextDarkGray
import com.roomflow.demo.ui.theme.TextGray
import com.roomflow.demo.ui.theme.TextWhite

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    symbolSize: TextUnit = 20.sp,
    titleSize: TextUnit = 13.sp,
    subtitleSize: TextUnit = 9.sp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarMark(
            width = (symbolSize.value * 1.7f).dp,
            height = (symbolSize.value * 1.28f).dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "ROOMFLOW",
                color = TextWhite,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "TOPLANTI TAKVİMİ",
                color = TextGray,
                fontSize = subtitleSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
fun BrandFooterLockup(
    modifier: Modifier = Modifier,
    textSize: TextUnit = 10.sp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarMark(
            width = (textSize.value * 1.8f).dp,
            height = (textSize.value * 1.15f).dp
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "ROOMFLOW",
            color = TextDarkGray,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "TAKVİM",
            color = TextDarkGray,
            fontSize = textSize,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CalendarMark(
    width: Dp,
    height: Dp
) {
    Canvas(
        modifier = Modifier
            .width(width)
            .height(height)
    ) {
        drawRoundRect(color = Color(0xFF247F79), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, size.height * .32f), androidx.compose.ui.geometry.Offset(size.width, size.height * .32f), 3f)
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(size.width * .3f, size.height * .55f), androidx.compose.ui.geometry.Offset(size.width * .45f, size.height * .75f), 3f)
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(size.width * .45f, size.height * .75f), androidx.compose.ui.geometry.Offset(size.width * .75f, size.height * .45f), 3f)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B2E)
@Composable
private fun BrandHeaderPreview() {
    MeetingRoomTheme {
        BrandHeader()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF060F1C)
@Composable
private fun BrandFooterLockupPreview() {
    MeetingRoomTheme {
        BrandFooterLockup()
    }
}
