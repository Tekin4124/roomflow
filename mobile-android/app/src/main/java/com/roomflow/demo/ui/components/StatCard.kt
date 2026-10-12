package com.roomflow.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.ui.theme.DarkerNavy
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.TextGray
import com.roomflow.demo.ui.theme.TextWhite

@Composable
fun StatCard(
    label: String,
    value: String,
    valueSize: TextUnit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(10.dp)
) {
    Column(
        modifier = modifier
            .background(
                color = DarkerNavy,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.6.sp
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1B2E)
@Composable
private fun StatCardPreview() {
    MeetingRoomTheme {
        StatCard(
            label = "BUGÜN",
            value = "6",
            valueSize = 22.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
