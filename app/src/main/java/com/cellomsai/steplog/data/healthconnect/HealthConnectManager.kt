package com.cellomsai.steplog.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val writePermission = HealthPermission.getWritePermission(StepsRecord::class)

    // 権限リクエスト時は読み書き両方を要求するが、表示の主目的は「読み取り」
    val permissions = setOf(readPermission, writePermission)

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    /**
     * 歩数の「読み取り」権限があるか。
     * 以前は読み書き両方を必須にしていたため、ユーザーが読み取りだけ許可しても
     * 連携なしと判定され HC を読みに行かない不具合があった。歩数表示には読み取りで十分。
     */
    suspend fun hasReadPermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            client().permissionController.getGrantedPermissions().contains(readPermission)
        }.getOrDefault(false)
    }

    suspend fun hasWritePermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            client().permissionController.getGrantedPermissions().contains(writePermission)
        }.getOrDefault(false)
    }

    // 歩数表示の可否は読み取り権限で判定する
    suspend fun hasPermissions(): Boolean = hasReadPermission()

    /**
     * 当日の歩数を Health Connect に書き込む。
     * 自アプリが過去に書いた当日の記録を削除してから 1 件挿入し、二重計上を防ぐ。
     * （HC ではアプリは自分が書いた記録のみ削除できる）
     */
    suspend fun writeSteps(date: LocalDate, count: Int) {
        if (count <= 0 || !isAvailable()) return
        if (!hasWritePermission()) return

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

    /**
     * 当日の合計歩数を返す。集計 API（COUNT_TOTAL）を使い、複数のデータソース
     * （Google Fit / OPPO ヘルス等）が書いた記録の重複を自動で排除する。
     * 集計が失敗した場合は生レコードの単純合計にフォールバックする。
     */
    suspend fun readSteps(date: LocalDate): Int {
        if (!isAvailable()) return 0
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(start, end)

        return runCatching {
            val response = client().aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = filter,
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        }.getOrElse {
            // 集計に失敗した端末向けフォールバック（生レコードの合計）
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = filter,
            )
            client().readRecords(request).records.sumOf { it.count }.toInt()
        }
    }

    /**
     * 期間内の日別合計歩数を返す。日単位の集計（COUNT_TOTAL）で重複排除する。
     */
    suspend fun readStepsForRange(from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        if (!isAvailable()) return emptyMap()
        return runCatching {
            val response = client().aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            response.mapNotNull { bucket ->
                val count = bucket.result[StepsRecord.COUNT_TOTAL]?.toInt()
                if (count != null) bucket.startTime.toLocalDate() to count else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
