package com.roomflow.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomflow.demo.data.model.DeviceConfig
import com.roomflow.demo.ui.components.BrandHeader
import com.roomflow.demo.ui.theme.BorderColor
import com.roomflow.demo.ui.theme.AccentRed
import com.roomflow.demo.ui.theme.DarkNavy
import com.roomflow.demo.ui.theme.DarkerNavy
import com.roomflow.demo.ui.theme.ErrorBackground
import com.roomflow.demo.ui.theme.ErrorText
import com.roomflow.demo.ui.theme.MeetingRoomTheme
import com.roomflow.demo.ui.theme.SecondaryButtonBg
import com.roomflow.demo.ui.theme.SecondaryButtonBorder
import com.roomflow.demo.ui.theme.SecondaryButtonText
import com.roomflow.demo.ui.theme.TextGray
import com.roomflow.demo.ui.theme.TextWhite

@Composable
fun SetupScreen(
    setupValues: DeviceConfig,
    setupError: String,
    isSaving: Boolean,
    hasActiveConfig: Boolean,
    onBackendUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRoomNameChange: (String) -> Unit,
    onRoomEmailChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    onStartDemo: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        val isPhone = maxWidth < 600.dp
        val scrollState = rememberScrollState()
        val cardPadding = if (isPhone) 20.dp else 28.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                color = DarkerNavy,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(cardPadding),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    SetupHeader()
                    androidx.compose.material3.OutlinedButton(onClick = onStartDemo) { Text("Örnek takvimi aç") }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Toplantı ekranı bağlantısını ayarla",
                            color = TextWhite,
                            fontSize = if (isPhone) 26.sp else 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Sunucu adresi, API anahtarı ve oda bilgilerini girin. Ayarlar cihaz içinde şifreli olarak saklanır.",
                            color = TextGray,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }

                    SetupFields(
                        setupValues = setupValues,
                        isPhone = isPhone,
                        isSaving = isSaving,
                        onBackendUrlChange = onBackendUrlChange,
                        onApiKeyChange = onApiKeyChange,
                        onRoomNameChange = onRoomNameChange,
                        onRoomEmailChange = onRoomEmailChange
                    )

                    HorizontalDivider(color = BorderColor)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Ayarlar cihaz içinde şifreli olarak saklanır. Gerekirse daha sonra ana ekrandaki sol üst bölgeye uzun basarak kurulum ekranına dönebilirsiniz.",
                            color = TextGray,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        if (setupError.isNotBlank()) {
                            ErrorPill(text = setupError)
                        }

                        SetupActions(
                            isPhone = isPhone,
                            isSaving = isSaving,
                            hasActiveConfig = hasActiveConfig,
                            onSave = onSave,
                            onCancel = onCancel,
                            onReset = onReset
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandHeader()
        Surface(
            color = DarkNavy,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(999.dp))
        ) {
            Text(
                text = "CİHAZ KURULUMU",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = TextGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.3.sp
            )
        }
    }
}

@Composable
private fun SetupFields(
    setupValues: DeviceConfig,
    isPhone: Boolean,
    isSaving: Boolean,
    onBackendUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRoomNameChange: (String) -> Unit,
    onRoomEmailChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SetupTextField(
            label = "Sunucu Adresi",
            value = setupValues.backendUrl,
            placeholder = "https://calendar.example.invalid",
            enabled = !isSaving,
            keyboardType = KeyboardType.Uri,
            onValueChange = onBackendUrlChange
        )

        if (isPhone) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SetupTextField(
                    label = "API Anahtarı",
                    value = setupValues.apiKey,
                    placeholder = "Sunucu erişim anahtarı",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    onValueChange = onApiKeyChange
                )
                SetupTextField(
                    label = "Oda Adı",
                    value = setupValues.roomName,
                    placeholder = "Toplantı Odası 1",
                    enabled = !isSaving,
                    onValueChange = onRoomNameChange
                )
                SetupTextField(
                    label = "Oda E-postası",
                    value = setupValues.roomEmail,
                    placeholder = "team@example.invalid",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Email,
                    onValueChange = onRoomEmailChange
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SetupTextField(
                    label = "API Anahtarı",
                    value = setupValues.apiKey,
                    placeholder = "Sunucu erişim anahtarı",
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.weight(1f)
                )
                SetupTextField(
                    label = "Oda Adı",
                    value = setupValues.roomName,
                    placeholder = "Toplantı Odası 1",
                    enabled = !isSaving,
                    onValueChange = onRoomNameChange,
                    modifier = Modifier.weight(1f)
                )
            }

            SetupTextField(
                label = "Oda E-postası",
                value = setupValues.roomEmail,
                placeholder = "team@example.invalid",
                enabled = !isSaving,
                keyboardType = KeyboardType.Email,
                onValueChange = onRoomEmailChange
            )
        }
    }
}

@Composable
private fun SetupTextField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextGray.copy(alpha = 0.8f),
                    fontSize = 15.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BorderColor,
                unfocusedBorderColor = BorderColor,
                disabledBorderColor = BorderColor.copy(alpha = 0.55f),
                focusedContainerColor = DarkNavy,
                unfocusedContainerColor = DarkNavy,
                disabledContainerColor = DarkNavy.copy(alpha = 0.75f),
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                disabledTextColor = TextWhite.copy(alpha = 0.7f),
                cursorColor = AccentRed
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            }
        )
    }
}

@Composable
private fun ErrorPill(text: String) {
    Surface(
        color = ErrorBackground,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = ErrorText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SetupActions(
    isPhone: Boolean,
    isSaving: Boolean,
    hasActiveConfig: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    if (isPhone) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasActiveConfig) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextWhite
                    )
                ) {
                    Text(text = "Vazgeç")
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryButtonBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SecondaryButtonBg,
                        contentColor = SecondaryButtonText
                    )
                ) {
                    Text(text = "Sıfırla")
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    contentColor = TextWhite
                )
            ) {
                Text(
                    text = if (isSaving) "Kaydediliyor..." else "Kaydet ve Bağlan",
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasActiveConfig) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(text = "Vazgeç")
                }
                Spacer(modifier = Modifier.widthIn(min = 12.dp))
                OutlinedButton(
                    onClick = onReset,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryButtonBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SecondaryButtonBg,
                        contentColor = SecondaryButtonText
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(text = "Sıfırla")
                }
                Spacer(modifier = Modifier.widthIn(min = 12.dp))
            }

            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    contentColor = TextWhite
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp)
            ) {
                Text(text = if (isSaving) "Kaydediliyor..." else "Kaydet ve Bağlan")
            }
        }
    }
}

@Preview(widthDp = 900, heightDp = 700)
@Composable
private fun SetupScreenPreview() {
    MeetingRoomTheme {
        SetupScreen(
            setupValues = DeviceConfig(
                backendUrl = "https://calendar.example.invalid",
                apiKey = "local-demo-key",
                roomName = "Toplantı Odası 1",
                roomEmail = "team@example.invalid"
            ),
            setupError = "",
            isSaving = false,
            hasActiveConfig = true,
            onBackendUrlChange = {},
            onApiKeyChange = {},
            onRoomNameChange = {},
            onRoomEmailChange = {},
            onSave = {},
            onCancel = {},
            onReset = {}
        )
    }
}
