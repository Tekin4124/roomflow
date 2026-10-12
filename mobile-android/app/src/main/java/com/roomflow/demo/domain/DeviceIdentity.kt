package com.roomflow.demo.domain

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

object DeviceIdentity {
    fun resolve(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
            .orEmpty()
            .trim()

        if (androidId.isNotBlank() && androidId.lowercase(Locale.US) != INVALID_ANDROID_ID) {
            return "android-$androidId"
        }

        val fallback = listOf(Build.MANUFACTURER, Build.MODEL, Build.DEVICE)
            .map(::sanitizePart)
            .filter(String::isNotBlank)
            .joinToString("-")
            .ifBlank { "unknown-device" }

        return "android-$fallback".take(MAX_DEVICE_ID_LENGTH)
    }

    fun displayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val device = Build.DEVICE.orEmpty().trim()

        val combined = listOf(manufacturer, model)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
            .trim()

        if (combined.isNotBlank()) {
            return combined.take(MAX_LABEL_LENGTH)
        }

        if (device.isNotBlank()) {
            return device.take(MAX_LABEL_LENGTH)
        }

        return "Android Cihaz"
    }

    private fun sanitizePart(value: String?): String {
        return value.orEmpty()
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._:-]+"), "-")
            .trim('-')
    }

    private const val INVALID_ANDROID_ID = "9774d56d682e549c"
    private const val MAX_DEVICE_ID_LENGTH = 128
    private const val MAX_LABEL_LENGTH = 48
}
