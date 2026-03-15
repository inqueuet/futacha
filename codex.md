# Codex Notes

## Maintainability Snapshot (2026-03-13)

### Overall estimate

- project overall: `about 25-35% improved`
- `shared` overall: `about 35-45% improved`
- screen layer: `about 50-60% improved`
- `FutachaApp` and adjacent screen wiring: `about 75-85% improved`
- state / repository layer touched in this pass: `about 35-45% improved`

This is an improvement estimate relative to the pre-refactor structure, not an absolute quality score.

### What has materially improved

1. Large screen files are now split by responsibility.
- `ThreadScreen` has been decomposed into state, overlay, bindings, execution helpers, derived runtime, and component files.
- `CatalogScreen`, `BoardManagementScreen`, and `GlobalSettingsScreen` now follow the same pattern:
  - screen file = orchestration
  - support/bindings file = state and callbacks
  - component file = UI rendering
- recent additions in this pass:
  - `GlobalSettingsScreen` now builds a single scaffold binding bundle instead of forwarding a long section-level argument list directly.
  - `BoardManagementScreen` now builds screen bindings for both scaffold and overlay hosts, so dialog/settings/cookie-management wiring is no longer spread across the screen body.

2. `AppStateStore` is no longer a single opaque blob.
- public entry points are now grouped around:
  - boards
  - history
  - preferences
- persistence, mutation, cached snapshot, debounce, and seed logic have been moved into support/facade files.

3. network / repository / service layers now have clearer execution boundaries.
- `HttpBoardApi`:
  - posting config, form building, retry, and posting-config cache orchestration are separated.
- `DefaultBoardRepository`:
  - cookie initialization, auth retry, OP-image cache, and close/cache cleanup are separated.
- `SavedThreadRepository`:
  - index/identity/path candidate logic is separated.
  - add/remove/update now share common index mutation helpers instead of maintaining parallel update paths.
- `ThreadSaveService`:
  - runtime helpers, execution helpers, media planning/download batching, saved-post conversion, raw HTML persistence, metadata persistence, output preparation, and final result building are separated.

4. testability is substantially better than before.
- many previously in-file private branches are now covered by `commonTest`.
- the shared JVM test corpus is the main safety net for refactoring shared logic.
- newer contract-style tests now lock down:
  - `FutachaApp` destination props / screen bindings
  - `AppStateStore` history mutation flow
  - `SavedThreadRepository` index mutation flow
  - `GlobalSettingsScreen` scaffold binding assembly
  - `BoardManagementScreen` scaffold/overlay binding assembly

### What is still structurally expensive to change

1. Remaining large orchestration/support files.
- `ThreadSaveService` is much smaller than before, but it still owns the top-level save pipeline.
- `HttpBoardApi` and `DefaultBoardRepository` still contain long request/execution flows, even though most private details are already extracted.
- `ThreadScreen.kt`, `GlobalSettingsComponents.kt`, `BoardManagementComponents.kt`, and `FutachaAppSupport.kt` are still large enough that future changes can become expensive if more responsibilities accumulate there.

2. Runtime integration verification.
- Android instrumented coverage has improved in source, but end-to-end device execution is still incomplete.
- iOS host/BGTask behavior still depends on manual/runtime verification.

3. Cross-layer regression confidence.
- pure helper coverage is much better.
- integration coverage across repository/service boundaries is still thinner than the UI/support coverage.

## Recommended Next Maintainability Order

### Highest value

1. Continue thinning service/repository orchestration.
- target first:
  - `shared/src/commonMain/kotlin/com/valoser/futacha/shared/service/ThreadSaveService.kt`
  - `shared/src/commonMain/kotlin/network/HttpBoardApi.kt`
  - `shared/src/commonMain/kotlin/repo/BoardRepository.kt`
- direction:
  - move remaining phase/execution orchestration into dedicated runner/support files
  - leave the main class as dependency wiring + high-level flow only

2. Strengthen repository/service integration tests.
- add focused tests around:
  - save pipeline failure cleanup
  - posting-config/cache fallback flows
  - cookie initialization and auth retry flows
  - saved-thread index consistency after add/remove/update

### Medium value

3. Finish runtime/integration verification.
- Android:
  - rerun instrumented tests on an actually online emulator/device
- iOS:
  - execute the existing manual verification strategy for host/BGTask paths

4. Keep new changes on the same structure.
- when touching a large file:
  - extract pure logic first
  - add `commonTest`
  - then move runtime/binding/component code

### Most recent maintainability wins

