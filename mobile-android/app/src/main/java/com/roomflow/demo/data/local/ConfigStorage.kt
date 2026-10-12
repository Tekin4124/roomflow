package com.roomflow.demo.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.roomflow.demo.data.model.DeviceConfig

class ConfigStorage(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences? by lazy { createPreferencesOrNull() }

    fun loadConfig(): DeviceConfig? {
        val safePreferences = preferences ?: return null
        val backendUrl = safePreferences.getString(KEY_BACKEND_URL, "").orEmpty()
        val apiKey = safePreferences.getString(KEY_API_KEY, "").orEmpty()
        val roomEmail = safePreferences.getString(KEY_ROOM_EMAIL, "").orEmpty()

        if (backendUrl.isBlank() && apiKey.isBlank() && roomEmail.isBlank()) {
            return null
        }

        return DeviceConfig(
            backendUrl = backendUrl,
            apiKey = apiKey,
            roomName = safePreferences.getString(KEY_ROOM_NAME, "").orEmpty().let { if (it == "ATLAS • DEMO") "Atlas" else it },
            roomEmail = roomEmail,
            refreshIntervalSeconds = safePreferences.getInt(
                KEY_REFRESH_INTERVAL,
                DeviceConfig.DEFAULT_REFRESH_INTERVAL_SECONDS
            )
        ).normalized()
    }

    fun saveConfig(config: DeviceConfig) {
        val safePreferences = preferences ?: throw IllegalStateException("Secure storage unavailable.")
        val normalized = config.normalized()
        safePreferences.edit()
            .putString(KEY_BACKEND_URL, normalized.backendUrl)
            .putString(KEY_API_KEY, normalized.apiKey)
            .putString(KEY_ROOM_NAME, normalized.roomName)
            .putString(KEY_ROOM_EMAIL, normalized.roomEmail)
            .putInt(KEY_REFRESH_INTERVAL, normalized.safeRefreshIntervalSeconds)
            .apply()
    }

    fun clearConfig() {
        preferences?.edit()?.clear()?.apply()
    }

    private fun createPreferencesOrNull(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val PREFS_NAME = "meeting_room_device_config"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_ROOM_NAME = "room_name"
        const val KEY_ROOM_EMAIL = "room_email"
        const val KEY_REFRESH_INTERVAL = "refresh_interval_seconds"
    }
}
