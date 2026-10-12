package com.roomflow.demo.data.model

import java.util.Locale
import java.net.URI

data class DeviceConfig(
    val backendUrl: String = "",
    val apiKey: String = "",
    val roomName: String = "",
    val roomEmail: String = "",
    val refreshIntervalSeconds: Int = DEFAULT_REFRESH_INTERVAL_SECONDS
) {
    val isDemo: Boolean get() = backendUrl == "demo://local"

    val safeRefreshIntervalSeconds: Int
        get() = refreshIntervalSeconds.coerceIn(15, 300)

    fun normalized(): DeviceConfig = copy(
        backendUrl = backendUrl.normalizeBackendUrl(),
        apiKey = apiKey.normalizeApiKey(),
        roomName = roomName.trim(),
        roomEmail = roomEmail.trim(),
        refreshIntervalSeconds = safeRefreshIntervalSeconds
    )

    fun displayRoomName(): String {
        if (roomName.isNotBlank()) {
            return roomName.trim()
        }

        val derived = roomEmail
            .substringBefore("@")
            .replace('.', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (derived.isBlank()) {
            "TOPLANTI ODASI"
        } else {
            derived.uppercase(Locale.US)
        }
    }

    companion object {
        fun demo() = DeviceConfig(backendUrl = "demo://local", roomName = "Atlas", roomEmail = "atlas@example.invalid")
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 60
    }
}

private fun String.normalizeBackendUrl(): String {
    val firstLine = substringBefore('\n')
        .substringBefore('\r')
        .trim()
    if (firstLine.isBlank()) {
        return ""
    }

    val trimmed = firstLine.trimEnd('/')
    val strippedPath = trimmed.replace(
        Regex("/(?:admin|health)(?:/.*)?$", RegexOption.IGNORE_CASE),
        ""
    )

    return runCatching {
        val uri = URI(strippedPath)
        if (uri.scheme.isNullOrBlank()) {
            strippedPath
        } else {
            strippedPath
        }
    }.getOrDefault(strippedPath)
}

private fun String.normalizeApiKey(): String {
    return substringBefore('\n')
        .substringBefore('\r')
        .filterNot(Char::isWhitespace)
}