- `FutachaApp` screen routing and cross-screen callback wiring were consolidated into helper/builders, leaving the app shell much thinner.
- `CatalogScreen` and `ThreadScreen` setup/orchestration were moved into dedicated setup bundles.
- `GlobalSettingsScreen` and `BoardManagementScreen` now use screen-level binding bundles to reduce prop drilling and make future section changes more localized.
- `AppStateStore` history updates and `SavedThreadRepository` index updates now share common mutation paths, reducing duplicate persistence/index maintenance logic.

## Practical Rule For Future Refactors

When choosing the next maintainability task, prefer this order:

1. remove long private branches from service/repository classes
2. convert them into small support or facade units
3. add or extend `commonTest`
4. only then touch platform-specific runtime tests

This is the most cost-effective path now. The biggest readability wins in the UI layer are already captured; the next real gains come from making shared execution flows smaller and easier to verify.

## Current Test Coverage

Common tests already cover a large part of:

- parser core
- `HttpBoardApi` support/validation/request flows
- cookie storage/repository/support
- save services/repository/round-trip flows
- history refresher/support
- `AppStateStore` support + part of integration paths
- `CatalogScreen` support helpers
- `GlobalSettingsScreen` support helpers:
  - preferred file manager summary
  - storage summary / warning state
  - catalog/thread menu config editing helpers
- `ThreadScreen` pure helpers:
  - search
  - refresh/load messages
  - save/read-aloud messages
  - reply/delete validation
  - quote selection helpers
  - thread filter helpers
  - NG filter helpers
- `FileSystem` / `UrlLauncher` support helpers:
  - path and size validation
  - URL normalization and launch target resolution
  - Android absolute-path alias resolution
  - iOS Documents/private absolute-path resolution
- `BackgroundRefreshManager` support helpers:
  - schedule submission vs backoff delay
  - retry-limit handling
  - retry job gating / delay normalization
- `PlatformVideoPlayer` / iOS host support helpers:
  - video preview chrome state
  - embedded video URL sanitization / HTML generation
  - iOS background refresh flow retry/backoff calculation
  - ready/idle state mapping
  - mute/volume normalization
- `ImagePicker.ios` support helpers:
  - picked image payload size validation
  - filename fallback for selected images

Validated repeatedly with:

- `./gradlew :shared:check`
- `./gradlew :shared:jvmTest`
- `./gradlew :app-android:assembleDebug`
- `./gradlew :app-android:testDebugUnitTest`
- `./gradlew :app-android:assembleDebugAndroidTest`

Important note:

- `shared` now has a JVM host target, so `commonTest` is executable in this environment through `:shared:jvmTest`
- latest result on 2026-03-11:
- latest confirmed result on 2026-03-13:
  - `./gradlew :shared:check` -> success
- earlier result on 2026-03-11:
  - `./gradlew :shared:jvmTest` -> success
  - 323 tests executed on JVM
- iOS test tasks exist (`iosX64Test`, `iosSimulatorArm64Test`) but are skipped here
- so the current improvement is best described as:
  - much broader testable logic extraction
  - JVM-executed common test corpus for shared logic
  - runtime gaps are now concentrated in Android instrumented execution and iOS host/manual verification

Android instrumented source now also includes:

- `UrlLauncherInstrumentedTest`
  - `mailto:` -> `ACTION_SENDTO`
  - `https:` -> `ACTION_VIEW`
  - blank input -> no launch
- `AndroidFileSystemInstrumentedTest`
  - `AUTO_SAVE_DIRECTORY` -> private app storage
  - `Documents` alias -> `futacha/saved_threads`
  - `SaveLocation.Path` write/read/delete round-trip
- `PlatformVideoPlayerAndroidTest`
  - invalid local file URI -> buffering then error callback
  - mute / volume recomposition smoke coverage

iOS manual verification now has extra debug logging around:

- `MainViewController` background refresh collector start / state changes / configure calls
- `runIosBackgroundRefresh()` start / success / cancellation / repo close
- `BackgroundRefreshManager` register / configure / submit / backoff / retry / cancel / expiration

## Remaining Test Gaps

### High Priority

1. Android instrumented execution
- `./gradlew :app-android:connectedDebugAndroidTest` は 2026-03-11 に再実行
- 結果:
  - build/install 準備までは成功
  - `emulator-5554` は検出されたが `Device is OFFLINE`
  - 最終的に `No online devices found.` で失敗
- エミュレータを online 状態にして再実行が必要

## Platform-Specific Test Gaps

These are still largely untested at runtime/integration level:

- `shared/src/androidMain/kotlin/util/FileSystem.android.kt`
- `shared/src/iosMain/kotlin/util/FileSystem.ios.kt`
- `shared/src/androidMain/kotlin/util/UrlLauncher.android.kt`
- `shared/src/iosMain/kotlin/util/UrlLauncher.ios.kt`
- `shared/src/androidMain/kotlin/ui/board/PlatformVideoPlayer.android.kt`
- `shared/src/iosMain/kotlin/ui/board/PlatformVideoPlayer.ios.kt`
- `shared/src/iosMain/kotlin/util/ImagePicker.ios.kt`
- `shared/src/iosMain/kotlin/MainViewController.kt`
- `shared/src/iosMain/kotlin/background/BackgroundRefreshManager.kt`

