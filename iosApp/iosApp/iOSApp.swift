import SwiftUI
import UIKit
import shared
import GoogleMobileAds
import FirebaseCore

#if canImport(AppIntents)
import AppIntents
#endif

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FutachaAppleIntelligenceBridge.installAiBridge()
        MainViewControllerKt.registerIosBackgroundRefreshTask()
        #if canImport(WatchConnectivity)
        FutachaWatchConnectivityManager.shared.start()
        #endif
        if let _ = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") {
            FirebaseApp.configure()
        }
        MobileAds.shared.start(completionHandler: nil)
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    retryPendingFutachaAiDeepLinks()
                }
                .onOpenURL { url in
                    if !submitFutachaAiDeepLink(url.absoluteString) {
                        NSLog("Failed to enqueue Futacha AI deep link: %@", url.absoluteString)
                    }
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        retryPendingFutachaAiDeepLinks()
                    }
                }
        }
    }
}

@discardableResult
private func submitFutachaAiDeepLink(_ raw: String) -> Bool {
    FutachaAiDeepLinkSubmitter.shared.submit(raw)
}

private func retryPendingFutachaAiDeepLinks() {
    FutachaAiDeepLinkSubmitter.shared.retryPendingSoon()
}

private struct PendingFutachaAiDeepLink {
    let raw: String
    var attempts: Int
}

private final class FutachaAiDeepLinkSubmitter {
    static let shared = FutachaAiDeepLinkSubmitter()

    private let maxPendingCount = 16
    private let maxRetryAttempts = 6
    private let retryDelaySeconds: TimeInterval = 0.75
    private var pending: [PendingFutachaAiDeepLink] = []
    private var retryWorkItem: DispatchWorkItem?

    private init() {}

    @discardableResult
    func submit(_ raw: String) -> Bool {
        if !Thread.isMainThread {
            DispatchQueue.main.async { [weak self] in
                _ = self?.submit(raw)
            }
            return true
        }

        if FutachaAiCommandBridge.shared.enqueueDeepLink(raw: raw, source: "ios") {
            return true
        }

        guard shouldRetry(raw) else {
            return false
        }
        enqueuePending(raw)
        scheduleRetry()
        return true
    }

    func retryPendingSoon() {
        if !Thread.isMainThread {
            DispatchQueue.main.async { [weak self] in
                self?.retryPendingSoon()
            }
            return
        }
        scheduleRetry(delay: 0.1)
    }

