# StepLog

歩数と体調を静かに記録するAndroidアプリです。  
メニエール病など、体調の波と向き合う方のための「記録だけする」アプリです。目標も励ましもありません。

## 機能（Phase 1）

- **今日の歩数** — Health Connect から自動取得（プルして更新）
- **体調記録** — めまい度・疲労度（6段階）、睡眠時間、メモ
- **カレンダー** — 月ごとの歩数と体調をひと目で確認
- **詳細画面** — 過去の日付の記録を編集

## 技術スタック

| 分類 | 採用技術 |
|------|----------|
| 言語 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| アーキテクチャ | MVVM + Repository |
| DI | Hilt 2.52 |
| DB | Room 2.7.0 |
| 歩数取得 | Health Connect 1.1.0-rc01 |
| 設定保存 | DataStore 1.1.1 |
| ナビゲーション | Navigation Compose 2.8.4 |

## 動作環境

- Android 8.0（API 26）以上
- Health Connect アプリ（歩数自動取得に必要）

---

## APKビルド手順

### 必要なもの

| ツール | バージョン | 備考 |
|--------|-----------|------|
| JDK | 17以上 | Microsoft OpenJDK 推奨 |
| Android SDK | API 36 | command line tools のみでも可 |
| Git | 任意 | |

### 1. JDK 17 のインストール（Windows）

PowerShell を管理者として実行：

```powershell
winget install Microsoft.OpenJDK.17
```

確認：

```powershell
java -version
# openjdk version "17.x.x" ...
```

### 2. Android SDK のインストール

1. [Command line tools only](https://developer.android.com/studio#command-line-tools-only) の Windows 版をダウンロード
2. `C:\Android\cmdline-tools\latest\` に展開

環境変数を設定（PowerShell 管理者）：

```powershell
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android", "Machine")
[System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Android\cmdline-tools\latest\bin;C:\Android\platform-tools", "Machine")
```

ターミナルを再起動後、SDKコンポーネントをインストール：

```powershell
sdkmanager --licenses        # すべて y で承認
sdkmanager "platform-tools" "platforms;android-36" "build-tools;34.0.0"
```

### 3. リポジトリをクローン

```powershell
git clone https://github.com/metal-saito/steplog.git
cd steplog
```

### 4. APK をビルド

```powershell
.\gradlew.bat assembleDebug
```

初回は Gradle・依存ライブラリのダウンロードで数分かかります。

**ビルド成功時の出力：**

```
BUILD SUCCESSFUL in X m Xs
```

**APK の出力先：**

```
app\build\outputs\apk\debug\app-debug.apk
```

---

## 端末へのインストール

### 方法A：ADB（USB接続）

端末で USBデバッグを有効にした後：

```powershell
adb devices                  # 端末が表示されることを確認
adb install app\build\outputs\apk\debug\app-debug.apk
```

### 方法B：ファイル転送（ADB不要）

1. Google ドライブ・メール等で `app-debug.apk` を端末に転送
2. 端末側で **設定 → 追加設定 → セキュリティ → 提供元不明のアプリ** を許可
3. ファイルマネージャーで APK をタップしてインストール

---

## Health Connect の権限について

初回起動時、Health Connect の権限ダイアログが表示されます。  
**「許可」** を選ぶと歩数の自動取得が有効になります。

端末に Health Connect アプリがない場合、歩数の自動取得はできませんが、体調記録は利用できます。

---

## ロードマップ

| フェーズ | 内容 | 状態 |
|---------|------|------|
| Phase 1 | 歩数記録・体調記録・カレンダー | ✅ 完了 |
| Phase 2 | グラフ・気圧連携・CSVエクスポート・設定画面 | 予定 |
| Phase 3 | 相関分析・ウィジェット・クラウドバックアップ | 予定 |

---

## ライセンス

Private
