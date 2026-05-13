package com.cellomsai.steplog.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cellomsai.steplog.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
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

    suspend fun readTodaySteps(): Int {
        val current = readCurrentCount() ?: return 0
        val today = LocalDate.now().toString()
        val (baseDate, baseCount) = userPreferences.getStepBaseline()

        return if (baseDate == today && current >= baseCount) {
            (current - baseCount).toInt()
        } else {
            // 新しい日、またはデバイスが再起動されてカウンタがリセットされた
            userPreferences.setStepBaseline(today, current)
            0
        }
    }
}
