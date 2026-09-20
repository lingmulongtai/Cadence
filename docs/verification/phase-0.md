# フェーズ0の検証

2026-09-20、Windows上のJDK 17.0.19、Gradle 9.4.1、AGP 9.2.1、Kotlin 2.3.21。
SDKはPlatform 37.0 / Build Tools 36.0.0。`minSdk=26` / `targetSdk=37`。
公式資料: [Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)、
[AGP 9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes)。

## 実行済み

- `:app:assembleDebug :app:lintDebug` — 成功
- `:motion:test :app:assembleRelease :app:lintRelease` — 成功
- フェーズ0の `:motion:test` は `NO-SOURCE`。テスト実装の合格件数ではない。
- debug / release APKの `aapt2 dump permissions` — `INTERNET` / `HIGH_SAMPLING_RATE_SENSORS` なし
- GitHub ActionsのYAMLをパース。ワークフロー自体のGitHub上での実行は未実施
- 専用AVD `Cadence_API_36`（Android 16 / API 36.1）へdebug APKをインストール
- `am start -W -n dev.lingmulongtai.cadence/.MainActivity` — `Status: ok`
- 起動画面を目視確認: [phase-0.png](phase-0.png)

日本語を含むパスはAGPの検査で拒否されたため、同一作業ツリーへの
ASCIIパスのjunction `C:\cadence-native-build` からビルドした。
端末エミュレーターの初期起動時にSystem UIのANRが発生したが、起動完了後に
画面を720×1600へ変更して再確認した。これはアプリの性能測定には使わない。

## 未検証

Galaxy S25 Ultra実機、Android 8実機、センサー精度、車両判定、オーバーレイ、
自動起動、CPU、電池消費、医療的効果、F-Droidビルド再現性。
空アプリの起動確認をこれらの受け入れ試験に代用しない。
