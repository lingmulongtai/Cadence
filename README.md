# Cadence

**車両の加速度に応じた画面端の視覚キューで、移動中の画面閲覧を支えるAndroidアプリ。**

An Android app with peripheral visual cues that respond to vehicle acceleration.

[English](README.en.md) · [開発手順・段階](docs/development.md) · [残作業](todo.md)

## 現在の状態

Kotlinによるネイティブ版へ移行中です。
**[記録用プレビュー v0.1.0-alpha.1 をダウンロード](https://github.com/lingmulongtai/Cadence/releases/tag/v0.1.0-alpha.1)**。
スマホだけで記録し、保存先・名前を選んでZIPを書き出したり、共有したりできます。
このプリリリースにはモーションキュー・車両判定・手ぶれ除去は含まれません。
フェーズ0の土台・起動確認は完了しています。[検証記録](docs/verification/phase-0.md)。
フェーズ1のdebug専用センサー記録・JVMリプレイを実装しています。
[実機ログの収集手順](docs/recording.md)に沿って、市街地走行と停車中の手振り等を各3〜5分収集する段階です。
実測ログを確認するまでフェーズ1の完了・フェーズ2への移行は行いません。
[計測機能の検証結果・コミット一覧](docs/verification/phase-1.md)も記録しています。
旧Expo版は `legacy/expo`（起点 `72c7cf6`）に保存し、履歴を保持しています。
旧版の完了チェックはネイティブ版の動作保証を意味しません。

オーバーレイの画像・GIFは、描画機能を実装・検証してから追加します。

## 目的と仕組み

OS標準のモーション支援が利用できない端末でも使える、端末内で完結する実装を目指します。
対象はAndroid 8.0以降、主要検証機はGalaxy S25 Ultra日本キャリア版です。
OS標準機能の提供状況は端末・地域・更新状態に依存します。

設計上は加速度とジャイロ、地磁気を使わない姿勢センサーから水平加速度を求め、
低域フィルタと車両方向の推定を通して画面端のドットへ伝えます。
速度を積分して表示する方式ではありません。手ぶれの除去性能は実走行ログで検証します。
ジャイロ等がない端末では、利用できるセンサーに応じて精度を落とす設計です。

乗り物酔いの軽減に役立つ可能性がありますが、効果を保証するものではありません。
運転中は使用しないでください。通知シェード、クイック設定、ロック画面など、
システムUIの上への描画は対象外です。

## インストール

スマホで[プリリリース](https://github.com/lingmulongtai/Cadence/releases/tag/v0.1.0-alpha.1)の
`Cadence-0.1.0-alpha.1-recorder.apk` を開いてインストールしてください（Android 8.0以降）。
「記録を開始」→「停止して保存」→「名前をつけて保存」で、ダウンロードなどの好きなフォルダに
ZIPを保存できます。「ZIPを共有」も使えます。PC・USBデバッグは不要です。
[詳しい収集手順](docs/recording.md)と[画面・検証結果](docs/verification/recorder-alpha.1.md)を参照してください。

通常製品版とF-Droid対応は後続フェーズの計画です。
Google Play servicesを使用する車両検知とF-Droidの配布条件の整合も、公開前に検証します。

## プライバシーと権限

ネイティブ版は `INTERNET` 権限を宣言せず、ネットワーク通信、アカウント、サーバー、
広告、分析SDKを持ちません。センサーデータの自動送信は行いません。
記録用プレビューはCSVログを端末のアプリ専用ストレージに保存します。
ユーザーの操作でCSVと端末情報のJSONをZIPにまとめ、選んだ保存先や共有先へ渡します。
保存をキャンセル・失敗しても元の記録は残ります。緯度・経度は記録しません。
バックアップも無効にします。

| 権限 | 計画している用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 他アプリ上のドット表示 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 表示中・計測中のサービス |
| `FOREGROUND_SERVICE_LOCATION` | 任意GPSを使う記録中のサービス |
| `POST_NOTIFICATIONS` | 動作通知と停止操作（Android 13以降） |
| `ACTIVITY_RECOGNITION` | 任意の車両検知。拒否時は手動運用 |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | 設定から任意で有効にするGPS補助 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Samsung等での常駐設定案内（後続フェーズ） |

現在のdebug版には記録用のサービス・通知・位置情報の権限だけを含めます。
release版には記録サービス、CSV保存コード、これらの権限を含めません。
他の権限は対応機能の実装時に追加します。200Hz超のサンプリングや画面キャプチャは行いません。

## 設定の設計

既定は同心の白黒ハロー、8個、中サイズ、不透明度60%、感度中、カットオフ1.5Hz、50Hz計測です。
100Hz計測は選択制。自動起動と位置情報補助は初期状態では無効です。
自動起動は乗車検知から30秒待ち、途中で降車検知があれば取り消します。
終了も60秒待ち、信号待ち等での点滅を避ける設計です。
自動車・電車／バス・船のプリセットは、実測後にゲイン・帯域・デッドゾーンを調整します。
これらは後続フェーズの実装対象です。

## Samsung / One UI

実機検証では設定 → アプリ → Cadence → バッテリーで制限状態を確認してください。
「バッテリーとデバイスケア」または「バッテリー」→「バックグラウンドでの使用を制限」で
Cadenceがスリープ／ディープスリープ対象になっていないか確認します。
メニュー名はOne UIのバージョンで変わります。アプリ内の案内導線はUIフェーズで追加します。

## ビルド

JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0を使用します。
`ANDROID_HOME` または未追跡の `local.properties` に `sdk.dir` を設定してください。
Gradle Wrapperがビルドツールと依存関係を取得します（アプリの通信とは別です）。

```powershell
.\gradlew.bat :app:assembleDebug :motion:test :app:lintDebug
```

macOS / Linuxでは `./gradlew` を使います。
Windowsで作業パスに日本語等を含む場合、Android Gradle Pluginのパス検査が失敗します。
ASCIIのみの場所にcloneするか、その場所へのjunction経由でビルドしてください。
デバッグAPKの出力先は `app/build/outputs/apk/debug/app-debug.apk` です。

| モジュール | 責務 |
| --- | --- |
| `:app` | Activity、Compose UI、タイル、通知操作 |
| `:overlay` | サービス、SurfaceView / Canvas描画。センサーの所有者 |
| `:motion` | Android SDK非依存のKotlin処理、CSVリプレイ、JVMテスト |
| `:sensor` | Android SensorManagerと車両検知のラッパー |
| `:data` | DataStore Preferencesによる設定 |

CPU使用率、1時間の電池消費、追従遅延、乗り物酔いへの効果はいずれも未測定です。
エミュレーターや合成データの検証を実車での受け入れ検証として扱いません。

## ライセンス・謝辞

Cadenceは **GPL-3.0-only** です。[LICENSE](LICENSE)を参照してください。
原理を共有する先行OSSの
[Vehicle Motion Cues](https://f-droid.org/packages/dev.davidv.motionsickness/)に敬意を表します。
他実装のコードやAppleのUIを移植するプロジェクトではありません。