    private func shouldRetry(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.lowercased().hasPrefix("futacha://ai") else {
            return false
        }
        guard let components = URLComponents(string: trimmed) else {
            return false
        }
        if components.queryItems?.contains(where: { item in
            let name = item.name
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
                .filter { $0 != "_" && $0 != "-" }
            return (name == "action" || name == "command") &&
                !(item.value ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }) == true {
            return true
        }
        return components.path
            .split(separator: "/")
            .contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private func enqueuePending(_ raw: String) {
        if pending.contains(where: { $0.raw == raw }) {
            return
        }
        if pending.count >= maxPendingCount {
            pending.removeFirst()
        }
        pending.append(PendingFutachaAiDeepLink(raw: raw, attempts: 0))
    }

    private func scheduleRetry(delay: TimeInterval? = nil) {
        guard !pending.isEmpty else {
            return
        }
        if delay != nil {
            retryWorkItem?.cancel()
            retryWorkItem = nil
        }
        guard retryWorkItem == nil else {
            return
        }
        let workItem = DispatchWorkItem { [weak self] in
            self?.flushPending()
        }
        retryWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + (delay ?? retryDelaySeconds), execute: workItem)
    }

    private func flushPending() {
        retryWorkItem = nil
        guard !pending.isEmpty else {
            return
        }

        var remaining: [PendingFutachaAiDeepLink] = []
        for item in pending {
            if FutachaAiCommandBridge.shared.enqueueDeepLink(raw: item.raw, source: "ios-retry") {
                continue
            }
            var next = item
            next.attempts += 1
            if next.attempts < maxRetryAttempts {
                remaining.append(next)
            } else {
                NSLog("Dropped pending Futacha AI deep link after retries: %@", item.raw)
            }
        }
        pending = remaining
        scheduleRetry()
    }
}

@discardableResult
private func submitFutachaAiIntentCommand(
    action: String,
    board: String = "",
    thread: String = "",
    query: String = "",
    url: String = "",
    value: String = "",
    name: String = "",
    email: String = "",
    subject: String = "",
    comment: String = "",
    password: String = ""
) -> Bool {
    FutachaAiCommandBridge.shared.enqueueIntentCommand(
        actionId: action,
        board: board,
        thread: thread,
        query: query,
        url: url,
        value: value,
        name: name,
        email: email,
        subject: subject,
        comment: comment,
        password: password,
        source: "ios-app-intents"
    )
}

#if canImport(AppIntents)
@available(iOS 16.0, *)
enum FutachaAppActionID: String, AppEnum {
    case openBoardList = "open_board_list"
    case openBoard = "open_board"
    case searchOpenBoard = "search_open_board"
    case refreshCurrentBoard = "refresh_current_board"
    case refreshCatalog = "refresh_catalog"
    case openHistoryDrawer = "open_history_drawer"
    case refreshHistory = "refresh_history"
    case openThread = "open_thread"
    case openThreadUrl = "open_thread_url"
    case refreshCurrentThread = "refresh_current_thread"
    case threadTop = "thread_top"
    case threadBottom = "thread_bottom"
    case threadReadAloudStart = "thread_read_aloud_start"
    case threadReadAloudPause = "thread_read_aloud_pause"
    case threadReadAloudStop = "thread_read_aloud_stop"
    case threadReadAloudNext = "thread_read_aloud_next"
    case threadReadAloudPrevious = "thread_read_aloud_previous"
    case catalogTop = "catalog_top"
    case startCatalogSearch = "start_catalog_search"
    case searchCatalog = "search_catalog"
    case startThreadSearch = "start_thread_search"
    case searchThread = "search_thread"
    case nextSearchResult = "next_search_result"
    case previousSearchResult = "previous_search_result"
    case openGallery = "open_gallery"
    case openSavedThreads = "open_saved_threads"
    case openGlobalSettings = "open_global_settings"
    case openCatalogSettings = "open_catalog_settings"
    case openThreadSettings = "open_thread_settings"
    case privacyOn = "privacy_on"
    case privacyOff = "privacy_off"
    case backgroundRefreshOn = "background_refresh_on"
    case backgroundRefreshOff = "background_refresh_off"
    case threadSummaryOn = "thread_summary_on"
    case threadSummaryOff = "thread_summary_off"
    case aiPostFilterOn = "ai_post_filter_on"
    case aiPostFilterOff = "ai_post_filter_off"
    case openCatalogDisplay = "open_catalog_display"
    case setCatalogMode = "set_catalog_mode"
    case openNgManagement = "open_ng_management"
    case openWatchWords = "open_watch_words"
    case addWatchWord = "add_watch_word"
    case addNgWord = "add_ng_word"
    case addNgHeader = "add_ng_header"
    case openBoardExternal = "open_board_external"
    case openThreadExternal = "open_thread_external"
    case openVersionInfo = "open_version_info"
    case openCookieManagement = "open_cookie_management"
    case openFileManagerSettings = "open_file_manager_settings"
    case saveCurrentThread = "save_current_thread"
    case saveThread = "save_thread"
    case deleteHistoryEntry = "delete_history_entry"
    case clearHistory = "clear_history"
    case deleteSavedThread = "delete_saved_thread"
    case clearSavedThreads = "clear_saved_threads"
    case addBoard = "add_board"
    case deleteBoard = "delete_board"
    case draftReply = "draft_reply"
    case draftThread = "draft_thread"

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Futacha操作")

