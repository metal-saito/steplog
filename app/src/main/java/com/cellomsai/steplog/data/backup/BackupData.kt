package com.cellomsai.steplog.data.backup

import com.cellomsai.steplog.data.database.entity.DailyRecord

/**
 * 機種変更などでデータを移行するためのバックアップ形式（JSON）。
 * 全レコードを作成日時・更新日時ごとそのまま保持する。
 *
 * @param version バックアップ形式のバージョン（将来の互換判定用）
 * @param exportedAt 書き出し時刻（epoch millis）
 * @param records 全 [DailyRecord]
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val records: List<DailyRecord> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
