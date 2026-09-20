# 実機ログの収集とリプレイ

フェーズ1の開発用機能です。**ドット表示・車両判定・手ぶれ除去はまだ実装していません。**
記録画面、センサー購読、CSV保存サービスはdebugビルドだけに含まれます。

## スマホにインストールする

1. スマホで[記録用プリリリース](https://github.com/lingmulongtai/Cadence/releases/tag/v0.1.0-alpha.1)を開く。
2. Assetsの `Cadence-0.1.0-alpha.1-recorder.apk` をダウンロードして開く。
3. Androidの確認画面に従ってインストールし、Cadenceを開く。PC・ADB・USB接続は不要。

Android 8.0以降が対象。記録専用のdebugビルドを、継続更新用の専用鍵で署名して配布する。
開発者が手元でビルドしたAPKと署名が異なる場合は上書きできない。
その場合も、アンインストールやデータ削除より先に、既存の記録をZIPで保存する。

## 1セッションの記録

1. **乗客として**操作する。運転中の操作はしない。
2. 初回は100Hzをオフにした既定の50Hzで記録する。100Hz比較は別セッションにする。
   記録名には「市街地・50Hz・手持ち」「停車中の手振り」などを入力できる（省略可）。
3. GPS速度・進行方向が必要な場合だけチェックを入れ、正確な位置情報を許可する。
   拒否した場合はチェックが入らず、GPSなしで記録できる。緯度・経度は保存しない。
4. 「記録を開始」を押す。Android 13以降は停止操作を表示するため通知を許可する。
   通知を拒否した場合は記録を開始しない。再度許可できない場合は端末のアプリ通知設定を変更する。
5. イベント件数が増えることを確認し、同じ条件で3〜5分記録する。
   画面表示中は自動消灯を抑止する。ロックせず、別アプリへ移った場合も画面を点灯したままにする。
6. 「停止して保存」、または通知を展開して「停止 / Stop」を押す。
   「保存しました。下の『保存済みの記録』から取り出せます。」になるまで待つ。

購読はActivityではなくサービスが所有する。画面を開き直しても二重購読しない。
GPSが端末設定で無効なら、警告を表示しセンサーだけを記録する。
GPSが有効でも測位前や速度・bearingが得られない時には `LOCATION` 行がないことがある。
JSONの `gpsStatus` は開始時点の状態であり、測位成功の証明には使わない。

## スマホで名前と保存先を選ぶ・共有する

1. 「保存済みの記録」で目的の名前・日時を確認する。アプリを閉じても一覧に残る。
2. **「名前をつけて保存」**を押す。
3. Androidの保存画面で、ダウンロード・端末内・SDカードなど、表示された好きな保存先へ移動する。
   新しいフォルダも作れる。利用できる保存先は端末とインストール済みのファイルアプリによる。
4. ファイル名を自由に変更し、拡張子 `.zip` を残して「保存」を押す。
5. Cadenceに「選んだ保存先にZIPを保存しました」と表示されたら完了。
   Samsungの「マイファイル」などでそのフォルダを開き、ZIPをPCへ送る。

アプリの **「ZIPを共有」** から、Quick Shareやインストール済みの送信アプリを選ぶこともできる。
共有先の操作は利用者が行う。**送ってもらうのはZIP 1個でよく、展開やCSV変換は不要。**
記録時の車種・固定か手持ちか・だいたい何をしたかを短いメモで添えると解析に使いやすい。

- ZIPには元の `sensors-….csv` と同名の `.csv.json` が入り、中身も内部ファイル名も変更しない。
- 完成メタデータとCSVの件数・形式を確認してからZIPを作る。中断・失敗した `.partial` は一覧から出力しない。
- 保存キャンセル・保存失敗でも元の記録は残る。保存中にアプリが終了した場合は再保存する。
  中断メッセージが出た場合、保存先に残ったZIPは未完成の可能性がある。
- 同じ名前のファイルがある場合、Androidが末尾に番号を付けることがある。
- アプリをアンインストール／データ削除するとアプリ内の元記録は消える。先にZIPを保存する。
  選んだフォルダへ保存したZIPはアプリの削除後も残る。

## PCで受け取る（解析担当向け）

受け取ったZIPを `.codex/collected/` などGit対象外のフォルダへ展開する。
元のZIPも残し、以下の完成状態・件数照合とJVMリプレイを行う。

ADBで直接取り出す場合だけ、USBデバッグを許可して端末serialを指定する。

```powershell
$deviceSerial = 'DEVICE_SERIAL'
adb -s $deviceSerial pull /sdcard/Android/data/dev.lingmulongtai.cadence/files/Documents/recordings .codex/collected
```

`sensors-<時刻>-<ランダム値>.csv` と同名の `.csv.json` を**一緒に**保管する。
`.codex/` はGitの対象外。内容を確認してから必要なファイルだけ `testdata/reference/` へコピーする。
アンインストールやアプリデータ削除ではログも消えるため、先にPCへ取り出す。

- 記録中・中断・エラー時は `.csv.partial`。拡張子を変えて完成品として扱わない。
- 正常停止時はCSVをflush / syncし、完成メタデータをatomicに置換してから `.csv` にする。
- キューあふれや保存エラーは失敗として表示する。欠測を隠して完成扱いにしない。
- JSONは記録名・開始日時・記録時間・端末・OS・センサー名・最小周期・要求周期・GPSの選択・完成状態・件数を持つ。
  `dataKind: unreviewed` は自動生成時の値。実機であることや収集条件は人が確認する。

対象のファイル名を指定して、完成状態と件数を照合する。

```powershell
$recording = Get-Item '.codex/collected/sensors-REPLACE-ME.csv'
$metadata = Get-Content -LiteralPath ($recording.FullName + '.json') -Raw | ConvertFrom-Json
$rowCount = (Import-Csv -LiteralPath $recording.FullName | Measure-Object).Count
if (-not $metadata.complete -or $metadata.eventCount -ne $rowCount) {
    throw '記録が未完成、またはCSVとメタデータの件数が不一致です'
}
```

## JVMでリプレイする

Android端末を切り離しても実行できる。JDK 17を使う。

```powershell
.\gradlew.bat :motion:test
.\gradlew.bat :motion:replay "-PinputCsv=$($recording.FullName)"
.\gradlew.bat :motion:replay "-PinputCsv=$($recording.FullName)" "-PoutputCsv=motion/build/ordered.csv"
```

リプレイはセンサーの単調増加ナノ秒時計で安定ソートし、件数・記録時間・種別ごとの件数を出す。
元のfloat値・timestampを保ち、等間隔の補間や壁時計による待機は行わない。
同じtimestampのイベントは入力順を保つ。出力で元の入力ファイルを上書きする指定は拒否する。
`.partial` はCLIで拒否する。CLI自体はJSONメタデータを検証しないため、上の照合も必ず行う。
`ordered.csv` は処理用の派生ファイルであり、元のCSV・JSONの代わりにしない。

現在は**生データのリプレイ**まで。`MotionCue` の生成・confidenceの判定はフェーズ2で追加する。
合成形式サンプルは次で試せるが、実測の受け入れデータにはならない。

```powershell
.\gradlew.bat :motion:replay "-PinputCsv=testdata/synthetic/schema.csv"
```

## CSVの形式

```text
timestampNs,sensorType,v0,v1,v2,v3,v4,speedMps,bearingDeg
```

| 種別 | 保存する値 |
| --- | --- |
| `ACCELEROMETER` | Android端末座標の加速度 x/y/z、m/s²（重力込み） |
| `GRAVITY` | 重力 x/y/z、m/s² |
| `LINEAR_ACCELERATION` | 重力除去済みの加速度 x/y/z、m/s² |
| `GYROSCOPE` | 角速度 x/y/z、rad/s |
| `GAME_ROTATION_VECTOR` | Androidが返した3〜5要素をそのまま保存。地磁気型は使わない |
| `LOCATION` | 取得できた速度 m/s・bearing [0,360) 度。センサー値列は空 |

存在しないセンサーは記録されない。加速度センサーの登録ができない場合は失敗として表示する。
50Hzは `SENSOR_DELAY_GAME`、100Hzは10,000µsを要求する。ハードウェアがそれより遅い場合は
最小周期を優先する。実際の到着間隔はOSや端末に依存するため、要求レートと実測周期を混同しない。
列数、非有限値、不正な種別・値、途切れた行はパーサーが行番号付きで拒否する。

## フェーズ1を完了する条件

まず市街地走行（停止・発進・右左折を含む）と、停車中に端末を手で振るログを各3〜5分収集する。
歩行、高速道路の定速、電車、助手席固定と手持ちの比較も別セッションで収集する。

利用者にはZIPと簡単な状況メモを送ってもらう。解析担当がJSON・リプレイ結果と突き合わせ、以下を整理する。

- 実機の機種・OS、収集日、実機かエミュレーターか、APKのSHA256またはビルド元commit
- シナリオ、車両の種類、端末の向き、固定／手持ち、GPSの選択
- 操作・イベントのおおよその時刻、途中のロック・割り込み・エラーの有無
- CSVとJSONの件数照合、リプレイ結果、実測の記録時間

元データを確認して `testdata/reference/` に保存し、リプレイできた時点でフェーズ1の受け入れを判定する。
データ量を見てGit LFSが必要か判断する。合成値やエミュレーター値で実走行を代用しない。
