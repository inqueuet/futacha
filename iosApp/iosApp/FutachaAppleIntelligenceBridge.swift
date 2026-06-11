import Foundation

#if canImport(FoundationModels)
import FoundationModels
#endif

#if canImport(UIKit)
import UIKit
#endif

@objc(FutachaAppleIntelligenceBridge)
final class FutachaAppleIntelligenceBridge: NSObject {
    private static let availabilityKey = "futacha_apple_intelligence_available"
    private static let reasonKey = "futacha_apple_intelligence_unavailable_reason"
    private static let summaryNotificationName = Notification.Name("futacha.appleIntelligence.summary.requested")
    private static let summaryResponseNotificationName = Notification.Name("futacha.appleIntelligence.summary.completed")
    private static let summaryCancelNotificationName = Notification.Name("futacha.appleIntelligence.summary.cancelled")
    private static let summaryRequestIdKey = "futacha_apple_intelligence_summary_request_id"
    private static let summaryRequestTitleKey = "futacha_apple_intelligence_summary_request_title"
    private static let summaryRequestTextKey = "futacha_apple_intelligence_summary_request_text"
    private static let summaryResponseIdKey = "futacha_apple_intelligence_summary_response_id"
    private static let summaryResponseTextKey = "futacha_apple_intelligence_summary_response_text"
    private static let summaryResponseErrorKey = "futacha_apple_intelligence_summary_response_error"
    private static let moderationNotificationName = Notification.Name("futacha.appleIntelligence.postModeration.requested")
    private static let moderationResponseNotificationName = Notification.Name("futacha.appleIntelligence.postModeration.completed")
    private static let moderationCancelNotificationName = Notification.Name("futacha.appleIntelligence.postModeration.cancelled")
    private static let moderationRequestIdKey = "futacha_apple_intelligence_moderation_request_id"
    private static let moderationRequestTextKey = "futacha_apple_intelligence_moderation_request_text"
    private static let moderationResponseIdKey = "futacha_apple_intelligence_moderation_response_id"
    private static let moderationResponseTextKey = "futacha_apple_intelligence_moderation_response_text"
    private static let moderationResponseErrorKey = "futacha_apple_intelligence_moderation_response_error"
    private static var summaryObserver: NSObjectProtocol?
    private static var summaryCancelObserver: NSObjectProtocol?
    private static var moderationObserver: NSObjectProtocol?
    private static var moderationCancelObserver: NSObjectProtocol?
    private static var appActiveObserver: NSObjectProtocol?
    private static let activeTaskQueue = DispatchQueue(label: "com.valoser.futacha.appleIntelligence.activeTasks")
    private static var activeTasks: [String: Task<Void, Never>] = [:]
    private static var pendingCancelledRequestIds: Set<String> = []
    private static var availabilityRefreshTask: Task<Void, Never>?
    private static var availabilityRefreshGeneration = 0
    private static var activeSummaryRequestId: String?
    private static var activeModerationRequestId: String?
    private static let modelResponseTimeoutNanoseconds: UInt64 = 45_000_000_000

    private final class AiTaskStartGate {
        private let lock = NSLock()
        private var continuation: CheckedContinuation<Void, Never>?
        private var isOpened = false

        func wait() async {
            await withCheckedContinuation { continuation in
                lock.lock()
                if isOpened {
                    lock.unlock()
                    continuation.resume()
                } else {
                    self.continuation = continuation
                    lock.unlock()
                }
            }
        }

        func open() {
            lock.lock()
            isOpened = true
            let continuation = continuation
            self.continuation = nil
            lock.unlock()
            continuation?.resume()
        }
    }

    static func refreshAvailability() {
        UserDefaults.standard.set(isThreadSummaryAvailable().boolValue, forKey: availabilityKey)
        UserDefaults.standard.set(unavailableReason() as String, forKey: reasonKey)
    }

    static func refreshAvailabilityAsync() {
        activeTaskQueue.async {
            availabilityRefreshGeneration += 1
            let generation = availabilityRefreshGeneration
            let taskToCancel = availabilityRefreshTask
            availabilityRefreshTask = Task {
                await Task.yield()
                guard !Task.isCancelled else { return }
                refreshAvailability()
                activeTaskQueue.async {
                    if availabilityRefreshGeneration == generation {
                        availabilityRefreshTask = nil
                    }
                }
            }
            taskToCancel?.cancel()
        }
    }

