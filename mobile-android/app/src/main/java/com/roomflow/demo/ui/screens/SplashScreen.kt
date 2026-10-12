package com.roomflow.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.ui.components.BrandHeader
import com.roomflow.demo.ui.theme.AccentRed
import com.roomflow.demo.ui.theme.DarkNavy
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.TextDarkGray

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BrandHeader(
                symbolSize = 20.sp,
                titleSize = 13.sp,
                subtitleSize = 9.sp
            )
            CircularProgressIndicator(
                color = AccentRed,
                strokeWidth = 3.dp
            )
            Text(
                text = "Uygulama hazırlanıyor...",
                color = TextDarkGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B2E)
@Composable
private fun SplashScreenPreview() {
    MeetingRoomTheme {
        SplashScreen(modifier = Modifier.height(280.dp))
    }
}
