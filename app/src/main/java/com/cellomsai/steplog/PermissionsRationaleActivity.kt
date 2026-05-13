package com.cellomsai.steplog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cellomsai.steplog.ui.theme.StepLogTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StepLogTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "StepLog が歩数を使う理由",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "StepLog はメニエール病などの体調管理のために、Health Connect 経由で歩数データを記録します。\n\n" +
                                "• 取得するデータ：歩数（読み取りのみ）\n" +
                                "• データはこの端末にのみ保存されます\n" +
                                "• 外部サーバーへの送信は行いません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { finish() }) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }
}
