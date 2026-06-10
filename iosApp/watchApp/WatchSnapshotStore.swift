import Foundation
import WatchConnectivity

final class WatchSnapshotStore: NSObject, ObservableObject {
    static let shared = WatchSnapshotStore()

    @Published private(set) var snapshot: WatchSnapshot?
    @Published private(set) var isReachable = false
    @Published private(set) var lastError: String?

    private let snapshotKey = "snapshot"
    private let snapshotAckKey = "snapshotAck"
    private let commandKey = "command"
    private let requestSnapshotKey = "requestSnapshot"
    private let snapshotDefaultsKey = "futacha.watch.snapshotJson"
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()
    private let workQueue = DispatchQueue(label: "com.valoser.futacha.watchSnapshotStore", qos: .utility)
    private let snapshotPayloadMaxBytes = 128 * 1024
    private let commandPayloadMaxBytes = 4 * 1024
    private var commandSequence = 0

    private override init() {
        super.init()
        loadPersistedSnapshotAsync()
        startSession()
    }

    func requestSnapshot() {
        guard WCSession.isSupported() else {
            setError("WatchConnectivity is not available.")
            return
        }
        let session = WCSession.default
        guard session.activationState == .activated else {
            setError("WatchConnectivity is not ready.")
            return
        }
        if !session.isReachable {
            transferSnapshotRequest(session)
            return
        }
        session.sendMessage([requestSnapshotKey: true], replyHandler: { [weak self] reply in
            if let json = reply[self?.snapshotKey ?? "snapshot"] as? String {
                let ackId = reply[self?.snapshotAckKey ?? "snapshotAck"] as? String
                self?.decodeAndStore(json, ackId: ackId)
            }
        }, errorHandler: { [weak self] error in
            self?.transferSnapshotRequest(session)
            self?.setError(error.localizedDescription)
        })
    }

    func requestRefresh() {
        sendCommand(.refresh())
    }

    func openBoardOnPhone(_ board: WatchBoard) {
        sendCommand(.openBoard(board))
    }

    func openThreadOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.openThread(thread))
    }

    func startReadAloudOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.startReadAloud(thread))
    }

    func pauseReadAloudOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.pauseReadAloud(thread))
    }

    func stopReadAloudOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.stopReadAloud(thread))
    }

    func nextReadAloudOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.nextReadAloud(thread))
    }

    func previousReadAloudOnPhone(_ thread: WatchThreadSummary) {
        sendCommand(.previousReadAloud(thread))
    }

    private func startSession() {
        guard WCSession.isSupported() else {
            setError("WatchConnectivity is not available.")
            return
        }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    private func sendCommand(_ command: WatchCommand) {
        guard WCSession.isSupported() else {
            setError("WatchConnectivity is not available.")
            return
        }
        let session = WCSession.default
        guard session.activationState == .activated else {
            setError("WatchConnectivity is not ready.")
            return
        }
        do {
            var commandWithId = command
            commandWithId.commandId = nextCommandId()
            let data = try encoder.encode(commandWithId)
            guard let json = String(data: data, encoding: .utf8) else {
                return
            }
            guard json.utf8.count <= commandPayloadMaxBytes else {
                setError("Command payload is too large.")
                return
            }
            if !session.isReachable {
                if command.type == "Refresh" {
                    transferCommandJson(json, session: session)
                } else {
                    setError("iPhoneアプリを開いてから再実行してください。")
                }
                return
            }
            session.sendMessage([commandKey: json], replyHandler: { [weak self] reply in
                if let accepted = reply["accepted"] as? Bool, !accepted {
                    if let message = reply["message"] as? String, !message.isEmpty {
                        self?.setError(message)
                    } else {
                        self?.setError("Command was rejected by iPhone.")
                    }
                }
            }, errorHandler: { [weak self] error in
                if command.type == "Refresh" {
                    self?.transferCommandJson(json, session: session)
                    self?.setError(error.localizedDescription)
                } else {
                    self?.setError("iPhoneアプリを開いてから再実行してください。")
                }
            })
        } catch {
            setError(error.localizedDescription)
        }
    }

    private func transferSnapshotRequest(_ session: WCSession) {
        session.transferUserInfo([requestSnapshotKey: true])
    }

    private func transferCommandJson(_ json: String, session: WCSession) {
        session.transferUserInfo([commandKey: json])
    }

    private func decodeAndStore(_ json: String, ackId: String? = nil) {
        guard !json.isEmpty, json.utf8.count <= snapshotPayloadMaxBytes else {
            return
        }
        workQueue.async { [weak self] in
            guard let self, let data = json.data(using: .utf8), !data.isEmpty else {
                return
            }
            do {
                let snapshot = try self.decoder.decode(WatchSnapshot.self, from: data)
                UserDefaults.standard.set(json, forKey: self.snapshotDefaultsKey)
                if let ackId {
                    self.ackSnapshot(id: ackId)
                }
                DispatchQueue.main.async {
                    self.snapshot = snapshot
                    self.lastError = nil
                }
            } catch {
                self.setError(error.localizedDescription)
            }
        }
    }

    private func ackSnapshot(id: String) {
        guard WCSession.isSupported() else {
            return
        }
        let session = WCSession.default
        guard session.activationState == .activated else {
            return
        }
        let payload = [snapshotAckKey: id]
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil, errorHandler: { [weak self] _ in
                self?.transferSnapshotAck(id, session: session)
            })
        } else {
            transferSnapshotAck(id, session: session)
        }
    }

    private func transferSnapshotAck(_ id: String, session: WCSession) {
        session.transferUserInfo([snapshotAckKey: id])
    }

    private func loadPersistedSnapshotAsync() {
        workQueue.async { [weak self] in
            guard let self else { return }
            guard
                let json = UserDefaults.standard.string(forKey: self.snapshotDefaultsKey),
                !json.isEmpty,
                json.utf8.count <= self.snapshotPayloadMaxBytes,
                let data = json.data(using: .utf8)
            else {
                return
            }
            guard let snapshot = try? self.decoder.decode(WatchSnapshot.self, from: data) else {
                return
            }
            DispatchQueue.main.async {
                self.snapshot = snapshot
            }
        }
    }

    private func setReachable(_ value: Bool) {
        DispatchQueue.main.async {
            self.isReachable = value
        }
    }

    private func setError(_ message: String) {
        DispatchQueue.main.async {
            self.lastError = message
        }
    }

    private func nextCommandId() -> String {
        commandSequence = commandSequence == Int.max ? 1 : commandSequence + 1
        return "watchos-\(Int(Date().timeIntervalSince1970 * 1000))-\(commandSequence)"
    }
}

extension WatchSnapshotStore: WCSessionDelegate {
    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        setReachable(session.isReachable)
        if let error {
            setError(error.localizedDescription)
            return
        }
        if activationState == .activated {
            requestSnapshot()
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        setReachable(session.isReachable)
    }

    func session(
        _ session: WCSession,
        didReceiveApplicationContext applicationContext: [String: Any]
    ) {
        if let json = applicationContext[snapshotKey] as? String {
            let ackId = applicationContext[snapshotAckKey] as? String
            decodeAndStore(json, ackId: ackId)
        }
    }

    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any]
    ) {
        if let json = message[snapshotKey] as? String {
            let ackId = message[snapshotAckKey] as? String
            decodeAndStore(json, ackId: ackId)
        }
    }
}
