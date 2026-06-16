package com.cellomsai.steplog.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellomsai.steplog.ui.components.BodyConditionInput
import com.cellomsai.steplog.ui.components.StepsDisplay
import com.cellomsai.steplog.ui.theme.StepLogTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    date: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(date) { viewModel.load(date) }

    LaunchedEffect(uiState.savedToastVisible) {
        if (uiState.savedToastVisible) {
            snackbarHostState.showSnackbar("記録しました")
            viewModel.dismissToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.displayDate, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepsDisplay(steps = uiState.record?.steps ?: 0)

            BodyConditionInput(
                initialDizziness = uiState.record?.dizzinessLevel,
                initialFatigue = uiState.record?.fatigueLevel,
                initialTinnitus = uiState.record?.tinnitusLevel,
                initialSleepHours = uiState.record?.sleepHours ?: 7f,
                initialWeightKg = uiState.record?.weightKg,
                initialMemo = uiState.record?.memo ?: "",
                isSaving = uiState.isSaving,
                onSave = { dizziness, fatigue, tinnitus, sleep, weightKg, memo ->
                    viewModel.save(dizziness, fatigue, tinnitus, sleep, weightKg, memo)
                },
            )

            uiState.record?.pressure?.let { pressure ->
                Text(
                    text = "気圧 %.1f hPa".format(pressure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.record?.precipitationMm?.let { mm ->
                Text(
                    text = if (mm > 0f) "降水量 %.1f mm".format(mm) else "降水量 なし",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailScreenPreview() {
    StepLogTheme {
        Text("Detail Preview")
    }
}
