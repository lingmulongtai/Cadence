# 記録プレビューの配布

`v0.1.0-alpha.1` は記録専用debug variantを配布する。通常のrelease variantには記録・エクスポートの
UI、サービス、Provider、保存処理を含めない。製品版の完成やフェーズ1の受け入れとは別のタグ。

## 署名

通常のローカルdebugビルドはAndroidのローカルdebug鍵を使う。公開APKには専用の継続更新用鍵を使う。
`CADENCE_SIGNING_STORE_FILE` と `CADENCE_SIGNING_PASSWORD` を環境変数で指定した場合のみ、
debug variantをそのPKCS12鍵（alias `cadence`）で署名する。

GitHub Actionsはリポジトリの `CADENCE_SIGNING_KEYSTORE`（Base64）と
`CADENCE_SIGNING_PASSWORD` を使う。鍵は一時ディレクトリへ復元し、終了時に削除する。
鍵・パスワードはソースや成果物に含めない。公開にはこの同じ鍵を使い続ける。

## 公開手順

1. atomic commit列をPRで確認し、Android CIを通す。履歴をsquashせずmainへmergeする。
2. `app/build.gradle.kts` のversionNameと一致する `docs/releases/<tag>.md`、現在の画面、検証記録を用意する。
3. mainのコミットへ `vX.Y.Z-alpha.N` の注釈付きタグを付けてpushする。
4. `Recorder prerelease` workflowがmainへの包含、タグ、テスト、両variant、記録境界、APK署名を確認し、
   署名済みの記録APK・SHA256SUMS・署名情報・commit情報をGitHubの **prerelease** として公開する。
5. 公開後、ダウンロードしたAPKのハッシュ・署名・application ID・versionを確認する。

失敗時はrunログを確認する。タグの付け直し、force-push、既存公開APKの無断差し替えはしない。
次回はversionCodeも増やす。F-Droid対応・製品版release APKの配布は別途検証する。
