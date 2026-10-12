package com.roomflow.demo.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.roomflow.demo.data.local.ConfigStorage
import com.roomflow.demo.data.model.DeviceRegistrationRequest
import com.roomflow.demo.data.model.DeviceConfig
import com.roomflow.demo.data.model.RoomEvent
import com.roomflow.demo.data.remote.ApiClient
import com.roomflow.demo.domain.ConfigValidator
import com.roomflow.demo.domain.DeviceIdentity
import com.roomflow.demo.domain.MeetingStateCalculator
import com.roomflow.demo.domain.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

data class MeetingUiState(
    val isBootstrapping: Boolean = true,
    val isSetupMode: Boolean = false,
    val isSavingConfig: Boolean = false,
    val setupError: String = "",
    val activeConfig: DeviceConfig? = null,
    val setupValues: DeviceConfig = DeviceConfig(),
    val currentTime: Long = System.currentTimeMillis(),
    val serverTimeOffset: Long = 0L,
    val events: List<RoomEvent> = emptyList(),
    val currentEvent: RoomEvent? = null,
    val nextEvent: RoomEvent? = null,
    val isOccupied: Boolean = false,
    val lastUpdated: Long? = null,
    val errorMessage: String? = null,
    val totalToday: Int = 0,
    val remaining: Int = 0,
    val occupiedHours: String = "0 sa"
)

