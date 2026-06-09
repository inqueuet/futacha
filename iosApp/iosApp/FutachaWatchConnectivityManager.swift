import Foundation
import shared

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
    private var snapshotRetryWorkItems: [DispatchWorkItem] = []
    private var snapshotTimeoutWorkItem: DispatchWorkItem?
    private var snapshotRequestGeneration = 0
    private var isSnapshotRequestInFlight = false
    private var pendingSnapshotReplyHandlers: [([String: Any]) -> Void] = []
    private var shouldSendSnapshotAfterCurrentRequest = false
    private var pendingSnapshotAckPayloads: [String: String] = [:]
    private var pendingSnapshotAckOrder: [String] = []

    private override init() {
        super.init()
    }

    func start() {
        guard WCSession.isSupported() else {
            return
        }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    func sendSnapshotIfAvailable() {
        guard WCSession.isSupported() else {
            return
        }
        requestSnapshot(replyHandler: nil)
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
                replyHandler?(["accepted": false])
                return
            }
            let accepted = MainViewControllerKt.handleIosWatchCommandJson(commandJson: commandJson)
            replyHandler?(["accepted": accepted])
            if accepted {
                sendSnapshotIfAvailable()
                scheduleSnapshotRetries()
            }
            return
        }

        if (message[requestSnapshotKey] as? Bool) == true {
            requestSnapshot(replyHandler: replyHandler)
        }
    }

    private func requestSnapshot(replyHandler: (([String: Any]) -> Void)?) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                replyHandler?(["snapshot": ""])
                return
            }
            if let replyHandler {
                self.pendingSnapshotReplyHandlers.append(replyHandler)
            } else {
                self.shouldSendSnapshotAfterCurrentRequest = true
            }
            guard !self.isSnapshotRequestInFlight else {
                return
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
        let workItem = DispatchWorkItem { [weak self] in
            self?.sendSnapshotIfAvailable()
        }
        snapshotRetryWorkItems.append(workItem)
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

    private func cancelSnapshotRetries() {
        dispatchPrecondition(condition: .onQueue(.main))
        snapshotRetryWorkItems.forEach { $0.cancel() }
        snapshotRetryWorkItems.removeAll()
    }
}
#endif
