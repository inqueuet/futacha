import Foundation
import shared

#if canImport(UIKit)
import UIKit
#endif

#if canImport(WatchConnectivity)
import WatchConnectivity

final class FutachaWatchConnectivityManager: NSObject, WCSessionDelegate {
    static let shared = FutachaWatchConnectivityManager()

    private let snapshotKey = "snapshot"
    private let snapshotAckKey = "snapshotAck"
    private let commandKey = "command"
    private let requestSnapshotKey = "requestSnapshot"
    private let commandPayloadMaxBytes = 4 * 1024
    private let snapshotRequestTimeoutSeconds: TimeInterval = 10
    private let maxPendingSnapshotAcks = 8
    private var snapshotRetryWorkItems: [UUID: DispatchWorkItem] = [:]
    private var snapshotTimeoutWorkItem: DispatchWorkItem?
    private var snapshotRequestGeneration = 0
    private var isSnapshotRequestInFlight = false
    private var pendingSnapshotReplyHandlers: [([String: Any]) -> Void] = []
    /// Called once the snapshot request in flight has been sent (or given up).
    private var currentSnapshotCompletions: [() -> Void] = []
    /// Wait for a fresh snapshot built after the request in flight, which may predate their change.
    private var nextSnapshotCompletions: [() -> Void] = []
    private var shouldSendSnapshotAfterCurrentRequest = false
    private var pendingSnapshotAckPayloads: [String: String] = [:]
    private var pendingSnapshotAckOrder: [String] = []
    private let applicationActiveLock = NSLock()
    private var isApplicationActiveCache = false
    private var applicationActiveObserversInstalled = false

    private override init() {
        super.init()
    }

