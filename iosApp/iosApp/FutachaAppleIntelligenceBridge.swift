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
    private static let summaryRequestIdKey = "futacha_apple_intelligence_summary_request_id"
    private static let summaryRequestTitleKey = "futacha_apple_intelligence_summary_request_title"
    private static let summaryRequestTextKey = "futacha_apple_intelligence_summary_request_text"
    private static let summaryResponseIdKey = "futacha_apple_intelligence_summary_response_id"
    private static let summaryResponseTextKey = "futacha_apple_intelligence_summary_response_text"
    private static let summaryResponseErrorKey = "futacha_apple_intelligence_summary_response_error"
    private static let moderationNotificationName = Notification.Name("futacha.appleIntelligence.postModeration.requested")
    private static let moderationRequestIdKey = "futacha_apple_intelligence_moderation_request_id"
    private static let moderationRequestTextKey = "futacha_apple_intelligence_moderation_request_text"
    private static let moderationResponseIdKey = "futacha_apple_intelligence_moderation_response_id"
    private static let moderationResponseTextKey = "futacha_apple_intelligence_moderation_response_text"
    private static let moderationResponseErrorKey = "futacha_apple_intelligence_moderation_response_error"
    private static var summaryObserver: NSObjectProtocol?
    private static var moderationObserver: NSObjectProtocol?
    private static var appActiveObserver: NSObjectProtocol?

    static func refreshAvailability() {
        UserDefaults.standard.set(isThreadSummaryAvailable().boolValue, forKey: availabilityKey)
        UserDefaults.standard.set(unavailableReason() as String, forKey: reasonKey)
    }

    static func installAiBridge() {
        refreshAvailability()
        if summaryObserver != nil {
            return
        }
        summaryObserver = NotificationCenter.default.addObserver(
            forName: summaryNotificationName,
            object: nil,
            queue: nil
        ) { _ in
            handleSummaryRequest()
        }
        moderationObserver = NotificationCenter.default.addObserver(
            forName: moderationNotificationName,
            object: nil,
            queue: nil
        ) { _ in
            handleModerationRequest()
        }
        #if canImport(UIKit)
        appActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: nil
        ) { _ in
            refreshAvailability()
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

    private static func handleSummaryRequest() {
        let defaults = UserDefaults.standard
        guard let requestId = defaults.string(forKey: summaryRequestIdKey), !requestId.isEmpty else {
            return
        }
        let title = defaults.string(forKey: summaryRequestTitleKey) ?? ""
        let sourceText = defaults.string(forKey: summaryRequestTextKey) ?? ""
        defaults.removeObject(forKey: summaryRequestIdKey)
        defaults.removeObject(forKey: summaryRequestTitleKey)
        defaults.removeObject(forKey: summaryRequestTextKey)
        Task {
            do {
                let summary = try await generateThreadSummary(title: title, sourceText: sourceText)
                defaults.set(requestId, forKey: summaryResponseIdKey)
                defaults.set(summary, forKey: summaryResponseTextKey)
                defaults.removeObject(forKey: summaryResponseErrorKey)
            } catch {
                defaults.set(requestId, forKey: summaryResponseIdKey)
                defaults.removeObject(forKey: summaryResponseTextKey)
                defaults.set(error.localizedDescription, forKey: summaryResponseErrorKey)
            }
        }
    }

    private static func handleModerationRequest() {
        let defaults = UserDefaults.standard
        guard let requestId = defaults.string(forKey: moderationRequestIdKey), !requestId.isEmpty else {
            return
        }
        let sourceText = defaults.string(forKey: moderationRequestTextKey) ?? ""
        defaults.removeObject(forKey: moderationRequestIdKey)
        defaults.removeObject(forKey: moderationRequestTextKey)
        Task {
            do {
                let response = try await generatePostModeration(sourceText: sourceText)
                defaults.set(requestId, forKey: moderationResponseIdKey)
                defaults.set(response, forKey: moderationResponseTextKey)
                defaults.removeObject(forKey: moderationResponseErrorKey)
            } catch {
                defaults.set(requestId, forKey: moderationResponseIdKey)
                defaults.removeObject(forKey: moderationResponseTextKey)
                defaults.set(error.localizedDescription, forKey: moderationResponseErrorKey)
            }
        }
    }

    private static func generateThreadSummary(title: String, sourceText: String) async throws -> String {
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
                出力は1行目を短い見出し、続く行を最大4個の箇条書きにしてください。
                """
            )
            let prompt = """
            スレタイ: \(title.isEmpty ? "なし" : title)

            投稿本文:
            \(sourceText.prefix(12_000))
            """
            let response = try await session.respond(
                to: prompt,
                options: GenerationOptions(
                    sampling: .greedy,
                    temperature: 0.2,
                    maximumResponseTokens: 320
                )
            )
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
                明確な荒らし、スパム、連投、脅迫、嫌がらせ、無関係な破壊的投稿だけを非表示候補にしてください。
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
            let response = try await session.respond(
                to: prompt,
                options: GenerationOptions(
                    sampling: .greedy,
                    temperature: 0.0,
                    maximumResponseTokens: 420
                )
            )
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
