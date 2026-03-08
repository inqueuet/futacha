# Codex Notes

## Current Test Coverage

Common tests already cover a large part of:

- parser core
- `HttpBoardApi` support/validation/request flows
- cookie storage/repository/support
- save services/repository/round-trip flows
- history refresher/support
- `AppStateStore` support + part of integration paths
- `CatalogScreen` support helpers
- `ThreadScreen` pure helpers:
  - search
  - refresh/load messages
  - save/read-aloud messages
  - reply/delete validation
  - quote selection helpers
  - thread filter helpers
  - NG filter helpers

Validated repeatedly with:

- `./gradlew :shared:check`
- `./gradlew :shared:assembleUnitTest`
- `./gradlew :app-android:assembleDebug`

## Remaining Test Gaps

### High Priority

1. `shared/src/commonMain/kotlin/ui/FutachaApp.kt`
- screen navigation glue
- history deletion <-> auto-save deletion linkage
- saved threads screen entry/return flow
- URL tap to board/thread navigation integration

2. `shared/src/commonMain/kotlin/ui/board/ThreadScreen.kt`
- media preview next/prev navigation
- gallery state transitions
- quote preview dialog state
- save progress dialog state
- auto-save trigger conditions
- initial refresh vs manual refresh interaction
- action sheet availability rules
- BackHandler/drawer/search interactions

3. `shared/src/commonMain/kotlin/ui/board/CatalogScreen.kt`
- settings action branching as a whole
- display style dialog state
- watch words sheet state
- NG management sheet state
- drawer/back handling
- pull-to-refresh / bottom sentinel refresh behavior
- create-thread dialog UI state transitions

4. Android tests are effectively missing
- `app-android/src/test/java/com/valoser/futacha/ExampleUnitTest.kt`
- `app-android/src/androidTest/java/com/valoser/futacha/ExampleInstrumentedTest.kt`
- need first real Android unit/instrumented tests

### Medium Priority

5. `shared/src/commonMain/kotlin/ui/board/GlobalSettingsScreen.kt`
- save directory display state
- file manager selection state
- cookie manager navigation
- menu config editing

6. `shared/src/commonMain/kotlin/ui/board/BoardManagementScreen.kt`
- add/delete/reorder/settings flows
- existing `BoardManagementScreenTest` is too thin

7. `shared/src/commonMain/kotlin/ui/board/SavedThreadsScreen.kt`
- fake repository based reload/delete integration-style tests

8. `shared/src/commonMain/kotlin/repo/BoardRepository.kt` / `DefaultBoardRepository`
- upper-layer behavior for create/reply/del/deleteByUser/vote

9. `shared/src/commonMain/kotlin/service/HistoryRefresher.kt`
- long history window rotation
- multi-board mixes
- skip-list persistence behavior
- partial failure aggregation cases

### Medium / Low Priority

10. `shared/src/commonMain/kotlin/state/AppStateStore.kt`
- more setter rollback/error paths
- broader persistence edge cases

11. `shared/src/commonMain/kotlin/ui/board/CookieManagementScreen.kt`
- reload/delete/clear UI state transitions

12. `shared/src/commonMain/kotlin/com/valoser/futacha/shared/service/ThreadSaveService.kt`
- larger partial-failure scenarios
- metadata/index consistency on more edge cases

13. parser fixture breadth
- more real-world HTML variants for catalog/thread parsers

## Platform-Specific Test Gaps

These are still largely untested:

- `shared/src/androidMain/kotlin/util/FileSystem.android.kt`
- `shared/src/iosMain/kotlin/util/FileSystem.ios.kt`
- `shared/src/androidMain/kotlin/util/UrlLauncher.android.kt`
- `shared/src/iosMain/kotlin/util/UrlLauncher.ios.kt`
- `shared/src/androidMain/kotlin/ui/board/PlatformVideoPlayer.android.kt`
- `shared/src/iosMain/kotlin/ui/board/PlatformVideoPlayer.ios.kt`
- `shared/src/iosMain/kotlin/util/ImagePicker.ios.kt`
- `shared/src/iosMain/kotlin/MainViewController.kt`
- `shared/src/iosMain/kotlin/background/BackgroundRefreshManager.kt`

## Recommended Next Order

1. `CatalogScreen` settings/drawer/mode-search support extraction
2. `SavedThreadsScreen` fake repository integration tests
3. `FutachaApp` navigation/state integration tests
4. first real Android unit/instrumented test
5. remaining `ThreadScreen` UI-state extraction

## Working Rule For Next Pass

Prefer this sequence when continuing:

- extract pure/state-composition helpers from large Compose files
- add `commonTest` coverage first
- only then add Android/UI/instrumented tests where common tests cannot cover behavior
