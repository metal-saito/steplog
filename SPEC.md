# StepLog — 機能仕様書

> バージョン: 0.1.0  
> 対象 OS: Android 8.0（API 26）以上  
> 設計思想: 「歩け」と言わないアプリ。メニエール病患者が無理なく続けられる、記録のためだけの日誌。

---

## 目次

1. [画面構成](#1-画面構成)
2. [機能仕様](#2-機能仕様)
3. [データ仕様](#3-データ仕様)
4. [技術スタック](#4-技術スタック)
5. [アーキテクチャ](#5-アーキテクチャ)
6. [権限](#6-権限)
7. [テーマ・デザイン](#7-テーマデザイン)
8. [外部連携](#8-外部連携)
9. [ビルド](#9-ビルド)

---

## 1. 画面構成

```
BottomNavigationBar
├── 今日 (HomeScreen)
├── カレンダー (CalendarScreen)
├── グラフ (GraphScreen)
└── 設定 (SettingsScreen)
```

---

## 2. 機能仕様

### 2.1 今日画面 (HomeScreen)

#### 歩数表示
- 画面中央に本日の歩数を大きく表示
- プルダウン（PullToRefresh）で手動更新可能
- 取得中はローディングインジケーター表示

#### 気圧表示
- TopAppBar 右端: `⇌ 1006 hPa`（スクロールしても常に表示）
- 歩数直下: `⇌ 気圧 1006.0 hPa` チップ（surfaceVariant カード）
- 気圧未取得時はどちらも非表示

#### 体調入力フォーム
| 項目 | 型 | 範囲 | 備考 |
|------|----|------|------|
| めまい度 | Int | 0〜5 | 穏やか/少し/やや/気になる/つらい/とてもつらい |
| 疲労度 | Int | 0〜5 | 同上 |
| 睡眠時間 | Float | 0.0〜12.0 h | 0.5h 刻みスライダー |
| メモ | String | 任意 | フリーテキスト |

- 「記録する」ボタンで保存、Snackbar で「記録しました」表示
- 記録済みの日は前回値をフォームに復元

#### 権限ガイダンスカード
| 条件 | 表示内容 |
|------|----------|
| センサーあり・ACTIVITY_RECOGNITION 未許可 | 「歩数センサーを許可する」ボタン |
| センサーなし・Health Connect 未接続 | 「Health Connect に接続する」ボタン |
| センサーなし・HC 接続済みだが歩数 0 | HC のデータソース設定手順を案内 |

---

### 2.2 カレンダー画面 (CalendarScreen)

- 月次カレンダー表示
- 各日のセルにめまい度・疲労度を色で可視化（ConditionColors: 緑→橙→赤）
- タップで詳細画面へ遷移

#### 詳細画面 (DetailScreen)
- 選択日の全データ表示（歩数・各体調スコア・睡眠時間・気圧・メモ）
- 気圧が記録されていれば `気圧 XXXX.X hPa` を表示

---

### 2.3 グラフ画面 (GraphScreen)

- 期間選択: 7日 / 30日 / 90日
- サマリーカード: 平均歩数・体調記録日数
- 歩数棒グラフ（Vico Charts）
- めまい度・疲労度の水平バーチャート

---

### 2.4 設定画面 (SettingsScreen)

#### 外観
- テーマ切替: システム / ライト / ダーク（SegmentedButton）
- DataStore に永続化

#### データ
- **CSV を共有する**: 全記録を CSV 形式で書き出し、Android 共有シートを開く
  - 出力先: キャッシュディレクトリ（FileProvider 経由）
  - ファイル名: `steplog_YYYY-MM-DD.csv`
  - カラム: `date, steps, dizziness_level, fatigue_level, sleep_hours, pressure, memo`
- **データをすべて削除**: 確認ダイアログ付き

#### 気圧データ（任意）
- OpenWeatherMap API キーの登録・変更
- デフォルトキーがプリセット済みのためそのまま利用可能
- 未入力でも歩数・体調記録は通常通り動作

#### 情報
- アプリの理念を表示

---

## 3. データ仕様

### 3.1 DB エンティティ（Room）

**テーブル: `daily_records`**

| カラム | 型 | デフォルト | 説明 |
|--------|----|-----------|------|
| `date` | TEXT (PK) | — | `yyyy-MM-dd` |
| `steps` | INT | 0 | 当日の歩数 |
| `dizzinessLevel` | INT? | null | 0〜5, null=未入力 |
| `fatigueLevel` | INT? | null | 0〜5, null=未入力 |
| `sleepHours` | REAL? | null | 0.0〜12.0 |
| `pressure` | REAL? | null | 気圧 (hPa) |
| `memo` | TEXT? | null | フリーテキスト |
| `createdAt` | INT | now | Unix ミリ秒 |
| `updatedAt` | INT | now | Unix ミリ秒 |

### 3.2 DataStore (UserPreferences)

| キー | 型 | デフォルト | 説明 |
|------|----|-----------|------|
| `theme` | String | `"SYSTEM"` | `SYSTEM` / `LIGHT` / `DARK` |
| `weather_api_key` | String | プリセット済み | OpenWeatherMap API キー |
| `step_date` | String | `""` | 歩数計測中の日付 |
| `step_last_sensor` | Long | 0 | 最後に処理した累計センサー値（再起動検知用） |
| `step_daily_total` | Int | 0 | 当日のここまでの累計歩数 |

### 3.3 歩数の差分蓄積方式（再起動・終了に強い）

`TYPE_STEP_COUNTER` センサーは**端末起動からの累計値**を返し、**端末再起動で 0 にリセット**される。
そのため「最後に見たセンサー値」と「当日累計」を保持し、差分を加算していく方式を採用する。

```
読み取りごと (readTodaySteps):
1. 日付が変わった（state.date != today）:
   → state = (today, current, 0), 返値 = 0  （新しい日の起点）

2. 通常時（current >= lastSensor）:
   → delta = current - lastSensor
   → dailyTotal += delta
   → state = (today, current, dailyTotal), 返値 = dailyTotal

3. 再起動検知（current < lastSensor）:
   → delta = current  （current 自体が再起動後の歩数）
   → dailyTotal += delta   ← リセットせず加算
   → state = (today, current, dailyTotal), 返値 = dailyTotal

4. センサー読み取り失敗:
   → 当日なら保存済み dailyTotal を返す（0 で上書きしない）
```

- **当日累計は減らない**ため、アプリ終了・端末再起動をまたいでも記録済み歩数が消えない。
- read-modify-write は `Mutex` で直列化し、並列呼び出しによる二重加算を防止。
- `refreshSteps()` は読み取り失敗（null）時に DB を保存しない（0 上書き防止）。

**既知の制約:** アプリを完全終了したまま歩き、その途中で端末を再起動した場合、
「最後の読み取り〜再起動」間の歩数は復元できない（センサーがリセットされ、
バックグラウンドで読み取っていないため）。完全な常時計測が必要な場合は
Health Connect 連携または前面サービスが必要。

---

## 4. 技術スタック

| カテゴリ | ライブラリ / バージョン |
|----------|------------------------|
| 言語 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| ナビゲーション | Navigation Compose |
| DI | Hilt (Dagger Hilt) |
| DB | Room |
| 設定永続化 | DataStore Preferences |
| 非同期 | Kotlin Coroutines + Flow |
| グラフ | Vico Charts |
| HTTP | Retrofit 2 + Gson converter + OkHttp Logging |
| 歩数センサー | Android `TYPE_STEP_COUNTER` (ハードウェアセンサー) |
| 気圧 | OpenWeatherMap Current Weather API |
| 位置情報 | Android `LocationManager` (粗い精度) |
| 歩数バックアップ | Health Connect (フォールバック) |
| ビルドツール | AGP 8.9.1 / Gradle 8.11.1 |
| Java 互換 | Java 17 + Core Library Desugaring |
| compileSdk | 36 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 |

---

## 5. アーキテクチャ

```
UI Layer
├── HomeScreen        ← HomeViewModel
├── CalendarScreen    ← CalendarViewModel
├── GraphScreen       ← GraphViewModel
└── SettingsScreen    ← SettingsViewModel

Domain / Data Layer
├── DailyRecordRepository   ← DailyRecordDao (Room)
├── UserPreferences         ← DataStore
├── StepSensorManager       ← TYPE_STEP_COUNTER sensor
├── WeatherRepository       ← WeatherApiService (Retrofit)
└── HealthConnectManager    ← Health Connect SDK
```

- MVVM パターン
- `StateFlow<UiState>` で UI に状態を流す
- `@HiltViewModel` + `@Singleton` で依存注入
- 各 save 操作は **列単位の SQL UPDATE** (`updateSteps` / `updatePressure` / `updateBodyCondition`) を使い、並列実行時のレースコンディションを防止

---

## 6. 権限

| 権限 | 必須/任意 | 用途 |
|------|-----------|------|
| `ACTIVITY_RECOGNITION` | 必須 | 歩数センサー読み取り（Android 10+） |
| `ACCESS_COARSE_LOCATION` | 任意 | 気圧取得のための位置情報 |
| `INTERNET` | 必須 | OpenWeatherMap API 通信 |
| `health.READ_STEPS` | 任意 | Health Connect から歩数を読む |
| `health.WRITE_STEPS` | 任意 | Health Connect へ歩数を書き込む |
| `health.READ_TOTAL_CALORIES_BURNED` | 任意 | Health Connect 連携 |
| `POST_NOTIFICATIONS` | 任意 | 将来的な朝リマインダー用（未実装） |
| `RECEIVE_BOOT_COMPLETED` | 任意 | 将来的な WorkManager 用（未実装） |

---

## 7. テーマ・デザイン

### テーマ一覧

| 選択 | 背景色 | プライマリ | 用途 |
|------|--------|-----------|------|
| システム (端末ライト時) | クリーム `#FAF7F2` | セージグリーン `#8FA68E` | 温かみ・自然 |
| システム (端末ダーク時) | ダーク `#1A1A1A` | `#6D8A6C` | ダーク |
| ライト | 純白 `#FFFFFF` | スレートブルー `#7A9BAF` | クリーン・クール |
| ダーク | ダーク `#1A1A1A` | `#6D8A6C` | ダーク |

### 体調レベル配色（ConditionColors）

| レベル | 色 | 意味 |
|--------|-----|------|
| 0 | `#E3EDE0` | 穏やか（薄緑） |
| 1 | `#D0E0CC` | 少し |
| 2 | `#B8D0B2` | やや |
| 3 | `#EFD9B8` | 気になる（橙） |
| 4 | `#E5BFAF` | つらい |
| 5 | `#D6A5A0` | とてもつらい（赤） |

---

## 8. 外部連携

### OpenWeatherMap

- エンドポイント: `https://api.openweathermap.org/data/2.5/weather`
- パラメータ: `lat`, `lon`, `appid`
- 取得値: `main.pressure` (hPa)
- タイミング: アプリ起動時・onResume 時（当日未取得の場合のみ）
- 位置情報取得順:
  1. `getLastKnownLocation(NETWORK_PROVIDER)`
  2. `getLastKnownLocation(GPS_PROVIDER)`
  3. `getLastKnownLocation(PASSIVE_PROVIDER)`
  4. `requestSingleUpdate`（5 秒タイムアウト）

### Health Connect（書き込み＋読み込み）

- **連携方法**: 設定 → 「Health Connect 連携」→ `PermissionController.createRequestPermissionResultContract()` で正式に権限リクエスト。これにより StepLog が HC の接続アプリ一覧に登録される。
- **書き込み**: センサーで計測した当日歩数を、`refreshSteps()` のたびに HC へ書き込む（`writeSteps`）。
  - 自アプリが過去に書いた当日記録を `deleteRecords`（時間範囲）で削除してから 1 件挿入し、二重計上を防止（HC ではアプリは自分が書いた記録のみ削除可能）。
  - これにより Google Fit 等の書き込み元が無くても HC に歩数が必ず存在し、他のヘルスアプリと共有できる。
- **読み込み**: センサーが利用不可の端末では HC から歩数を読む（フォールバック）。
- **アプリ発見性**: `PermissionsRationaleActivity` を登録。
  - Android 13 以下: `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
  - Android 14 以上: `android.intent.action.VIEW_PERMISSION_USAGE` + `category.HEALTH_PERMISSIONS`
- **端末互換**: contract 起動に失敗する端末向けに、HC 設定画面（`ACTION_HEALTH_CONNECT_SETTINGS`）を開くフォールバックを用意。

---

## 9. ビルド

### デバッグ APK

```bash
# Windows
.\gradlew assembleDebug
# 出力先: app\build\outputs\apk\debug\app-debug.apk

# Linux / macOS
./gradlew assembleDebug
```

### リリース APK

```bash
.\gradlew assembleRelease
# 出力先: app\build\outputs\apk\release\app-release.apk
# ※ リリースビルドには署名設定が別途必要
```

### リポジトリ

- GitHub: `metal-saito/steplog` (main ブランチ)
- パッケージ名: `com.cellomsai.steplog`
