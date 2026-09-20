# Expo版の記録

起点: `72c7cf664760928443a0c7eaf887798061feb5b6`、保存ブランチ: `legacy/expo`。
ネイティブ版へコードをコピーせず、画面構成と配色の意図を残す。

## 引き継ぐUX

- 初回は機能紹介から「続ける」でホームへ進む。
- ホームは状態、中央の大きい電源ボタン、ライブプレビューの順。
- 日本語の「ホーム」「設定」の2つの入口を持つ。
- 設定は動作モード、外観、感度、説明のまとまりを保つ。
- 運転者の利用を避ける案内と、効果を断定しない説明を残す。

旧タイトルはスクリーンショットに記録するが、新版の名称はCadenceにする。
新版の設定既定値は指令書を優先する（旧版は初回からmode=onだった）。

## 配色

`theme.config.js` の値。各ペアはlight / dark。

| 役割 | light | dark |
| --- | --- | --- |
| primary | `#007AFF` | `#0A84FF` |
| background | `#FFFFFF` | `#000000` |
| surface | `#F2F2F7` | `#1C1C1E` |
| foreground | `#000000` | `#FFFFFF` |
| muted | `#8E8E93` | `#636366` |
| border | `#E5E5EA` | `#38383A` |
| success | `#34C759` | `#30D158` |
| warning | `#FF9500` | `#FF9F0A` |
| error | `#FF3B30` | `#FF453A` |

新UIはMaterial 3を使い、Appleの画面をそのまま再現しない。

## 実装から分かった制約

- オーバーレイはHomeのReactツリー内で表示され、他アプリ上のサービスではない。
- 適応色はOSテーマによる白黒切替。背景ピクセルは読んでいない。
- 通知操作はReact側の通知リスナーで、ネイティブのQSタイルではない。
- Webでは実センサー・通知を使わない。画面の記録で動作精度は証明できない。
- プレビューの枠は高さ160pxだがドット座標は画面全体を使うため、枠内に見えない場合がある。
- 旧ユニットテストはreducerのコピーを検査しており、実センサーの精度を検証していない。

参照: `app/(tabs)/index.tsx`, `app/(tabs)/settings.tsx`,
`components/onboarding-screen.tsx`, `components/motion-dots-overlay.tsx`,
`lib/motion-cues-context.tsx`, `hooks/use-motion-sensor.ts`, `theme.config.js`
（すべて `legacy/expo` 上）。

## 撮影方法

ExpoのWeb表示で `/` のオンボーディングとホーム、`/settings` を記録する。
Androidネイティブのスクリーンショットではなく、旧UXの記録である。
初回画面はブラウザーの新しいセッションで撮影し、「続ける」からホームへ進む。

2026-09-20、412×915のブラウザーで実際に表示して撮影した。
現在のpnpmは旧 `.npmrc` のhoisted指定を引き継がなかったため、取得済み依存を
ローカルのnode_modulesで参照できるように補正した。アプリのソースは変更していない。

| 画面 | 記録 |
| --- | --- |
| 初回紹介 | [01-onboarding.png](01-onboarding.png) |
| ホーム・オン | [02-home-on.png](02-home-on.png) |
| ホーム・オフ | [03-home-off.png](03-home-off.png) |
| 設定 | [04-settings.png](04-settings.png) |
