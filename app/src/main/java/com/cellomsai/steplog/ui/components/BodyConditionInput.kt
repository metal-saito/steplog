package com.cellomsai.steplog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cellomsai.steplog.R
import com.cellomsai.steplog.ui.theme.StepLogTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyConditionInput(
    initialDizziness: Int?,
    initialFatigue: Int?,
    initialTinnitus: Int?,
    initialSleepHours: Float,
    initialWeightKg: Float?,
    initialMemo: String,
    isSaving: Boolean,
    onSave: (dizziness: Int?, fatigue: Int?, tinnitus: Int?, sleepHours: Float?, weightKg: Float?, memo: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val conditionLabels = stringArrayResource(R.array.condition_labels)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var dizziness by rememberSaveable(initialDizziness) { mutableStateOf(initialDizziness) }
    var fatigue by rememberSaveable(initialFatigue) { mutableStateOf(initialFatigue) }
    var tinnitus by rememberSaveable(initialTinnitus) { mutableStateOf(initialTinnitus) }
    var sleepHours by rememberSaveable(initialSleepHours) { mutableFloatStateOf(initialSleepHours) }
    var weightText by rememberSaveable(initialWeightKg) {
        mutableStateOf(initialWeightKg?.let { "%.1f".format(it) } ?: "")
    }
    var memo by rememberSaveable(initialMemo) { mutableStateOf(initialMemo) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "体調の記録",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            ConditionSegment(
                label = "めまい度",
                selected = dizziness,
                labels = conditionLabels,
                onSelect = { dizziness = it },
            )

            ConditionSegment(
                label = "疲労度",
                selected = fatigue,
                labels = conditionLabels,
                onSelect = { fatigue = it },
            )

            ConditionSegment(
                label = "耳鳴り",
                selected = tinnitus,
                labels = conditionLabels,
                onSelect = { tinnitus = it },
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "睡眠時間",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "%.1f 時間".format(sleepHours),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = sleepHours,
                    onValueChange = { sleepHours = it },
                    valueRange = 0f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { input ->
                    // allow only digits and one decimal point, max 3 digits before decimal
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) weightText = filtered
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("体重（kg）") },
                placeholder = { Text("例：65.0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("メモ（任意）") },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            Button(
                onClick = {
                    // 保存メッセージ（Snackbar）が隠れないようキーボードを閉じてフォーカスを外す
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onSave(dizziness, fatigue, tinnitus, sleepHours, weightText.toFloatOrNull(), memo.ifBlank { null })
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("記録する")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionSegment(
    label: String,
    selected: Int?,
    labels: Array<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, labelText ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    label = { Text(text = labelText, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BodyConditionInputPreview() {
    StepLogTheme {
        BodyConditionInput(
            initialDizziness = null,
            initialFatigue = null,
            initialTinnitus = null,
            initialSleepHours = 7f,
            initialWeightKg = null,
            initialMemo = "",
            isSaving = false,
            onSave = { _, _, _, _, _, _ -> },
        )
    }
}
