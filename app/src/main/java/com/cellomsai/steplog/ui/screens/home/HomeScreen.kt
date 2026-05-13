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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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

    // Health Connect から戻ったときに歩数を再取得
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSteps()
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

    // パーミッション説明ダイアログ
    if (uiState.showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionRationale() },
            title = { Text("歩数の自動取得") },
            text = {
                Text(
                    "歩数を自動で記録するために、Health Connect へのアクセスを許可してください。" +
                        "\n\nデータはこの端末にのみ保存されます。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPermissionRationale()
                    requestPermissions.launch(viewModel.healthConnect.permissions)
                }) {
                    Text("許可する")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionRationale() }) {
                    Text("あとで", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
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
                                text = "歩数が取得できていません",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Google Fit などの歩数アプリと Health Connect を連携すると自動で記録されます。\n\n" +
                                    "① Health Connect を開く\n" +
                                    "② 「アプリとデータ」→「アプリの接続」\n" +
                                    "③ Google Fit を選び、このアプリへの提供を許可",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                        runCatching { context.startActivity(intent) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                    ),
                                ) {
                                    Text("Health Connect を開く")
                                }
                                OutlinedButton(onClick = { viewModel.refreshSteps() }) {
                                    Text("更新")
                                }
                            }
                        }
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