    static var caseDisplayRepresentations: [FutachaAppActionID: DisplayRepresentation] = [
        .openBoardList: "板一覧を開く",
        .openBoard: "板を開く",
        .searchOpenBoard: "板名で検索して開く",
        .refreshCurrentBoard: "現在の板を更新",
        .refreshCatalog: "カタログを更新",
        .openHistoryDrawer: "履歴ドロワーを開く",
        .refreshHistory: "履歴を更新",
        .openThread: "スレを開く",
        .openThreadUrl: "URLからスレを開く",
        .refreshCurrentThread: "現在のスレを更新",
        .threadTop: "スレの先頭へ移動",
        .threadBottom: "スレの末尾へ移動",
        .threadReadAloudStart: "スレ読み上げを開始",
        .threadReadAloudPause: "スレ読み上げを一時停止",
        .threadReadAloudStop: "スレ読み上げを停止",
        .threadReadAloudNext: "次の読み上げ位置へ移動",
        .threadReadAloudPrevious: "前の読み上げ位置へ移動",
        .catalogTop: "カタログの先頭へ移動",
        .startCatalogSearch: "カタログ検索を開始",
        .searchCatalog: "カタログで検索",
        .startThreadSearch: "スレ内検索を開始",
        .searchThread: "スレ内で検索",
        .nextSearchResult: "次の検索結果へ移動",
        .previousSearchResult: "前の検索結果へ移動",
        .openGallery: "ギャラリーを開く",
        .openSavedThreads: "保存済みスレ一覧を開く",
        .openGlobalSettings: "グローバル設定を開く",
        .openCatalogSettings: "カタログ設定を開く",
        .openThreadSettings: "スレ設定を開く",
        .privacyOn: "プライバシーフィルタON",
        .privacyOff: "プライバシーフィルタOFF",
        .backgroundRefreshOn: "バックグラウンド更新ON",
        .backgroundRefreshOff: "バックグラウンド更新OFF",
        .threadSummaryOn: "スレ要約モードON",
        .threadSummaryOff: "スレ要約モードOFF",
        .aiPostFilterOn: "荒らし非表示ON",
        .aiPostFilterOff: "荒らし非表示OFF",
        .openCatalogDisplay: "カタログ表示設定を開く",
        .setCatalogMode: "カタログモードを変更",
        .openNgManagement: "NG管理を開く",
        .openWatchWords: "監視ワード管理を開く",
        .addWatchWord: "監視ワードを追加",
        .addNgWord: "NGワードを追加",
        .addNgHeader: "NGヘッダーを追加",
        .openBoardExternal: "外部アプリで板を開く",
        .openThreadExternal: "外部アプリでスレを開く",
        .openVersionInfo: "バージョン情報を表示",
        .openCookieManagement: "Cookie管理を開く",
        .openFileManagerSettings: "優先ファイラー設定を開く",
        .saveCurrentThread: "現在のスレを保存",
        .saveThread: "指定スレを保存",
        .deleteHistoryEntry: "履歴エントリを削除",
        .clearHistory: "履歴を全削除",
        .deleteSavedThread: "保存済みスレを削除",
        .clearSavedThreads: "保存済みスレを全削除",
        .addBoard: "板を追加",
        .deleteBoard: "板を削除",
        .draftReply: "レス投稿の下書き",
        .draftThread: "スレ立て下書き"
    ]
}

@available(iOS 16.0, *)
private let futachaConfirmActionIds: Set<String> = [
    "save_current_thread",
    "save_thread",
    "delete_history_entry",
    "clear_history",
    "delete_saved_thread",
    "clear_saved_threads",
    "add_board",
    "delete_board",
    "draft_reply",
    "draft_thread"
]

@available(iOS 16.0, *)
private func futachaIntentDialog(action: String, accepted: Bool) -> IntentDialog {
    if !accepted {
        return IntentDialog("操作を受け付けられませんでした。futachaを開いてから、もう一度実行してください。")
    }
    if futachaConfirmActionIds.contains(action) {
        return IntentDialog("操作を受け付けました。\(futachaConfirmationReason(action: action))、アプリ内で確認してから実行します。")
    }
    return IntentDialog("操作を受け付けました。")
}

@available(iOS 16.0, *)
private func futachaConfirmationReason(action: String) -> String {
    switch action {
    case "save_current_thread", "save_thread":
        return "スレ保存に関係するため"
    case "delete_history_entry", "clear_history", "delete_saved_thread", "clear_saved_threads", "delete_board":
        return "削除に関係するため"
    case "draft_reply", "draft_thread":
        return "投稿下書きを作成するため"
    case "add_board":
        return "板リストを変更するため"
    default:
        return "データ変更に関係するため"
    }
}

@available(iOS 16.0, *)
struct OpenFutachaIntent: AppIntent {
    static var title: LocalizedStringResource = "Futachaを開く"
    static var description = IntentDescription("板、スレ、保存済みスレ一覧などを開きます。")
    static var openAppWhenRun = true

    @Parameter(title: "操作", default: .openBoardList)
    var action: FutachaAppActionID

    @Parameter(title: "板", default: "")
    var board: String

    @Parameter(title: "スレ番号", default: "")
    var thread: String

    @Parameter(title: "URL", default: "")
    var url: String

    func perform() async throws -> some IntentResult {
        let actionId = action.rawValue
        let accepted = submitFutachaAiIntentCommand(action: actionId, board: board, thread: thread, url: url)
        return .result(dialog: futachaIntentDialog(action: actionId, accepted: accepted))
    }
}

@available(iOS 16.0, *)
struct SearchFutachaIntent: AppIntent {
    static var title: LocalizedStringResource = "Futachaで検索"
    static var description = IntentDescription("カタログまたはスレ内検索を開始するために対象画面を開きます。")
    static var openAppWhenRun = true

