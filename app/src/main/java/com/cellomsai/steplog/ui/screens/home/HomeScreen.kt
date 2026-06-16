package com.cellomsai.steplog.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cellomsai.steplog.ui.components.BodyConditionInput
import com.cellomsai.steplog.ui.components.StepsDisplay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Staggered entrance: body-condition form slides in after the step counter
    var bodyFormVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        bodyFormVisible = true
    }

    // ACTIVITY_RECOGNITION パーミッションリクエストランチャー
    val requestActivityRecognition = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onActivityRecognitionResult(granted)
    }

    // Health Connect パーミッションリクエストランチャー
    val requestHealthConnect = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    // 初回起動などで未連携なら Health Connect 権限リクエストを自動起動
    LaunchedEffect(uiState.shouldRequestHealthConnect) {
        if (uiState.shouldRequestHealthConnect) {
            viewModel.onHealthConnectRequestConsumed()
            runCatching { requestHealthConnect.launch(viewModel.healthConnect.permissions) }
        }
    }

    // 画面表示中は数秒ごとに歩数を静かに更新（リアルタイム反映）
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(4000)
                viewModel.refreshSteps(showLoading = false)
            }
        }
    }

    // 位置情報パーミッションリクエストランチャー（気圧取得用）
    val requestLocation = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onLocationPermissionGranted()
    }

    // Health Connect から戻ったときに権限状態と歩数を再チェック
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 初回にパーミッション状態をチェック＆位置情報権限をリクエスト
    LaunchedEffect(Unit) {
        val arGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onActivityRecognitionResult(arGranted)

        val locGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (locGranted) {
            viewModel.onLocationPermissionGranted()
        } else {
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(uiState.savedToastVisible) {
        if (uiState.savedToastVisible) {
            snackbarHostState.showSnackbar("記録しました")
            viewModel.dismissToast()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // HC を開く：設定画面 → アプリ直接起動 の順で試す
    fun openHealthConnect() {
        // 1. HC 設定画面（最も確実）
        try {
            context.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
            return
        } catch (_: Exception) { }

        // 2. HC アプリをパッケージ名で直接起動
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage("com.google.android.apps.healthdata")
                ?: error("not found")
            context.startActivity(intent)
            return
        } catch (_: Exception) { }

        scope.launch {
            snackbarHostState.showSnackbar(
                "Health Connect が起動できません。Play ストアでインストール・更新してください。"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.today.format(
                            DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPANESE)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    uiState.record?.pressure?.let { pressure ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Air,
                                contentDescription = "気圧",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "%.0f hPa".format(pressure),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoadingSteps,
            onRefresh = { viewModel.refreshSteps() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!uiState.healthConnectAvailable && !uiState.sensorAvailable) {
                    Text(
                        text = "この端末では歩数の自動取得ができません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                StepsDisplay(steps = uiState.steps)

                AnimatedVisibility(
                    visible = uiState.record?.pressure != null,
                    enter = fadeIn(animationSpec = tween(300)) +
                        expandVertically(animationSpec = tween(300), expandFrom = Alignment.Top),
                    exit = fadeOut(animationSpec = tween(200)) +
                        shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Top),
                ) {
                    uiState.record?.pressure?.let { pressure ->
                        PressureChip(
                            pressure = pressure,
                            trend = pressureTrend(pressure, uiState.yesterdayPressure),
                            precipitationMm = uiState.record?.precipitationMm,
                        )
                    }
                }

                val todayPressure = uiState.record?.pressure
                val pressureTrendValue = if (todayPressure != null) pressureTrend(todayPressure, uiState.yesterdayPressure) else null
                AnimatedVisibility(
                    visible = todayPressure != null &&
                        (pressureTrendValue == PressureTrend.FALLING || todayPressure < 998f),
                    enter = fadeIn(animationSpec = tween(400)) +
                        expandVertically(animationSpec = tween(400), expandFrom = Alignment.Top),
                    exit = fadeOut(animationSpec = tween(200)) +
                        shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Top),
                ) {
                    if (todayPressure != null) {
                        PressureInsightMessage(pressure = todayPressure, trend = pressureTrendValue)
                    }
                }

                AnimatedVisibility(
                    visible = uiState.steps == 0 && !uiState.isLoadingSteps,
                    enter = fadeIn(animationSpec = tween(350)) +
                        expandVertically(animationSpec = tween(350), expandFrom = Alignment.Top),
                    exit = fadeOut(animationSpec = tween(250)) +
                        shrinkVertically(animationSpec = tween(250), shrinkTowards = Alignment.Top),
                ) {
                    when {
                        // センサーが使えるが ACTIVITY_RECOGNITION が未許可
                        uiState.sensorAvailable && !uiState.activityRecognitionGranted -> {
                            HealthConnectGuidanceCard(
                                title = "歩数センサーの許可が必要です",
                                message = "歩数センサーを使うには「身体活動の認識」の許可が必要です。",
                                primaryLabel = "歩数センサーを許可する",
                                onPrimary = {
                                    requestActivityRecognition.launch(
                                        Manifest.permission.ACTIVITY_RECOGNITION
                                    )
                                },
                            )
                        }
                        // センサーなし・HC も設定されていない
                        !uiState.sensorAvailable && uiState.healthConnectAvailable && !uiState.healthConnectPermissionGranted -> {
                            HealthConnectGuidanceCard(
                                title = "歩数の取得が許可されていません",
                                message = "Health Connect への接続を許可すると歩数が自動で記録されます。",
                                primaryLabel = "Health Connect に接続する",
                                onPrimary = { openHealthConnect() },
                            )
                        }
                        !uiState.sensorAvailable && uiState.healthConnectAvailable && uiState.healthConnectPermissionGranted -> {
                            HealthConnectGuidanceCard(
                                title = "歩数データが見つかりません",
                                message = "Google Fit などのアプリを Health Connect に連携するとデータが取得できます。\n\n" +
                                    "① Health Connect を開く\n" +
                                    "② 「アプリとデータ」→「アプリの接続」\n" +
                                    "③ Google Fit を選んで許可",
                                primaryLabel = "Health Connect を開く",
                                onPrimary = { openHealthConnect() },
                                secondaryLabel = "更新",
                                onSecondary = { viewModel.refreshSteps() },
                            )
                        }
                    }
                }

                // HC は接続済みだが HC の歩数が 0 → Google Fit 等が HC に連携されていない可能性
                val showHcSyncGuidance = !uiState.isLoadingSteps &&
                    uiState.healthConnectPermissionGranted &&
                    uiState.lastHcSteps == 0 &&
                    uiState.sensorAvailable &&
                    uiState.activityRecognitionGranted &&
                    uiState.steps < 500
                AnimatedVisibility(
                    visible = showHcSyncGuidance,
                    enter = fadeIn(animationSpec = tween(350)) +
                        expandVertically(animationSpec = tween(350), expandFrom = Alignment.Top),
                    exit = fadeOut(animationSpec = tween(250)) +
                        shrinkVertically(animationSpec = tween(250), shrinkTowards = Alignment.Top),
                ) {
                    HealthConnectGuidanceCard(
                        title = "歩数アプリのデータが Health Connect に届いていません",
                        message = "Health Connect から取得した本日の歩数は 0 歩でした。" +
                            "そのため、アプリ起動前に歩いた分が反映されず、起動後の差分だけ（現在 ${uiState.steps} 歩）になっています。\n\n" +
                            "Google Fit の歩数を反映するには、Google Fit 側で同期を有効にする必要があります：\n" +
                            "① Google Fit を開く\n" +
                            "②「プロフィール」→ 設定（⚙）\n" +
                            "③「Google Fit のデータを Health Connect と同期」をオン\n\n" +
                            "OPPO ヘルス（HeyTap）を使っている場合も、同様に Health Connect への接続を許可してください。",
                        primaryLabel = "Health Connect を開く",
                        onPrimary = { openHealthConnect() },
                        secondaryLabel = "更新",
                        onSecondary = { viewModel.refreshSteps() },
                    )
                }

                AnimatedVisibility(
                    visible = bodyFormVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(animationSpec = tween(380)),
                ) {
                    BodyConditionInput(
                        initialDizziness = uiState.record?.dizzinessLevel,
                        initialFatigue = uiState.record?.fatigueLevel,
                        initialTinnitus = uiState.record?.tinnitusLevel,
                        initialSleepHours = uiState.record?.sleepHours ?: 7f,
                        initialWeightKg = uiState.record?.weightKg,
                        initialMemo = uiState.record?.memo ?: "",
                        isSaving = uiState.isSaving,
                        onSave = { dizziness, fatigue, tinnitus, sleep, weightKg, memo ->
                            viewModel.saveBodyCondition(dizziness, fatigue, tinnitus, sleep, weightKg, memo)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private enum class PressureTrend { RISING, FALLING, STABLE }

private fun pressureTrend(today: Float, yesterday: Float?): PressureTrend? {
    if (yesterday == null) return null
    val delta = today - yesterday
    return when {
        delta > 2f -> PressureTrend.RISING
        delta < -2f -> PressureTrend.FALLING
        else -> PressureTrend.STABLE
    }
}

@Composable
private fun PressureChip(pressure: Float, trend: PressureTrend?, precipitationMm: Float?) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Air,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = "%.1f hPa".format(pressure),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (trend != null) {
                    val (symbol, label) = when (trend) {
                        PressureTrend.RISING -> "↑" to "昨日より上昇"
                        PressureTrend.FALLING -> "↓" to "昨日より下降"
                        PressureTrend.STABLE -> "→" to "昨日とほぼ同じ"
                    }
                    Text(
                        text = "$symbol $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 気圧と同じ高さで降水量も表示（取得済みのときのみ）
            if (precipitationMm != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = "降水量",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (precipitationMm > 0f) "%.1f mm".format(precipitationMm) else "降水なし",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureInsightMessage(pressure: Float, trend: PressureTrend?) {
    val message = when {
        trend == PressureTrend.FALLING && pressure < 1000f ->
            "気圧が急激に下がっています。体調に変化があれば記録してみましょう。"
        trend == PressureTrend.FALLING ->
            "気圧が下がり気味です。体調の変化を気にしながら過ごしてみましょう。"
        pressure < 998f ->
            "低気圧が続いています。体調の変化を記録しておくと、あとで傾向が見えます。"
        else -> return
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.6f,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HealthConnectGuidanceCard(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text(primaryLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}
