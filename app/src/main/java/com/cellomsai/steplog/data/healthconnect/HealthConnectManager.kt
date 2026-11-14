package com.cellomsai.steplog.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val writePermission = HealthPermission.getWritePermission(StepsRecord::class)
    private val sleepReadPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)

    // 権限リクエスト時は歩数の読み書き＋睡眠の読み取りを要求する（睡眠は任意。拒否されても歩数は使える）
    val permissions = setOf(readPermission, writePermission, sleepReadPermission)

    /** 睡眠の「読み取り」権限があるか。無ければ睡眠時間は手入力のまま。 */
    suspend fun hasSleepPermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            client().permissionController.getGrantedPermissions().contains(sleepReadPermission)
        }.getOrDefault(false)
    }

    /**
     * 指定日の睡眠時間（時間、0.5 刻み）を返す。
     *
     * その日に目覚めた睡眠セッション（終了時刻が当日 0:00〜翌 0:00）を対象に、
     * 覚醒・離床ステージを除いた「眠っていた区間」を求め、複数アプリが同じ夜を
     * 書いている場合の二重計上を防ぐため区間を合成（和集合）して合計する。
     *
     * 権限なし・データなし・取得失敗のいずれでも例外を出さず null を返す（＝手入力のまま）。
     */
    suspend fun readSleepHours(date: LocalDate): Float? {
        if (!isAvailable() || !hasSleepPermission()) return null
        return runCatching {
            val zone = ZoneId.systemDefault()
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            // 前夜からの睡眠を拾うため前日正午から読み、終了が当日のセッションだけ採用
            val queryStart = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
            val sessions = client().readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(queryStart, dayEnd),
                )
            ).records.filter { !it.endTime.isBefore(dayStart) && it.endTime.isBefore(dayEnd) }
            if (sessions.isEmpty()) return@runCatching null

            val minutes = mergedMinutes(sessions.flatMap { asleepIntervals(it) })
            if (minutes <= 0) return@runCatching null
            // 入力スライダー（0〜12 時間・0.5 刻み）に合わせて丸める
            val halfHours = (minutes / 30.0).roundToInt()
            (halfHours / 2f).coerceIn(0f, 12f)
        }.getOrNull()
    }

    /** セッションから覚醒・離床ステージを除いた区間のリスト。 */
    private fun asleepIntervals(session: SleepSessionRecord): List<Pair<Instant, Instant>> {
        val awakeTypes = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )
        val awake = session.stages
            .filter { it.stage in awakeTypes }
            .sortedBy { it.startTime }
        val result = mutableListOf<Pair<Instant, Instant>>()
        var cursor = session.startTime
        for (stage in awake) {
            val start = maxOf(stage.startTime, session.startTime)
            val end = minOf(stage.endTime, session.endTime)
            if (start > cursor) result += cursor to start
            if (end > cursor) cursor = end
        }
        if (session.endTime > cursor) result += cursor to session.endTime
        return result
    }

    /** 重なりを統合した区間の合計分数。 */
    private fun mergedMinutes(intervals: List<Pair<Instant, Instant>>): Long {
        var total = 0L
        var currentStart: Instant? = null
        var currentEnd: Instant? = null
        for ((start, end) in intervals.sortedBy { it.first }) {
            if (currentEnd == null || start > currentEnd) {
                if (currentStart != null) total += Duration.between(currentStart, currentEnd).toMinutes()
                currentStart = start
                currentEnd = end
            } else if (end > currentEnd) {
                currentEnd = end
            }
        }
        if (currentStart != null && currentEnd != null) {
            total += Duration.between(currentStart, currentEnd).toMinutes()
        }
        return total
    }

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
