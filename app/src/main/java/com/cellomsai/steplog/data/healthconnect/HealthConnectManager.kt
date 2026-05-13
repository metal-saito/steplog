package com.cellomsai.steplog.data.healthconnect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * HC の実際のパッケージ名を動的に解決して権限リクエストインテントを返す。
     * null の場合は HC が権限画面を提供していない。
     */
    fun buildPermissionRequestIntent(): Intent? {
        val probe = Intent("androidx.health.ACTION_REQUEST_PERMISSIONS")
        val matches = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
        val pkg = matches.firstOrNull()?.activityInfo?.packageName ?: return null
        return Intent("androidx.health.ACTION_REQUEST_PERMISSIONS").apply {
            setPackage(pkg)
            putExtra("androidx.health.extra.permissions", permissions.toTypedArray())
        }
    }

    fun sdkStatusLabel(): String = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
        HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "UPDATE_REQUIRED"
        else -> "UNKNOWN"
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
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