## Platform Integration Strategy

### Android

1. `FileSystem.android`
- target: `resolveAbsolutePath()`, public/private fallback, SAF `TreeUri` write/read/delete
- approach:
  - keep validation in `commonTest`
  - add `app-android/src/androidTest` instrumentation around a real `AndroidFileSystem`
  - cover:
    - `AUTO_SAVE_DIRECTORY` resolves under private app storage
    - `Download` / `Documents` aliases resolve under `futacha/...`
    - `SaveLocation.Path` write/read/delete round-trip
    - `SaveLocation.TreeUri` round-trip using picker-granted tree URI if test env can seed one
- constraints:
  - emulator/device filesystem differences
  - `TreeUri` test may need to stay manual unless a stable test document provider is introduced

2. `UrlLauncher.android`
- target: browser vs `mailto` intent routing and invalid URL rejection
- approach:
  - keep URL normalization in `commonTest`
  - add instrumentation test with Espresso-Intents or ActivityMonitor
  - assert:
    - `mailto:` uses `ACTION_SENDTO`
    - `https:` uses `ACTION_VIEW` + `CATEGORY_BROWSABLE`
    - blank/scheme-less input does not launch external activity

3. `PlatformVideoPlayer.android`
- target: ExoPlayer state callback mapping and release behavior
- approach:
  - keep preview chrome rules in `commonTest`
  - add a focused instrumentation smoke test that hosts `PlatformVideoPlayer`
  - drive with a local MP4 asset or tiny HTTP fixture and assert:
    - buffering callback arrives first
    - ready callback is observed
    - mute/volume updates do not crash
    - composable disposal releases the player without leaking

### iOS

1. `FileSystem.ios`
- target: Documents/ApplicationSupport resolution, bookmark-based write/read/delete
- approach:
  - keep validation in `commonTest`
  - add host-side XCTest or manual verification for:
    - `AUTO_SAVE_DIRECTORY` goes to private Application Support
    - normal relative paths go to Documents
    - bookmark-based save directory survives write/read/delete in one session
    - invalid/stale bookmark fails with re-selection guidance

2. `UrlLauncher.ios`
- target: `UIApplication.canOpenURL/openURL` routing
- approach:
  - keep normalization in `commonTest`
  - verify manually or with host-side fake wrapper after introducing a small platform adapter
  - assert:
    - `mailto:` and `https:` pass `canOpenURL`
    - blank/scheme-less input is ignored

3. `PlatformVideoPlayer.ios`
- target: WKWebView-based embedded playback wiring
- approach:
  - keep extension parsing / HTML sanitization in `commonTest`
  - verify manually on simulator/device with MP4 and WEBM sample URLs
  - assert:
    - initial buffering state
    - ready transition after page load
    - error transition on invalid URL
    - HTML escaping prevents broken markup for quoted URLs

4. `ImagePicker.ios`
- target: picker delegate lifecycle, max-size guard, null-root fallback
- approach:
  - keep size/selection policy as candidate pure helpers if more logic is added
  - current best option is manual verification on simulator/device:
    - no root VC -> returns null
    - cancel returns null once
    - oversized image is rejected
    - normal image returns bytes + filename

## Manual Verification Strategy

### iOS host / BGTask

1. `MainViewController`
- toggle background refresh on/off from settings
- confirm `configureIosBackgroundRefresh()` is called once per distinct state change
- force collector failure in debug build and verify exponential backoff logs stop after retry limit

2. `BackgroundRefreshManager`
- with `BGTaskSchedulerPermittedIdentifiers` present:
  - app launch registers task
  - enable schedules refresh
  - disable cancels pending request and active retry job
- with missing identifier:
  - registration is skipped and logs explicit reason

3. background execution
- on a real device/simulator capable of BGTask testing:
  - schedule refresh while enabled
  - trigger task via Xcode debug tooling
  - verify only one active refresh runs
  - verify completion reschedules next refresh
  - verify expiration handler cancels active work

## Recommended Next Order

1. instrumented test の実機実行確認
2. 実機/エミュレータ接続後に Android instrumented test 実行確認
3. iOS host / BGTask runtime wiring の manual verification strategy 整理

## Working Rule For Next Pass

Prefer this sequence when continuing:

- extract pure/state-composition helpers from large Compose files
- add `commonTest` coverage first
- only then add Android/UI/instrumented tests where common tests cannot cover behavior
