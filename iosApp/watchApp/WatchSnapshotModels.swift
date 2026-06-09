import Foundation

struct WatchSnapshot: Codable {
    let generatedAtMillis: Int64
    let boards: [WatchBoard]
    let threads: [WatchThreadSummary]
    let watchWords: [String]
    let unreadTotal: Int
    let watchMatchTotal: Int

    var isStale: Bool {
        guard generatedAtMillis > 0 else {
            return true
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let ageMillis = nowMillis - generatedAtMillis
        return ageMillis < 0 || ageMillis > watchSnapshotStaleAgeMillis
    }
}

struct WatchBoard: Codable, Identifiable {
    let id: String
    let name: String
    let category: String
    let url: String
    let pinned: Bool
}

struct WatchThreadSummary: Codable, Identifiable {
    let threadId: String
    let boardId: String
    let boardName: String
    let boardUrl: String
    let title: String
    let thumbnailUrl: String?
    let replyCount: Int
    let previousReplyCount: Int?
    let newReplyCount: Int
    let lastVisitedEpochMillis: Int64
    let isWatchWordMatch: Bool
    let previewPosts: [WatchPostPreview]
    let readAloudStatus: WatchReadAloudStatus?

    var id: String {
        "\(boardId)-\(threadId)"
    }

    var freshReadAloudStatus: WatchReadAloudStatus? {
        guard let readAloudStatus else {
            return nil
        }
        return readAloudStatus.isFresh ? readAloudStatus : nil
    }
}

struct WatchPostPreview: Codable, Identifiable {
    let postId: String
    let text: String
    let postedAtText: String?

    var id: String {
        postId
    }
}

struct WatchReadAloudStatus: Codable {
    let boardId: String
    let boardUrl: String
    let threadId: String
    let state: String
    let postId: String?
    let currentIndex: Int
    let totalPosts: Int
    let updatedAtMillis: Int64

    var isFresh: Bool {
        guard updatedAtMillis > 0 else {
            return false
        }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let ageMillis = nowMillis - updatedAtMillis
        return ageMillis >= 0 && ageMillis <= watchReadAloudStatusMaxAgeMillis
    }

    var displayText: String {
        let stateLabel: String
        switch state {
        case "Speaking":
            stateLabel = "読み上げ中"
        case "Paused":
            stateLabel = "一時停止中"
        default:
            stateLabel = "読み上げ"
        }
        if let postId, !postId.isEmpty {
            return "\(stateLabel) No.\(postId)"
        }
        return "\(stateLabel) \(currentIndex + 1)/\(totalPosts)"
    }
}

private let watchReadAloudStatusMaxAgeMillis: Int64 = 10 * 60 * 1000
private let watchSnapshotStaleAgeMillis: Int64 = 30 * 60 * 1000

struct WatchCommand: Encodable {
    let type: String
    let boardId: String?
    let boardUrl: String?
    let threadId: String?

    static func refresh() -> WatchCommand {
        WatchCommand(type: "Refresh", boardId: nil, boardUrl: nil, threadId: nil)
    }

    static func openBoard(_ board: WatchBoard) -> WatchCommand {
        WatchCommand(
            type: "SelectBoard",
            boardId: board.id,
            boardUrl: board.url,
            threadId: nil
        )
    }

    static func openThread(_ thread: WatchThreadSummary) -> WatchCommand {
        WatchCommand(
            type: "OpenThreadOnPhone",
            boardId: thread.boardId,
            boardUrl: thread.boardUrl,
            threadId: thread.threadId
        )
    }

    static func startReadAloud(_ thread: WatchThreadSummary) -> WatchCommand {
        threadCommand("StartReadAloudOnPhone", thread: thread)
    }

    static func pauseReadAloud(_ thread: WatchThreadSummary) -> WatchCommand {
        threadCommand("PauseReadAloudOnPhone", thread: thread)
    }

    static func stopReadAloud(_ thread: WatchThreadSummary) -> WatchCommand {
        threadCommand("StopReadAloudOnPhone", thread: thread)
    }

    static func nextReadAloud(_ thread: WatchThreadSummary) -> WatchCommand {
        threadCommand("NextReadAloudOnPhone", thread: thread)
    }

    static func previousReadAloud(_ thread: WatchThreadSummary) -> WatchCommand {
        threadCommand("PreviousReadAloudOnPhone", thread: thread)
    }

    private static func threadCommand(_ type: String, thread: WatchThreadSummary) -> WatchCommand {
        WatchCommand(
            type: type,
            boardId: thread.boardId,
            boardUrl: thread.boardUrl,
            threadId: thread.threadId
        )
    }
}