    @Parameter(title: "検索語")
    var query: String

    @Parameter(title: "板", default: "")
    var board: String

    @Parameter(title: "スレ番号", default: "")
    var thread: String

    @Parameter(title: "スレ内検索", default: false)
    var inThread: Bool

    func perform() async throws -> some IntentResult {
        let action = inThread ? "search_thread" : "search_catalog"
        let accepted = submitFutachaAiIntentCommand(action: action, board: board, thread: thread, query: query)
        return .result(dialog: futachaIntentDialog(action: action, accepted: accepted))
    }
}

@available(iOS 16.0, *)
struct RefreshFutachaIntent: AppIntent {
    static var title: LocalizedStringResource = "Futachaを更新"
    static var description = IntentDescription("履歴、カタログ、現在のスレなどを更新します。")
    static var openAppWhenRun = true

    @Parameter(title: "操作", default: .refreshHistory)
    var action: FutachaAppActionID

    func perform() async throws -> some IntentResult {
        let actionId = action.rawValue
        let accepted = submitFutachaAiIntentCommand(action: actionId)
        return .result(dialog: futachaIntentDialog(action: actionId, accepted: accepted))
    }
}

@available(iOS 16.0, *)
struct ConfigureFutachaIntent: AppIntent {
    static var title: LocalizedStringResource = "Futacha設定を変更"
    static var description = IntentDescription("プライバシーフィルタ、バックグラウンド更新、監視ワード、NGワードなどを変更します。")
    static var openAppWhenRun = true

    @Parameter(title: "操作", default: .openGlobalSettings)
    var action: FutachaAppActionID

    @Parameter(title: "値", default: "")
    var value: String

    @Parameter(title: "板", default: "")
    var board: String

    func perform() async throws -> some IntentResult {
        let actionId = action.rawValue
        let accepted = submitFutachaAiIntentCommand(action: actionId, board: board, query: value, value: value)
        return .result(dialog: futachaIntentDialog(action: actionId, accepted: accepted))
    }
}

@available(iOS 16.0, *)
struct ConfirmFutachaActionIntent: AppIntent {
    static var title: LocalizedStringResource = "Futacha確認操作"
    static var description = IntentDescription("保存、削除、投稿下書きなど、アプリ内確認が必要な操作を開始します。")
    static var openAppWhenRun = true

    @Parameter(title: "操作", default: .saveCurrentThread)
    var action: FutachaAppActionID

    @Parameter(title: "板", default: "")
    var board: String

    @Parameter(title: "スレ番号", default: "")
    var thread: String

    @Parameter(title: "URL", default: "")
    var url: String

    @Parameter(title: "名前", default: "")
    var name: String

    @Parameter(title: "メール", default: "")
    var email: String

    @Parameter(title: "件名", default: "")
    var subject: String

    @Parameter(title: "本文", default: "")
    var comment: String

    @Parameter(title: "削除キー", default: "")
    var password: String

    func perform() async throws -> some IntentResult {
        let actionId = action.rawValue
        let accepted = submitFutachaAiIntentCommand(
            action: actionId,
            board: board,
            thread: thread,
            url: url,
            name: name,
            email: email,
            subject: subject,
            comment: comment,
            password: password
        )
        return .result(dialog: futachaIntentDialog(action: actionId, accepted: accepted))
    }
}

@available(iOS 16.0, *)
struct FutachaAppShortcuts: AppShortcutsProvider {
    static var shortcutTileColor: ShortcutTileColor = .teal

    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenFutachaIntent(),
            phrases: [
                "\(.applicationName)を開く",
                "\(.applicationName)の板一覧を開く"
            ],
            shortTitle: "板一覧",
            systemImageName: "list.bullet"
        )
        AppShortcut(
            intent: RefreshFutachaIntent(),
            phrases: [
                "\(.applicationName)を更新",
                "\(.applicationName)の履歴を更新"
            ],
            shortTitle: "履歴更新",
            systemImageName: "arrow.clockwise"
        )
        AppShortcut(
            intent: ConfigureFutachaIntent(),
            phrases: [
                "\(.applicationName)の設定を開く",
                "\(.applicationName)のAI操作設定を開く"
            ],
            shortTitle: "設定",
            systemImageName: "gearshape"
        )
        AppShortcut(
            intent: ConfirmFutachaActionIntent(),
            phrases: [
                "\(.applicationName)でスレを保存",
                "\(.applicationName)の現在のスレを保存"
            ],
            shortTitle: "スレ保存",
            systemImageName: "square.and.arrow.down"
        )
    }
}
#endif
