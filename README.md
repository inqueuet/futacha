# ふたちゃ - ふたばちゃんねるブラウザ
> Kotlin Multiplatform × Compose Multiplatform で Android と iOS に共通 UI (`FutachaApp`) を配信する Futaba 専用ブラウザ。

## Getting Started

### Prerequisites
- Java 21（もしくは Kotlin 1.9 以降がサポートする JDK 17+/21）。  
- Android Studio（API 33/34 以上の SDK）＋ Android SDK/NDK を `local.properties` で設定。  
- macOS ＋ Xcode（iOS を触る場合）。CocoaPods は不要ですが、Kotlin/Native の toolchain を整備してください。  
- `git` と `./gradlew` の実行権限。

### Setup
1. リポジトリをクローンし、`./gradlew` を一度実行して依存をダウンロード。  
2. Android Studio で `app-android` をインポートし、`sdk.dir` を `local.properties` に設定。  
3. iOS では `./gradlew :shared:linkDebugFrameworkIosArm64` で Compose フレームワークを生成し、Xcode プロジェクトから host target に組み込みます。  

### Running the App
- **Android**  
  - `./gradlew :app-android:installDebug` でデバッグ APK を生成・サイドロード。  
  - Android Studio から `app-android` モジュールをビルド＆ランすると、MainActivity が Compose UI を表示します。  
- **iOS**  
  - `./gradlew :shared:linkDebugFrameworkIosArm64` → Xcode で framework をリンク → `MainViewController` をホストして Compose UI (`FutachaApp`) をレンダリング。  
  - シミュレータでも実機でも同じコードベースが動きます（ただし現状 Thread 保存に必要な `FileSystem` の注入が TODO）。  

### User Manual
1. **Board Management**  
   - Board/Catalog/Thread を1ファイルで管理。左上のメニューから履歴ドロワーを開いたり、Board の追加・削除・並び替え・設定を行います。  
   - `Add board` では URL スキーマのバリデーションと重複チェック後に slugify した ID を追加。  
2. **Catalog**  
   - `LazyVerticalGrid` で 5 列表示。Pull-to-refresh と下部のドラッグセンチネルで最新化。  
   - Bottom navigation からスレ立て（CreateThread）、更新、表示モード、設定（監視ワード・NG管理・外部アプリ・表示切替・トップ移動・プライバシー）を切り替え。  
   - 検索モードも搭載し、バック操作で検索解除 → ドロワー → 前画面の順に戻ります。  
3. **Thread**  
   - Save / Reply / Gallery / Refresh / Scroll に対応したアクションバー。自動保存は 60 秒ごとに `AUTO_SAVE_DIRECTORY` にレコードを残し、ネットワーク不通時はオフラインコピーを通知。バックグラウンド更新時も本文・メディアを自動保存します。  
   - 引用プレビュー、ID ハイライト、検索（前/次）や長押しの操作シートなど、Compose で細かい動作を実装。  
   - `GlobalSettingsScreen` や `ThreadSettingsSheet` から NG 管理、外部アプリ、プライバシーフィルタ、読み上げ（基本実装）が利用可能。  
4. **History & Saved Threads**  
   - History Drawer から閲覧履歴をスワイプ削除。履歴リストは `ThreadHistoryEntry` のスクロール位置も保持。  
   - `SavedThreadsScreen` は存在するが遷移経路が未実装。手動保存はスレ保存ダイアログから `SavedThreadRepository` に記録されます。  
5. **Global Settings & Version**  
   - Board/Catalog/Thread のどこからでも `GlobalSettingsScreen` を開け、Email/X/GitHub へのリンクと `VersionChecker` 由来の `appVersion` を確認。  
   - バックグラウンド更新トグル（Android は WorkManager の 15 分最小間隔定期実行、iOS は BGTask）に加えて、「スレ保存先」を Documents/Download の簡易指定や絶対パスで変更可能（手動保存用の `manualSaveDirectory` を更新）。  

詳しいアーキテクチャや機能の振る舞いについては `AGENTS.md` を参照してください（モック vs リモート、データストア、HTTP API、保存処理、画面遷移などを網羅しています）。

## Testing
- `./gradlew :shared:check`（`CatalogHtmlParserCoreTest` / `ThreadHtmlParserCoreTest` / `BoardManagementScreenTest` + 共通 JVM テスト）。  
- Compose プレビューや手動での動作確認は `FakeBoardRepository` + `example/` フィクスチャを利用。

## Deployment & Release
- Android: `./gradlew :app-android:assembleRelease` → Play Console へアップロード。`build.gradle.kts` の signingConfigs を適宜設定。  
- iOS: Kotlin/Native フレームワークを Xcode プロジェクトに組み込み、Xcode 経由でアーカイブ・配布。  
- GitHub Releases との連携で `VersionChecker` が `releases/latest` をチェックし、新バージョン通知ダイアログを表示します。

## Development Process
- UI/ロジックは `shared/` に集約（Compose + Ktor + StateFlow）。`AppStateStore` が Boards/History/Privacy/NG/Watch Words を管理。  
- Platform 層で `HttpClient`, `FileSystem`, `VersionChecker`, `PermissionRequest`, `ImagePicker` などを expect/actual で注入。  
- キャッシュ・保存・メディア再生（Coil + Media3/AVPlayer/WKWebView）も `shared` 側で Compose UI に集約しています。詳細は `AGENTS.md` の 0〜5 セクションを参照。  

## Recent Changes (5f977c478d5f 以降)
- Android のバックグラウンド履歴更新を Foreground Service から WorkManager 定期実行 (15 分最小、ネット必須、即時ワンショット付き) に置き換え、FOREGROUND_SERVICE 系パーミッションを削除。  
- 手動保存先を設定画面から変更可能にし、Documents/Download/絶対パスの入力に対応。`manualSaveDirectory` を DataStore/NSUserDefaults へ永続化。  
- バックグラウンド更新でも本文・画像・動画を `AUTO_SAVE_DIRECTORY` に自動保存し、履歴のレス数とメタデータを最新化。  
- カタログモードと表示スタイルの永続化・適用周りを整備（モードの保存・復元）。  
- Thread 保存処理や FileSystem のバグを修正し、保存先解決とエラーハンドリングを改善。  

## Support & Issues
- 問題点や改善案は GitHub Issues で共有してください（リポジトリの Issue テンプレートを活用）。  
- 使い方で困った場合、`AGENTS.md` に記載された UI フロー・保存・設定の詳細を先に確認すると多くは解決します。  
- バグ報告には再現手順、ログ、端末情報を添えてください。  

## Additional Resources
- `AGENTS.md`: エントリポイント、UI フロー、状態管理、ネットワーク、保存処理、プラットフォーム実装、VersionChecker など詳細なドキュメント。  
- `app-android/` / `shared/` / `example/` を参照すると、設定ファイルやフィクスチャ、モックデータも確認できます。
