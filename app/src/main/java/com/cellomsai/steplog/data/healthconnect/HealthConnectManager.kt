package com.cellomsai.steplog.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
    }

    /**
     * 当日の歩数を Health Connect に書き込む。
     * 自アプリが過去に書いた当日の記録を削除してから 1 件挿入し、二重計上を防ぐ。
     * （HC ではアプリは自分が書いた記録のみ削除できる）
     */
    suspend fun writeSteps(date: LocalDate, count: Int) {
        if (count <= 0 || !isAvailable()) return
        if (!hasPermissions()) return

        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone)
        val end = if (date == LocalDate.now()) {
            ZonedDateTime.now(zone)
        } else {
            date.plusDays(1).atStartOfDay(zone)
        }
        if (!end.isAfter(start)) return

        runCatching {
            client().deleteRecords(
                StepsRecord::class,
                TimeRangeFilter.between(start.toInstant(), end.toInstant()),
            )
        }
        val record = StepsRecord(
            count = count.toLong(),
            startTime = start.toInstant(),
            startZoneOffset = start.offset,
            endTime = end.toInstant(),
            endZoneOffset = end.offset,
            metadata = Metadata.manualEntry(),
        )
        client().insertRecords(listOf(record))
    }

    suspend fun readSteps(date: LocalDate): Int {
        if (!isAvailable()) return 0
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        val response = client().readRecords(request)
        return response.records.sumOf { it.count }.toInt()
    }

    suspend fun readStepsForRange(from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        if (!isAvailable()) return emptyMap()
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        val response = client().readRecords(request)
        return response.records
            .groupBy { record ->
                record.startTime.atZone(zone).toLocalDate()
            }
            .mapValues { (_, records) -> records.sumOf { it.count }.toInt() }
    }
}