    func start() {
        guard WCSession.isSupported() else {
            return
        }
        installApplicationActiveObserversIfNeeded()
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// `completion` runs on the main queue once a snapshot built after this
    /// call has been handed to WatchConnectivity, or the attempt was given up.
    func sendSnapshotIfAvailable(completion: (() -> Void)? = nil) {
        guard WCSession.isSupported() else {
            completion?()
            return
        }
        requestSnapshot(replyHandler: nil, completion: completion)
    }

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        guard error == nil, activationState == .activated else {
            return
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any]
    ) {
        handleMessage(message, replyHandler: nil)
    }

    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        handleMessage(message, replyHandler: replyHandler)
    }

    func session(
        _ session: WCSession,
        didReceiveUserInfo userInfo: [String: Any] = [:]
    ) {
        handleMessage(userInfo, replyHandler: nil)
    }

    private func handleMessage(
        _ message: [String: Any],
        replyHandler: (([String: Any]) -> Void)?
    ) {
        if let ackId = message[snapshotAckKey] as? String {
            completeSnapshotAck(ackId)
            replyHandler?(["accepted": true])
            return
        }

        if let commandJson = message[commandKey] as? String {
            guard commandJson.utf8.count <= commandPayloadMaxBytes else {
                replyHandler?(["accepted": false, "message": "Command payload is too large."])
                return
            }
            let commandType = watchCommandType(commandJson)
            if isUiDependentCommandType(commandType) && !isApplicationActiveForWatchCommand() {
                replyHandler?(["accepted": false, "message": "iPhoneアプリを開いてから再実行してください。"])
                return
            }
            // A Refresh may arrive while the app is in the background. Hold
            // background time for the whole run: once suspended mid-run, the
            // refresh stayed "running" and every later Refresh was dropped.
            var refreshTask: WatchRefreshBackgroundTask?
            if commandType == "Refresh" {
                guard let task = WatchRefreshBackgroundTask.begin() else {
                    replyHandler?(["accepted": false, "message": "iPhoneアプリを開いてから再実行してください。"])
                    return
                }
                refreshTask = task
            }
            let outcome = MainViewControllerKt.handleIosWatchCommandJsonOutcome(commandJson: commandJson)
            let accepted = outcome != "rejected"
            if let refreshTask {
                if accepted && outcome != "refreshThrottled" {
                    // Covers a run that was started now or one this command joined.
                    // Keep the background time until the refreshed snapshot is
                    // built and sent: both are asynchronous, and ending the task
                    // first let iOS suspend the app before the Watch got it.
                    // The expiry handler still ends the task; end() is idempotent.
                    MainViewControllerKt.invokeWhenIosWatchRefreshIdle { [weak self] in
                        guard let self else {
                            refreshTask.end()
                            return
                        }
                        self.sendSnapshotIfAvailable {
                            refreshTask.end()
                        }
                    }
                } else {
                    refreshTask.end()
                }
            }
            replyHandler?(
                accepted
                    ? ["accepted": true]
                    : ["accepted": false, "message": "iPhone側でコマンドを処理できませんでした。"]
            )
            if accepted {
                if outcome == "refreshThrottled" {
                    // Within the minimum refresh interval nothing new is
                    // fetched; answer once with the current snapshot.
                    sendSnapshotIfAvailable()
                } else if commandType == "Refresh" {
                    scheduleRefreshSnapshotRetries()
                } else {
                    sendSnapshotIfAvailable()
                    scheduleSnapshotRetries()
                }
            }
            return
        }

        if (message[requestSnapshotKey] as? Bool) == true {
            requestSnapshot(replyHandler: replyHandler)
        }
    }

    private func watchCommandType(_ commandJson: String) -> String? {
        guard
            let data = commandJson.data(using: .utf8),
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
            let type = object["type"] as? String
        else {
            return nil
        }
        return type
    }

    private func isUiDependentCommandType(_ type: String?) -> Bool {
        guard let type else {
            return true
        }
        // Pause/stop only reduce activity: they act on the already composed thread
        // screen directly (also while a background-audio reading continues with
        // the app inactive), like the Android watch handler. Start/seek/open
        // still need the active app.
        return type != "Refresh"
            && type != "PauseReadAloudOnPhone"
            && type != "StopReadAloudOnPhone"
    }

    private func isApplicationActiveForWatchCommand() -> Bool {
        #if canImport(UIKit)
        if Thread.isMainThread {
            return UIApplication.shared.applicationState == .active
        }
        // Avoid DispatchQueue.main.sync from the WCSession delegate queue: it
        // stalls watch command handling whenever the main thread is busy and
        // can deadlock if the main thread ever waits on this queue.
        applicationActiveLock.lock()
        defer { applicationActiveLock.unlock() }
        return isApplicationActiveCache
        #else
        return true
        #endif
    }

    private func installApplicationActiveObserversIfNeeded() {
        #if canImport(UIKit)
        let install = { [weak self] in
            guard let self, !self.applicationActiveObserversInstalled else {
                return
            }
            self.applicationActiveObserversInstalled = true
            self.updateApplicationActiveCache(UIApplication.shared.applicationState == .active)
            NotificationCenter.default.addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.updateApplicationActiveCache(true)
            }
            NotificationCenter.default.addObserver(
                forName: UIApplication.willResignActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.updateApplicationActiveCache(false)
            }
        }
        if Thread.isMainThread {
            install()
        } else {
            DispatchQueue.main.async(execute: install)
        }
        #endif
    }

    private func updateApplicationActiveCache(_ isActive: Bool) {
        applicationActiveLock.lock()
        isApplicationActiveCache = isActive
        applicationActiveLock.unlock()
    }

    private func requestSnapshot(
        replyHandler: (([String: Any]) -> Void)?,
        completion: (() -> Void)? = nil
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                replyHandler?(["snapshot": ""])
                completion?()
                return
            }
            if let replyHandler {
                self.pendingSnapshotReplyHandlers.append(replyHandler)
            } else {
                self.shouldSendSnapshotAfterCurrentRequest = true
            }
            guard !self.isSnapshotRequestInFlight else {
                if let completion {
                    self.nextSnapshotCompletions.append(completion)
                }
                return
            }
            if let completion {
                self.currentSnapshotCompletions.append(completion)
            }
            self.isSnapshotRequestInFlight = true
            self.snapshotRequestGeneration += 1
            let requestGeneration = self.snapshotRequestGeneration
            self.scheduleSnapshotTimeout(for: requestGeneration)
            MainViewControllerKt.requestIosWatchSnapshotJson { [weak self] snapshotJson in
                DispatchQueue.main.async {
                    self?.finishSnapshotRequest(payload: snapshotJson ?? "", generation: requestGeneration)
                }
            }
        }
    }

    private func scheduleSnapshotTimeout(for generation: Int) {
        dispatchPrecondition(condition: .onQueue(.main))
        snapshotTimeoutWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            self?.finishSnapshotRequest(payload: "", generation: generation)
        }
        snapshotTimeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + snapshotRequestTimeoutSeconds, execute: workItem)
    }

    private func finishSnapshotRequest(payload: String, generation: Int) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard isSnapshotRequestInFlight, generation == snapshotRequestGeneration else {
            return
        }
        snapshotTimeoutWorkItem?.cancel()
        snapshotTimeoutWorkItem = nil

        let replyHandlers = pendingSnapshotReplyHandlers
        let shouldSendSnapshot = shouldSendSnapshotAfterCurrentRequest || !replyHandlers.isEmpty
        pendingSnapshotReplyHandlers.removeAll()
        shouldSendSnapshotAfterCurrentRequest = false
        isSnapshotRequestInFlight = false

        let hasPayload = !payload.isEmpty
        let ackId = hasPayload && shouldSendSnapshot ? nextSnapshotAckId(for: generation) : nil
        if let ackId {
            registerPendingSnapshotAck(id: ackId, payload: payload)
        }

        let replyPayload: [String: Any] = {
            guard let ackId else {
                return [snapshotKey: payload]
            }
            return [snapshotKey: payload, snapshotAckKey: ackId]
        }()
        replyHandlers.forEach { $0(replyPayload) }
        let deliveredByReply = !replyHandlers.isEmpty && !payload.isEmpty
        let deliveredBySnapshot = shouldSendSnapshot && !payload.isEmpty && sendSnapshot(payload, ackId: ackId)
        if let ackId, !deliveredByReply && !deliveredBySnapshot {
            removePendingSnapshotAck(id: ackId)
        }

        let completions = currentSnapshotCompletions
        currentSnapshotCompletions.removeAll()
        completions.forEach { $0() }
        if !nextSnapshotCompletions.isEmpty {
            let waiting = nextSnapshotCompletions
            nextSnapshotCompletions.removeAll()
            requestSnapshot(replyHandler: nil) {
                waiting.forEach { $0() }
            }
        }
    }

    private func sendSnapshot(_ snapshotJson: String, ackId: String?) -> Bool {
        let session = WCSession.default
        guard session.activationState == .activated else {
            return false
        }
        var payload: [String: Any] = [snapshotKey: snapshotJson]
        if let ackId {
            payload[snapshotAckKey] = ackId
        }
        var didDeliver = false
        do {
            try session.updateApplicationContext(payload)
            didDeliver = true
        } catch {
            NSLog("Failed to update watch snapshot context: %@", error.localizedDescription)
        }

        var didQueueReachableMessage = false
        if session.isReachable {
            didQueueReachableMessage = true
            session.sendMessage(payload, replyHandler: nil, errorHandler: { [weak self] error in
                NSLog("Failed to send reachable watch snapshot message: %@", error.localizedDescription)
                if !didDeliver, let ackId {
                    DispatchQueue.main.async {
                        self?.removePendingSnapshotAck(id: ackId)
                    }
                }
            })
        }
        return didDeliver || didQueueReachableMessage
    }

    private func nextSnapshotAckId(for generation: Int) -> String {
        "\(generation)-\(Int(Date().timeIntervalSince1970 * 1000))"
    }

    private func registerPendingSnapshotAck(id: String, payload: String) {
        dispatchPrecondition(condition: .onQueue(.main))
        pendingSnapshotAckPayloads[id] = payload
        pendingSnapshotAckOrder.append(id)
        while pendingSnapshotAckOrder.count > maxPendingSnapshotAcks {
            let expiredId = pendingSnapshotAckOrder.removeFirst()
            pendingSnapshotAckPayloads.removeValue(forKey: expiredId)
        }
    }

    private func removePendingSnapshotAck(id: String) {
        dispatchPrecondition(condition: .onQueue(.main))
        pendingSnapshotAckPayloads.removeValue(forKey: id)
        pendingSnapshotAckOrder.removeAll { $0 == id }
    }

    private func completeSnapshotAck(_ ackId: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }
            guard let payload = self.pendingSnapshotAckPayloads[ackId] else {
                return
            }
            self.removePendingSnapshotAck(id: ackId)
            MainViewControllerKt.markIosWatchSnapshotDelivered(snapshotJson: payload)
        }
    }

    private func scheduleSnapshotRetry(after seconds: TimeInterval) {
        dispatchPrecondition(condition: .onQueue(.main))
        let retryId = UUID()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self else {
                return
            }
            self.snapshotRetryWorkItems[retryId] = nil
            self.sendSnapshotIfAvailable()
        }
        snapshotRetryWorkItems[retryId] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: workItem)
    }

    private func scheduleSnapshotRetries() {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }
            self.cancelSnapshotRetries()
            [1.5, 3, 8, 20, 45].forEach { delay in
                self.scheduleSnapshotRetry(after: delay)
            }
        }
    }

    private func scheduleRefreshSnapshotRetries() {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }
            self.cancelSnapshotRetries()
            [8, 20, 45, 90, 180].forEach { delay in
                self.scheduleSnapshotRetry(after: delay)
            }
        }
    }

    private func cancelSnapshotRetries() {
        dispatchPrecondition(condition: .onQueue(.main))
        snapshotRetryWorkItems.values.forEach { $0.cancel() }
        snapshotRetryWorkItems.removeAll()
    }
}