    static func installAiBridge() {
        refreshAvailabilityAsync()
        if summaryObserver != nil {
            return
        }
        summaryObserver = NotificationCenter.default.addObserver(
            forName: summaryNotificationName,
            object: nil,
            queue: nil
        ) { notification in
            handleSummaryRequest(notification)
        }
        summaryCancelObserver = NotificationCenter.default.addObserver(
            forName: summaryCancelNotificationName,
            object: nil,
            queue: nil
        ) { notification in
            cancelAiTask(requestId: notification.userInfo?[summaryRequestIdKey] as? String)
        }
        moderationObserver = NotificationCenter.default.addObserver(
            forName: moderationNotificationName,
            object: nil,
            queue: nil
        ) { notification in
            handleModerationRequest(notification)
        }
        moderationCancelObserver = NotificationCenter.default.addObserver(
            forName: moderationCancelNotificationName,
            object: nil,
            queue: nil
        ) { notification in
            cancelAiTask(requestId: notification.userInfo?[moderationRequestIdKey] as? String)
        }
        #if canImport(UIKit)
        appActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: nil
        ) { _ in
            refreshAvailabilityAsync()
        }
        #endif
    }

    @objc static func isThreadSummaryAvailable() -> NSNumber {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return NSNumber(value: SystemLanguageModel.default.isAvailable)
        }
        return NSNumber(value: false)
        #else
        return NSNumber(value: false)
        #endif
    }

    @objc static func unavailableReason() -> NSString {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return reasonForUnavailableModel()
        }
        return "Apple Intelligence 要約には iOS 26 以降が必要です。"
        #else
        return "このXcode/SDKでは Foundation Models framework を利用できません。"
        #endif
    }

    private static func handleSummaryRequest(_ notification: Notification) {
        guard let requestId = notification.userInfo?[summaryRequestIdKey] as? String, !requestId.isEmpty else {
            return
        }
        let sourceText = notification.userInfo?[summaryRequestTextKey] as? String ?? ""
        let startGate = AiTaskStartGate()
        let task = Task {
            defer { removeAiTask(requestId: requestId) }
            await startGate.wait()
            guard !Task.isCancelled else { return }
            do {
                let summary = try await generateThreadSummary(sourceText: sourceText)
                guard !Task.isCancelled else { return }
                postAiResponse(
                    name: summaryResponseNotificationName,
                    requestId: requestId,
                    requestIdKey: summaryResponseIdKey,
                    text: summary,
                    textKey: summaryResponseTextKey,
                    error: nil,
                    errorKey: summaryResponseErrorKey
                )
            } catch {
                guard !Task.isCancelled else { return }
                postAiResponse(
                    name: summaryResponseNotificationName,
                    requestId: requestId,
                    requestIdKey: summaryResponseIdKey,
                    text: nil,
                    textKey: summaryResponseTextKey,
                    error: error.localizedDescription,
                    errorKey: summaryResponseErrorKey
                )
            }
        }
        storeAiTask(requestId: requestId, task: task, kind: .summary)
        startGate.open()
    }

    private static func handleModerationRequest(_ notification: Notification) {
        guard let requestId = notification.userInfo?[moderationRequestIdKey] as? String, !requestId.isEmpty else {
            return
        }
        let sourceText = notification.userInfo?[moderationRequestTextKey] as? String ?? ""
        let startGate = AiTaskStartGate()
        let task = Task {
            defer { removeAiTask(requestId: requestId) }
            await startGate.wait()
            guard !Task.isCancelled else { return }
            do {
                let response = try await generatePostModeration(sourceText: sourceText)
                guard !Task.isCancelled else { return }
                postAiResponse(
                    name: moderationResponseNotificationName,
                    requestId: requestId,
                    requestIdKey: moderationResponseIdKey,
                    text: response,
                    textKey: moderationResponseTextKey,
                    error: nil,
                    errorKey: moderationResponseErrorKey
                )
            } catch {
                guard !Task.isCancelled else { return }
                postAiResponse(
                    name: moderationResponseNotificationName,
                    requestId: requestId,
                    requestIdKey: moderationResponseIdKey,
                    text: nil,
                    textKey: moderationResponseTextKey,
                    error: error.localizedDescription,
                    errorKey: moderationResponseErrorKey
                )
            }
        }
        storeAiTask(requestId: requestId, task: task, kind: .moderation)
        startGate.open()
    }

    private enum AiTaskKind {
        case summary
        case moderation
    }

    private static func storeAiTask(requestId: String, task: Task<Void, Never>, kind: AiTaskKind) {
        var tasksToCancel: [Task<Void, Never>] = []
        activeTaskQueue.sync {
            switch kind {
            case .summary:
                if let previousRequestId = activeSummaryRequestId, previousRequestId != requestId,
                   let previousTask = activeTasks.removeValue(forKey: previousRequestId) {
                    tasksToCancel.append(previousTask)
                }
                activeSummaryRequestId = requestId
            case .moderation:
                if let previousRequestId = activeModerationRequestId, previousRequestId != requestId,
                   let previousTask = activeTasks.removeValue(forKey: previousRequestId) {
                    tasksToCancel.append(previousTask)
                }
                activeModerationRequestId = requestId
            }
            if pendingCancelledRequestIds.remove(requestId) != nil {
                tasksToCancel.append(task)
                if activeSummaryRequestId == requestId {
                    activeSummaryRequestId = nil
                }
                if activeModerationRequestId == requestId {
                    activeModerationRequestId = nil
                }
            } else {
                if let replacedTask = activeTasks.updateValue(task, forKey: requestId) {
                    tasksToCancel.append(replacedTask)
                }
            }
        }
        tasksToCancel.forEach { $0.cancel() }
    }

    private static func removeAiTask(requestId: String) {
        activeTaskQueue.sync {
            activeTasks.removeValue(forKey: requestId)
            pendingCancelledRequestIds.remove(requestId)
            if activeSummaryRequestId == requestId {
                activeSummaryRequestId = nil
            }
            if activeModerationRequestId == requestId {
                activeModerationRequestId = nil
            }
        }
    }

    private static func cancelAiTask(requestId: String?) {
        guard let requestId, !requestId.isEmpty else {
            return
        }
        let task = activeTaskQueue.sync {
            let task = activeTasks.removeValue(forKey: requestId)
            if task == nil {
                pendingCancelledRequestIds.insert(requestId)
            }
            if activeSummaryRequestId == requestId {
                activeSummaryRequestId = nil
            }
            if activeModerationRequestId == requestId {
                activeModerationRequestId = nil
            }
            return task
        }
        task?.cancel()
    }

    private static func postAiResponse(
        name: Notification.Name,
        requestId: String,
        requestIdKey: String,
        text: String?,
        textKey: String,
        error: String?,
        errorKey: String
    ) {
        var userInfo: [String: String] = [requestIdKey: requestId]
        if let text {
            userInfo[textKey] = text
        }
        if let error {
            userInfo[errorKey] = error
        }
        NotificationCenter.default.post(name: name, object: nil, userInfo: userInfo)
    }

    private static func generateThreadSummary(sourceText: String) async throws -> String {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let model = SystemLanguageModel.default
            guard model.isAvailable else {
                throw NSError(
                    domain: "FutachaAppleIntelligenceBridge",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: reasonForUnavailableModel()]
                )
            }
            let session = LanguageModelSession(
                model: model,
                instructions: """
                あなたは日本語掲示板スレッドを端末内で要約するアシスタントです。
                個人情報や攻撃的表現を増幅せず、投稿内容から読み取れる要点だけを短くまとめてください。
                出力は1行目を短い見出し、続く行を最大4個の箇条書きにしてください。全体で1000字以内にしてください。
                """
            )
            let prompt = """
            投稿本文:
            \(sourceText.prefix(10_000))
            """
            let response = try await withModelResponseTimeout {
                try await session.respond(
                    to: prompt,
                    options: GenerationOptions(
                        sampling: .greedy,
                        temperature: 0.2,
                        maximumResponseTokens: 700
                    )
                )
            }
            return response.content
        }
        throw NSError(
            domain: "FutachaAppleIntelligenceBridge",
            code: 2,
            userInfo: [NSLocalizedDescriptionKey: "Apple Intelligence 要約には iOS 26 以降が必要です。"]
        )
        #else
        throw NSError(
            domain: "FutachaAppleIntelligenceBridge",
            code: 3,
            userInfo: [NSLocalizedDescriptionKey: "このXcode/SDKでは Foundation Models framework を利用できません。"]
        )
        #endif
    }

    private static func generatePostModeration(sourceText: String) async throws -> String {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let model = SystemLanguageModel(useCase: .contentTagging)
            guard model.isAvailable else {
                throw NSError(
                    domain: "FutachaAppleIntelligenceBridge",
                    code: 4,
                    userInfo: [NSLocalizedDescriptionKey: reasonForUnavailableModel()]
                )
            }
            let session = LanguageModelSession(
                model: model,
                instructions: """
                あなたは日本語掲示板スレッドの投稿を端末内で分類するアシスタントです。
                明確な荒らし、スパム、連投、脅迫、嫌がらせ、個人や集団への攻撃的な内容、無関係な破壊的投稿だけを非表示候補にしてください。
                通常の反対意見、冗談、批判、短文、引用、荒い口調だけでは非表示にしないでください。
                先頭投稿は非表示候補にしないでください。不確かな場合は空文字を返してください。非表示候補は最大8件までにしてください。
                非表示候補がない場合は空文字を返してください。
                出力は非表示候補のみ、1行ごとに postId<TAB>HIDE<TAB>短い日本語理由 の形式にしてください。
                """
            )
            let prompt = """
            投稿一覧:
            \(sourceText.prefix(10_000))
            """
            let response = try await withModelResponseTimeout {
                try await session.respond(
                    to: prompt,
                    options: GenerationOptions(
                        sampling: .greedy,
                        temperature: 0.0,
                        maximumResponseTokens: 420
                    )
                )
            }
            return response.content
        }
        throw NSError(
            domain: "FutachaAppleIntelligenceBridge",
            code: 5,
            userInfo: [NSLocalizedDescriptionKey: "Apple Intelligence 投稿分類には iOS 26 以降が必要です。"]
        )
        #else
        throw NSError(
            domain: "FutachaAppleIntelligenceBridge",
            code: 6,
            userInfo: [NSLocalizedDescriptionKey: "このXcode/SDKでは Foundation Models framework を利用できません。"]
        )
        #endif
    }

    private static func withModelResponseTimeout<T>(
        operation: @escaping () async throws -> T
    ) async throws -> T {
        let operationTask = Task {
            try await operation()
        }
        let timeoutTask = Task<T, Error> {
            try await Task.sleep(nanoseconds: modelResponseTimeoutNanoseconds)
            throw NSError(
                domain: "FutachaAppleIntelligenceBridge",
                code: 10,
                userInfo: [NSLocalizedDescriptionKey: "Apple Intelligence request timed out."]
            )
        }
        return try await withTaskCancellationHandler {
            defer {
                operationTask.cancel()
                timeoutTask.cancel()
            }
            return try await withThrowingTaskGroup(of: T.self) { group in
                group.addTask {
                    try await operationTask.value
                }
                group.addTask {
                    try await timeoutTask.value
                }
                guard let value = try await group.next() else {
                    throw NSError(
                        domain: "FutachaAppleIntelligenceBridge",
                        code: 11,
                        userInfo: [NSLocalizedDescriptionKey: "Apple Intelligence request was cancelled."]
                    )
                }
                group.cancelAll()
                return value
            }
        } onCancel: {
            operationTask.cancel()
            timeoutTask.cancel()
        }
    }

    private static func reasonForUnavailableModel() -> NSString {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                return ""
            case .unavailable(.deviceNotEligible):
                return "この端末は Apple Intelligence に対応していません。"
            case .unavailable(.appleIntelligenceNotEnabled):
                return "Apple Intelligence が有効化されていません。"
            case .unavailable(.modelNotReady):
                return "Apple Intelligence のモデル準備が完了していません。"
            @unknown default:
                return "Apple Intelligence を利用できません。"
            }
        }
        return "Apple Intelligence 要約には iOS 26 以降が必要です。"
        #else
        return "このXcode/SDKでは Foundation Models framework を利用できません。"
        #endif
    }
}
