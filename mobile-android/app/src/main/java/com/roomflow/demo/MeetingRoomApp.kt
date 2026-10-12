package com.roomflow.demo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomflow.demo.ui.screens.MainDisplayScreen
import com.roomflow.demo.ui.screens.SetupScreen
import com.roomflow.demo.ui.screens.SplashScreen
import com.roomflow.demo.viewmodel.MeetingViewModel
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun MeetingRoomApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPhone = min(configuration.screenWidthDp, configuration.screenHeightDp) < 600
    val viewModel: MeetingViewModel = viewModel(
        factory = remember(context) {
            MeetingViewModel.Factory(context.applicationContext)
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeviceOrientationEffect(
        isSetupMode = uiState.isSetupMode,
        isPhone = isPhone
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        // Saat tikinin sadece ekran aktifken calismasi yeterli; arka plana
        // gecince zaman zaten Compose tarafindan render edilmiyor.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.onClockTick()
                delay(1_000)
            }
        }
    }

    val activeConfig = uiState.activeConfig
    LaunchedEffect(activeConfig, uiState.isSetupMode, lifecycleOwner) {
        if (activeConfig == null || uiState.isSetupMode) {
            return@LaunchedEffect
        }

        // Polling sadece STARTED durumunda calisir, tablet uyuyunca network/CPU yok.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.loadEvents()
                delay(activeConfig.safeRefreshIntervalSeconds * 1_000L)
            }
        }
    }

    when {
        uiState.isBootstrapping -> SplashScreen(modifier = modifier)
        uiState.isSetupMode -> SetupScreen(
            setupValues = uiState.setupValues,
            setupError = uiState.setupError,
            isSaving = uiState.isSavingConfig,
            hasActiveConfig = uiState.activeConfig != null,
            onBackendUrlChange = viewModel::updateBackendUrl,
            onApiKeyChange = viewModel::updateApiKey,
            onRoomNameChange = viewModel::updateRoomName,
            onRoomEmailChange = viewModel::updateRoomEmail,
            onSave = viewModel::saveConfig,
            onCancel = viewModel::cancelSetup,
            onReset = viewModel::resetConfig,
            onStartDemo = { viewModel.startDemo() },
            modifier = modifier
        )

        else -> MainDisplayScreen(
            uiState = uiState,
            onDemoScenario = viewModel::startDemo,
            onOpenSetup = viewModel::openSetup,
            modifier = modifier
        )
    }
}

@Composable
private fun DeviceOrientationEffect(isSetupMode: Boolean, isPhone: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return

    LaunchedEffect(activity, isSetupMode, isPhone) {
        activity.requestedOrientation = when {
            isSetupMode && isPhone -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            isPhone -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
