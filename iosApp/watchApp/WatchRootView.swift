import SwiftUI

struct WatchRootView: View {
    @StateObject private var store = WatchSnapshotStore.shared

    var body: some View {
        NavigationStack {
            Group {
                if let snapshot = store.snapshot {
                    SnapshotListView(snapshot: snapshot, store: store)
                } else {
                    EmptySnapshotView(store: store)
                }
            }
            .navigationTitle("Futacha")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        store.requestSnapshot()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
    }
}

private struct EmptySnapshotView: View {
    @ObservedObject var store: WatchSnapshotStore

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "applewatch")
                .font(.title2)
            Text("同期データなし")
                .font(.headline)
            if let error = store.lastError {
                Text(error)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Button("同期") {
                store.requestSnapshot()
            }
        }
        .padding()
    }
}

private struct SnapshotListView: View {
    let snapshot: WatchSnapshot
    @ObservedObject var store: WatchSnapshotStore

    var body: some View {
        List {
            Section {
                HStack {
                    Label("\(snapshot.unreadTotal)", systemImage: "sparkles")
                    Spacer()
                    Label("\(snapshot.watchMatchTotal)", systemImage: "eye")
                }
                .font(.caption)
                Text("\(snapshot.isStale ? "同期古い" : "同期") \(formatSnapshotGeneratedAt(snapshot.generatedAtMillis)) / \(snapshot.boards.count)板 / \(snapshot.threads.count)スレ")
                    .font(.caption2)
                    .foregroundStyle(snapshot.isStale ? .orange : .secondary)
                Text(store.isReachable ? "iPhone接続中" : "キャッシュ表示")
                    .font(.caption2)
                    .foregroundStyle(store.isReachable ? .green : .secondary)
                Button("iPhoneで更新") {
                    store.requestRefresh()
                }
            }

            if !snapshot.boards.isEmpty {
                Section("板") {
                    ForEach(snapshot.boards) { board in
                        NavigationLink {
                            BoardThreadListView(board: board, snapshot: snapshot, store: store)
                        } label: {
                            BoardRow(board: board, snapshot: snapshot)
                        }
                    }
                }
            }

            Section("スレ") {
                ForEach(snapshot.threads) { thread in
                    NavigationLink {
                        ThreadPreviewView(thread: thread, store: store)
                    } label: {
                        ThreadRow(thread: thread)
                    }
                }
            }
        }
    }
}

private func formatSnapshotGeneratedAt(_ epochMillis: Int64) -> String {
    guard epochMillis > 0 else {
        return "--:--"
    }
    let date = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
    let formatter = DateFormatter()
    formatter.dateFormat = "HH:mm"
    return formatter.string(from: date)
}

private struct BoardRow: View {
    let board: WatchBoard
    let snapshot: WatchSnapshot

    private var threadCount: Int {
        snapshot.threads.filter { $0.boardId == board.id }.count
    }

    private var unreadCount: Int {
        snapshot.threads.filter { $0.boardId == board.id && $0.newReplyCount > 0 }.count
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(board.name)
                    .lineLimit(1)
                Text("\(threadCount)スレ")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if unreadCount > 0 {
                Text("+\(unreadCount)")
                    .font(.caption2)
                    .foregroundStyle(.green)
            }
        }
    }
}

private struct BoardThreadListView: View {
    let board: WatchBoard
    let snapshot: WatchSnapshot
    @ObservedObject var store: WatchSnapshotStore

    private var threads: [WatchThreadSummary] {
        snapshot.threads.filter { $0.boardId == board.id }
    }

    var body: some View {
        List {
            Section {
                Text(board.name)
                    .font(.headline)
                Text("\(threads.count)スレ")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Button("iPhoneで板を開く") {
                    store.openBoardOnPhone(board)
                }
            }

            if threads.isEmpty {
                Text("履歴なし")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Section("スレ") {
                    ForEach(threads) { thread in
                        NavigationLink {
                            ThreadPreviewView(thread: thread, store: store)
                        } label: {
                            ThreadRow(thread: thread)
                        }
                    }
                }
            }
        }
        .navigationTitle(board.name)
    }
}

private struct ThreadRow: View {
    let thread: WatchThreadSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(thread.boardName)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
                if thread.newReplyCount > 0 {
                    Text("+\(thread.newReplyCount)")
                        .font(.caption2)
                        .foregroundStyle(.green)
                }
            }
            Text(thread.title)
                .lineLimit(2)
            if thread.isWatchWordMatch {
                Label("監視ワード", systemImage: "eye")
                    .font(.caption2)
                    .foregroundStyle(.yellow)
            }
            if let readAloudStatus = thread.freshReadAloudStatus {
                Label(readAloudStatus.displayText, systemImage: "speaker.wave.2")
                    .font(.caption2)
                    .foregroundStyle(.green)
            }
        }
    }
}

private struct ThreadPreviewView: View {
    let thread: WatchThreadSummary
    @ObservedObject var store: WatchSnapshotStore

    var body: some View {
        List {
            Section {
                Text(thread.title)
                    .font(.headline)
                Text("\(thread.boardName) / \(thread.replyCount)レス")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                if let readAloudStatus = thread.freshReadAloudStatus {
                    Label(readAloudStatus.displayText, systemImage: "speaker.wave.2")
                        .font(.caption2)
                        .foregroundStyle(.green)
                }
                Button("iPhoneで開く") {
                    store.openThreadOnPhone(thread)
                }
            }

            Section("読み上げ") {
                Button("開始 / 再開") {
                    store.startReadAloudOnPhone(thread)
                }
                Button("前へ") {
                    store.previousReadAloudOnPhone(thread)
                }
                Button("次へ") {
                    store.nextReadAloudOnPhone(thread)
                }
                Button("一時停止") {
                    store.pauseReadAloudOnPhone(thread)
                }
                Button("停止") {
                    store.stopReadAloudOnPhone(thread)
                }
            }

            Section("プレビュー") {
                if thread.previewPosts.isEmpty {
                    Text("保存済みプレビューなし")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(thread.previewPosts) { post in
                        VStack(alignment: .leading, spacing: 3) {
                            Text("No.\(post.postId)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(post.text)
                                .font(.caption)
                                .lineLimit(5)
                        }
                    }
                }
            }
        }
        .navigationTitle(thread.boardName)
    }
}
