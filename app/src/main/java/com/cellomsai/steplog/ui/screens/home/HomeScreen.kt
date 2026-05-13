package com.cellomsai.steplog.ui.screens.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cellomsai.steplog.ui.components.BodyConditionInput
import com.cellomsai.steplog.ui.components.StepsDisplay
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // Health Connect パーミッション要求ランチャー
    val requestPermissions = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    LaunchedEffect(uiState.launchPermissionRequest) {
        if (uiState.launchPermissionRequest) {
            requestPermissions.launch(viewModel.healthConnect.permissions)
            viewModel.onPermissionRequestLaunched()
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!uiState.healthConnectAvailable) {
                    Text(
                        text = "この端末では歩数の自動取得ができません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                StepsDisplay(steps = uiState.steps)

                if (uiState.healthConnectAvailable && uiState.steps == 0 && !uiState.isLoadingSteps) {
                    if (!uiState.healthConnectPermissionGranted) {
                        // 権限未付与 → 権限リクエストを直接発行
                        HealthConnectGuidanceCard(
                            title = "歩数の取得が許可されていません",
                            message = "Health Connect への接続を許可すると歩数が自動で記録されます。",
                            primaryLabel = "Health Connect に接続する",
                            onPrimary = { viewModel.requestPermissions() },
                        )
                    } else {
                        // 権限あり・歩数 0 → データソース未連携
                        HealthConnectGuidanceCard(
                            title = "歩数データが見つかりません",
                            message = "Google Fit などのアプリを Health Connect に連携するとデータが取得できます。\n\n" +
                                "① Health Connect を開く\n" +
                                "② 「アプリとデータ」→「アプリの接続」\n" +
                                "③ Google Fit を選んで許可",
                            primaryLabel = "Health Connect を開く",
                            onPrimary = {
                                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                runCatching { context.startActivity(intent) }
                            },
                            secondaryLabel = "更新",
                            onSecondary = { viewModel.refreshSteps() },
                        )
                    }
                }

                BodyConditionInput(
                    initialDizziness = uiState.record?.dizzinessLevel,
                    initialFatigue = uiState.record?.fatigueLevel,
                    initialSleepHours = uiState.record?.sleepHours ?: 7f,
                    initialMemo = uiState.record?.memo ?: "",
                    isSaving = uiState.isSaving,
                    onSave = { dizziness, fatigue, sleep, memo ->
                        viewModel.saveBodyCondition(dizziness, fatigue, sleep, memo)
                    },
                )

                uiState.record?.pressure?.let { pressure ->
                    Text(
                        text = "気圧 %.1f hPa".format(pressure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
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
