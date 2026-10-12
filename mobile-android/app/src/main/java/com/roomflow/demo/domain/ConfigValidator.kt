package com.roomflow.demo.domain

import android.net.Uri
import android.util.Patterns
import com.roomflow.demo.data.model.DeviceConfig
import java.util.Locale

object ConfigValidator {
    fun validate(config: DeviceConfig): String? {
        val normalized = config.normalized()
        if (normalized.isDemo) return null

        if (normalized.backendUrl.isBlank()) {
            return "Sunucu adresi zorunludur."
        }
        if (!isBackendUrlAllowed(normalized.backendUrl)) {
            return "Sunucu adresi HTTPS olmalı veya yerel takvim adresi kullanılmalıdır."
        }
        if (normalized.apiKey.isBlank()) {
            return "API anahtarı zorunludur."
        }
        if (normalized.roomEmail.isBlank()) {
            return "Oda e-posta adresi zorunludur."
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized.roomEmail).matches()) {
            return "Oda e-posta adresi geçersiz."
        }

        return null
    }

    fun isBackendUrlAllowed(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false

        if (host.isBlank()) {
            return false
        }

        return when (scheme) {
            "https" -> true
            "http" -> host in setOf("localhost", "127.0.0.1", "10.0.2.2")
            else -> false
        }
    }

    private fun isPrivateNetworkHost(host: String): Boolean {
        if (host == "localhost") {
            return true
        }

        val octets = host.split('.')
        if (octets.size != 4) {
            return false
        }

        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in 0..255 }) {
            return false
        }

        val first = values[0]
        val second = values[1]

        return first == 10 ||
            first == 127 ||
            (first == 192 && second == 168) ||
            (first == 172 && second in 16..31)
    }
}