/// Background execution time held for one Watch-requested refresh. On expiry
/// the refresh is cancelled first, so its in-flight state is cleared and the
/// next Refresh command starts a new run instead of joining a suspended one.
private final class WatchRefreshBackgroundTask {
    private let lock = NSLock()
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    /// Returns nil when iOS grants no background time (the app is about to be suspended).
    static func begin() -> WatchRefreshBackgroundTask? {
        #if canImport(UIKit)
        let task = WatchRefreshBackgroundTask()
        // beginBackgroundTask may be called off the main thread; the WCSession
        // delegate queue must not wait for the main thread here.
        let identifier = UIApplication.shared.beginBackgroundTask(withName: "FutachaWatchRefresh") {
            MainViewControllerKt.cancelIosWatchRefresh()
            task.end()
        }
        guard identifier != .invalid else {
            return nil
        }
        task.lock.lock()
        task.identifier = identifier
        task.lock.unlock()
        return task
        #else
        return WatchRefreshBackgroundTask()
        #endif
    }

    /// Idempotent: completion and expiry may both end the same task.
    func end() {
        lock.lock()
        let ending = identifier
        identifier = .invalid
        lock.unlock()
        #if canImport(UIKit)
        guard ending != .invalid else {
            return
        }
        UIApplication.shared.endBackgroundTask(ending)
        #endif
    }
}
#endif
