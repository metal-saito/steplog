package com.cellomsai.steplog.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cellomsai.steplog.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class StepSensorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // readTodaySteps の read-modify-write を直列化し、二重加算を防ぐ
    private val mutex = Mutex()

    val isAvailable: Boolean get() = stepSensor != null

    private suspend fun readCurrentCount(): Long? = withTimeoutOrNull(3000L) {
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    sensorManager?.unregisterListener(this)
                    if (cont.isActive) cont.resume(event.values[0].toLong())
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            val ok = sensorManager?.registerListener(
                listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL
            ) == true
            if (!ok && cont.isActive) cont.resume(null)
            cont.invokeOnCancellation { sensorManager?.unregisterListener(listener) }
        }
    }

    /**
     * 当日の累計歩数を返す。
     *
     * TYPE_STEP_COUNTER は端末起動からの累計を返すため、差分を蓄積して当日歩数を出す。
     * - 通常時: current >= lastSensor → 差分 (current - lastSensor) を加算
     * - 再起動時: current < lastSensor → センサーが 0 にリセットされたので current 自体を加算
     * これにより端末再起動・アプリ終了をまたいでも、記録済みの当日歩数が減ることはない。
     *
     * 読み取りに失敗した場合は保存済みの当日累計を返す（0 で上書きしない）。
     */
    suspend fun readTodaySteps(): Int = mutex.withLock {
        val today = LocalDate.now().toString()
        val state = userPreferences.getStepState()
        val current = readCurrentCount()
            ?: return@withLock if (state.date == today) state.dailyTotal else 0

        if (state.date != today) {
            // 新しい日：当日の起点を記録して 0 から開始
            userPreferences.setStepState(today, current, 0)
            return@withLock 0
        }

        val delta = if (current >= state.lastSensor) {
            current - state.lastSensor
        } else {
            // 再起動でセンサーがリセットされた：current が再起動後の歩数
            current
        }
        val newTotal = state.dailyTotal + delta.toInt()
        userPreferences.setStepState(today, current, newTotal)
        newTotal
    }
}