class MeetingViewModel(
    private val configStorage: ConfigStorage,
    private val deviceId: String,
    private val deviceLabel: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(MeetingUiState())
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private var demoScenario = "busy"
    private var demoAnchor = System.currentTimeMillis()
    private val refreshMutex = Mutex()
    private var lastRegisteredAt: Long = 0L
    private var lastRegisteredFingerprint: String? = null
    private val rememberedSubjects = mutableMapOf<String, String>()

    init {
        bootstrap()
    }

    fun updateBackendUrl(value: String) {
        _uiState.update { state ->
            state.copy(
                setupValues = state.setupValues.copy(backendUrl = value),
                setupError = ""
            )
        }
    }

    fun updateApiKey(value: String) {
        _uiState.update { state ->
            state.copy(
                setupValues = state.setupValues.copy(apiKey = value),
                setupError = ""
            )
        }
    }

    fun updateRoomName(value: String) {
        _uiState.update { state ->
            state.copy(
                setupValues = state.setupValues.copy(roomName = value),
                setupError = ""
            )
        }
    }

    fun updateRoomEmail(value: String) {
        _uiState.update { state ->
            state.copy(
                setupValues = state.setupValues.copy(roomEmail = value),
                setupError = ""
            )
        }
    }

    fun onClockTick(nowMillis: Long = System.currentTimeMillis()) {
        _uiState.update { state ->
            recalculate(state.copy(currentTime = nowMillis))
        }
    }

    fun openSetup() {
        _uiState.update { state ->
            state.copy(
                isSetupMode = true,
                setupError = "",
                setupValues = state.activeConfig ?: state.setupValues
            )
        }
    }

    fun cancelSetup() {
        val activeConfig = _uiState.value.activeConfig ?: return
        _uiState.update { state ->
            state.copy(
                isSetupMode = false,
                setupError = "",
                setupValues = activeConfig
            )
        }
    }

    fun resetConfig() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                configStorage.clearConfig()
            }

            _uiState.value = MeetingUiState(
                isBootstrapping = false,
                isSetupMode = true,
                currentTime = System.currentTimeMillis()
            )
        }
    }

    fun saveConfig() {
        val normalized = _uiState.value.setupValues.normalized()
        val validationError = ConfigValidator.validate(normalized)
        if (validationError != null) {
            _uiState.update { it.copy(setupError = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingConfig = true, setupError = "") }

            val saveResult = withContext(Dispatchers.IO) {
                runCatching { configStorage.saveConfig(normalized) }
            }

            saveResult
                .onSuccess {
                    _uiState.update { state ->
                        recalculate(
                            state.copy(
                                isSavingConfig = false,
                                isSetupMode = false,
                                activeConfig = normalized,
                                setupValues = normalized,
                                errorMessage = null
                            )
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSavingConfig = false,
                            setupError = "Ayarlar kaydedilemedi. Lütfen tekrar deneyin."
                        )
                    }
                }
        }
    }

    fun startDemo(scenario: String = "busy") {
        demoScenario = scenario
        demoAnchor = System.currentTimeMillis()
        val config = DeviceConfig.demo()
        _uiState.update { state -> recalculate(state.copy(isBootstrapping = false, isSetupMode = false, activeConfig = config, setupValues = config, events = com.roomflow.demo.domain.DemoCalendar.events(scenario, demoAnchor), serverTimeOffset = 0L, errorMessage = null, lastUpdated = demoAnchor)) }
        viewModelScope.launch(Dispatchers.IO) { runCatching { configStorage.saveConfig(config) } }
    }

    fun loadEvents() {
        val config = _uiState.value.activeConfig?.normalized() ?: return

        if (config.isDemo) {
            _uiState.update { state -> recalculate(state.copy(events = com.roomflow.demo.domain.DemoCalendar.events(demoScenario, demoAnchor), lastUpdated = System.currentTimeMillis(), errorMessage = null)) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // tryLock + try/finally: cancel veya exception'da deadlock olmaz,
            // ayni anda iki refresh baslatilamaz.
            if (!refreshMutex.tryLock()) {
                return@launch
            }

            try {
                val apiService = ApiClient.create(config.backendUrl)
                val now = System.currentTimeMillis()
                val fingerprint = "${config.backendUrl}|${config.apiKey}|${config.roomEmail}|$deviceLabel"
                val configChanged = fingerprint != lastRegisteredFingerprint
                val registerExpired = (now - lastRegisteredAt) >= REGISTER_HEARTBEAT_INTERVAL_MS
                val shouldRegister = configChanged || registerExpired

                if (shouldRegister) {
                    runCatching {
                        apiService.registerDevice(
                            deviceId = deviceId,
                            apiKey = config.apiKey,
                            request = DeviceRegistrationRequest(
                                roomEmail = config.roomEmail,
                                deviceLabel = deviceLabel
                            )
                        )
                    }.onSuccess {
                        lastRegisteredAt = now
                        lastRegisteredFingerprint = fingerprint
                    }
                }

                val response = apiService.getRoomEvents(
                    roomEmail = config.roomEmail,
                    apiKey = config.apiKey
                )

                val responseReceivedAt = System.currentTimeMillis()
                val lastUpdatedMillis = TimeUtils.parseBackendDateTime(response.lastUpdated)
                val serverTimeOffset =
                    lastUpdatedMillis?.minus(responseReceivedAt) ?: _uiState.value.serverTimeOffset

                val events = response.events
                    .mapNotNull { it.toRoomEvent() }
                    .filterNot { it.showAs.equals("free", ignoreCase = true) }
                    .sortedBy { it.startMillis }
                    .let(::restoreRememberedSubjects)

                _uiState.update { state ->
                    recalculate(
                        state.copy(
                            activeConfig = config,
                            events = events,
                            serverTimeOffset = serverTimeOffset,
                            lastUpdated = lastUpdatedMillis,
                            errorMessage = null
                        )
                    )
                }
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Etkinlikler yuklenemedi. roomEmail=${config.roomEmail}, backendUrl=${config.backendUrl}",
                    throwable
                )
                _uiState.update { state ->
                    recalculate(
                        state.copy(errorMessage = throwable.toUserMessage())
                    )
                }
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val storedConfig = withContext(Dispatchers.IO) {
                configStorage.loadConfig()
            }
            if (storedConfig == null || storedConfig.isDemo) {
                startDemo()
                return@launch
            }
            val normalizedConfig = storedConfig.normalized()

            val validationError = normalizedConfig?.let(ConfigValidator::validate)
            if (normalizedConfig != storedConfig && validationError == null) {
                withContext(Dispatchers.IO) {
                    runCatching { configStorage.saveConfig(normalizedConfig) }
                }
            }

            _uiState.value = if (validationError != null) {
                MeetingUiState(
                    isBootstrapping = false,
                    isSetupMode = true,
                    setupValues = normalizedConfig ?: DeviceConfig(),
                    currentTime = System.currentTimeMillis()
                )
            } else {
                recalculate(
                    MeetingUiState(
                        isBootstrapping = false,
                        isSetupMode = false,
                        activeConfig = normalizedConfig,
                        setupValues = normalizedConfig,
                        currentTime = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun recalculate(state: MeetingUiState): MeetingUiState {
        val nowMillis = TimeUtils.adjustedNow(state.currentTime, state.serverTimeOffset)
        val derivedState = MeetingStateCalculator.derive(
            events = state.events,
            nowMillis = nowMillis
        )

        return state.copy(
            currentEvent = derivedState.currentEvent,
            nextEvent = derivedState.nextEvent,
            isOccupied = derivedState.isOccupied,
            totalToday = derivedState.totalToday,
            remaining = derivedState.remaining,
            occupiedHours = derivedState.occupiedHours
        )
    }

    private fun restoreRememberedSubjects(events: List<RoomEvent>): List<RoomEvent> {
        return events.map { event ->
            val key = event.identityKey()
            val subject = event.subject.trim()
            val looksLikeOrganizer = event.subjectLooksLikeOrganizer()
            val rememberedSubject = rememberedSubjects[key]

            when {
                subject.isNotBlank() && !looksLikeOrganizer -> {
                    rememberedSubjects[key] = subject
                    event.copy(subject = subject)
                }

                rememberedSubject != null -> event.copy(subject = rememberedSubject)
                else -> event
            }
        }
    }

    private fun RoomEvent.identityKey(): String {
        val organizer = organizerEmail.ifBlank { organizerName }.trim().lowercase()
        return "$startMillis|$endMillis|$organizer"
    }

    private fun RoomEvent.subjectLooksLikeOrganizer(): Boolean {
        val normalizedSubject = subject.trim()
        if (normalizedSubject.isBlank()) {
            return false
        }

        return normalizedSubject.equals(organizerName.trim(), ignoreCase = true) ||
            normalizedSubject.equals(organizerEmail.trim(), ignoreCase = true)
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is HttpException -> when (code()) {
                401 -> "API anahtarı geçersiz. Lütfen ayarları kontrol edin."
                404 -> "Oda bulunamadı. E-posta adresini kontrol edin."
                else -> "Sunucudan veri alınırken bir hata oluştu."
            }

            is SocketTimeoutException -> "Sunucuya bağlanırken zaman aşımı."
            is IOException -> "Sunucuya erişilemedi. Adres ve ağ bağlantısını kontrol edin."
            else -> "Beklenmeyen bir hata oluştu."
        }
    }

    class Factory(
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeetingViewModel(
                configStorage = ConfigStorage(appContext),
                deviceId = DeviceIdentity.resolve(appContext),
                deviceLabel = DeviceIdentity.displayName()
            ) as T
        }
    }

    private companion object {
        const val TAG = "MeetingViewModel"
        // Tablet her polling'de register cagirmamali. Yapilandirma degismedikce
        // gunde bir defa heartbeat yeter (config + lastSeenAt).
        const val REGISTER_HEARTBEAT_INTERVAL_MS = 12L * 60L * 60L * 1000L
    }
}
