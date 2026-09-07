# スマート選択の改修証跡

調査・実装の説明は [改修報告](../../text-selection-fix-2026-09-07.md) を参照。
実機はSCG33 / Android16 / API36。端末上のキー・タッチイベントは計測テストから発行した。

- `final-device-results.txt`: 最終Debug APKでの回帰テストと既存UIテスト。
- `final-apk-build.txt`: 最終ビルド用パッケージ配置でのAPKビルド。
- `quality-current-dependencies.txt`: 依存カタログ整理後のqualityGate。
- `quality-and-release-build.txt`: qualityGateとR8 Releaseビルド、API35 Baseline Profile生成。Crashlytics mapping送信は除外。
- `release-guard-mapping-excerpt.txt`: R8が2箇所の補強を取り込んだことを示すmappingの抜粋。
- `artifact-sha256.tsv`: 最終Debug/Test APK、検証済みRelease APKと主要入力のSHA-256。Releaseビルド後に依存カタログの追加整理とビルド用Javaパッケージの配置変更を行ったため、Releaseは最終Debugと完全に同じビルド入力の成果物ではない。
- `unpatched-boundary-and-input-results.txt`: 未改修の最小アプリ。18件中3件は既知の未修正例外（長押し中削除2件・分類例外1件）で失敗する比較用テスト。
- `unpatched-combined-menu-repeat.txt`: 未改修版の範囲拡張＋分類メニューの組合せ診断5回。4回成功・1回失敗。
- `patched-combined-menu-failure.txt`: 改修版でも同じ組合せ診断が失敗したログ。クラッシュ対策テストの失敗とは区別する。
- `TextSelectionCombinedMenuDiagnostic.kt`: 組合せ診断の改修アプリ用ソース。恒久テストとは別に保存。
- `unpatched/`: 同じFoundationを使い、ASM補強を適用しない比較アプリの入力。アプリIDは `com.valoser.futacha.selectionbaseline` で既存Futachaデータと分離。比較時点のテストソースを保存している。

## 再実行

リポジトリ直下から `./gradlew :app-android:assembleDebug :app-android:assembleDebugAndroidTest` を実行し、
生成したDebug APKと計測APKを対象端末へインストールする。
`adb shell am instrument -w -e class <対象クラスのカンマ区切り> com.valoser.futacha.test/androidx.test.runner.AndroidJUnitRunner`
で実行する。クラスと件数は改修報告に記載。

未改修の比較アプリは、Android SDKを設定した環境で
`./gradlew -p quality/repro/text-selection-fix/unpatched assembleDebug assembleDebugAndroidTest`
を実行する。共通カタログを参照するため、将来カタログの版を変えた場合は本記録と同じ比較条件にはならない。
計測の宛先は `com.valoser.futacha.selectionbaseline.test/androidx.test.runner.AndroidJUnitRunner`。
比較アプリには既知の失敗を確認するテストも含まれるので、通常の品質ゲートへ追加しない。

ログに本文・認証情報は収集しない。メニュー診断の表示文字は専用テストActivityの固定文字列に限定する。
