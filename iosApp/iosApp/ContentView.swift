import UIKit
import SwiftUI
import Foundation
import WebKit
import shared

struct SavedHtmlDocument: Identifiable {
    let id = UUID()
    let url: URL
}

final class SavedHtmlDocumentStore: ObservableObject {
    static let shared = SavedHtmlDocumentStore()
    @Published var document: SavedHtmlDocument?
    private var securityScopedURL: URL?

    @discardableResult
    func openIfSupported(_ url: URL) -> Bool {
        guard url.isFileURL, ["htm", "html"].contains(url.pathExtension.lowercased()) else {
            return false
        }
        close()
        if url.startAccessingSecurityScopedResource() {
            securityScopedURL = url
        }
        document = SavedHtmlDocument(url: url)
        return true
    }

    func close() {
        securityScopedURL?.stopAccessingSecurityScopedResource()
        securityScopedURL = nil
        document = nil
    }
}

#if DEBUG
private enum SavedHtmlUITestFixture {
    private static var didOpen = false

    static func openIfRequested() {
        guard !didOpen,
              ProcessInfo.processInfo.arguments.contains("-futacha.issue78.saved_html_fixture") else {
            return
        }
        didOpen = true
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("issue78-saved-thread.htm")
        let html = """
        <html><head><meta charset="UTF-8"></head><body>
        <a target=_blank href="other/fu7199371.png">fu7199371.png</a><span
          id="preview" onclick="previewImg('preview','other/fu7199371.png')">[見る]</span><br>保存本文
        </body></html>
        """
        guard (try? html.write(to: url, atomically: true, encoding: .utf8)) != nil else { return }
        _ = SavedHtmlDocumentStore.shared.openIfSupported(url)
    }
}
#endif

private struct SavedHtmlWebView: UIViewRepresentable {
    let url: URL

    private func sanitizedHTML() -> String? {
        guard var html = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        let rules: [(String, String)] = [
            (
                #"(?is)(<a\b[^>]{0,1000}>\s*(?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv)\s*</a\s*>)\s*<span\b[^>]{0,1000}>\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)\s*</span\s*>"#,
                "$1"
            ),
            (
                #"(?i)((?:fu|f)\d+\.(?:gif|jpe?g|jpe|png|webp|bmp|apng|avif|webm|mp4|m4v|mov|mkv|avi|ts|flv))(\s*</a\s*>)?\s*(?:\[|［|&#0*91;|&#x0*5b;|&lbrack;)\s*見る\s*(?:\]|］|&#0*93;|&#x0*5d;|&rbrack;)(?=\s*(?:</a\s*>|<br\b[^>]*>|</?(?:font|span|blockquote|div|p|td)\b[^>]*>|$))"#,
                "$1$2"
            ),
            (#"(?is)<script\b[^>]*>.*?</script\s*>"#, ""),
            (#"(?is)<(?:iframe|object|embed)\b[^>]*>.*?</(?:iframe|object|embed)\s*>"#, ""),
            (#"(?i)\b(src|href)\s*=\s*(['\"])\s*(?:https?:)?//.*?\2"#, "$1=$2#$2"),
            (#"(?i)url\(\s*(['\"]?)(?:https?:)?//.*?\1\s*\)"#, "url()")
        ]
        for (pattern, replacement) in rules {
            guard let expression = try? NSRegularExpression(pattern: pattern) else { continue }
            let range = NSRange(html.startIndex..<html.endIndex, in: html)
            html = expression.stringByReplacingMatches(
                in: html,
                range: range,
                withTemplate: replacement
            )
        }
        return html
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = false
        configuration.defaultWebpagePreferences = preferences
        let view = WKWebView(frame: .zero, configuration: configuration)
        if let html = sanitizedHTML() {
            view.loadHTMLString(html, baseURL: url.deletingLastPathComponent())
        }
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private struct SavedHtmlDocumentView: View {
    let document: SavedHtmlDocument
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            SavedHtmlWebView(url: document.url)
                .navigationTitle("保存済みスレ")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("閉じる") { dismiss() }
                    }
                }
        }
    }
}

private let ugcEulaCurrentVersion = "2026-08-22"
private let ugcEulaDefaultsKey = "review.ugc_eula_accepted_version"

struct ComposeView: UIViewControllerRepresentable {
    final class Coordinator {
        private static let issue78ArchiveFixture: Bool = {
#if DEBUG
            ProcessInfo.processInfo.arguments.contains("-futacha.issue78.archive_fixture")
#else
            false
#endif
        }()

        lazy var controller: UIViewController = FutachaComposeHostViewController(
            content: MainViewControllerKt.MainViewController(
                issue78ArchiveFixture: Self.issue78ArchiveFixture
            )
        )
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        context.coordinator.controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/**
 * Compose itself owns the app chrome, but iOS requires a UIViewController
 * override to change status-bar glyph contrast or hide the home indicator.
 * Kotlin publishes just those presentation values; this host keeps the
 * visible result aligned with the Android compatibility viewer.
 */
private final class FutachaComposeHostViewController: UIViewController {
    private let content: UIViewController
    private var statusUsesDarkIcons = false
    private var viewerChromeHidden = false
    private var systemBarColor = UIColor.black
    private var observers: [NSObjectProtocol] = []

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
        let center = NotificationCenter.default
        observers.append(center.addObserver(
            forName: Notification.Name("com.valoser.futacha.system-bars"),
            object: nil,
            queue: .main
        ) { [weak self] notification in
            self?.applySystemBars(notification.userInfo)
        })
        observers.append(center.addObserver(
            forName: Notification.Name("com.valoser.futacha.viewer-bars"),
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let hidden = (notification.userInfo?["hidden"] as? NSNumber)?.boolValue else { return }
            self?.viewerChromeHidden = hidden
            self?.setNeedsStatusBarAppearanceUpdate()
            self?.setNeedsUpdateOfHomeIndicatorAutoHidden()
        })
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        let center = NotificationCenter.default
        observers.forEach(center.removeObserver)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = systemBarColor
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    override var childForStatusBarStyle: UIViewController? { nil }
    override var childForStatusBarHidden: UIViewController? { nil }
    override var preferredStatusBarStyle: UIStatusBarStyle {
        statusUsesDarkIcons ? .darkContent : .lightContent
    }
    override var prefersStatusBarHidden: Bool { viewerChromeHidden }
    override var prefersHomeIndicatorAutoHidden: Bool { viewerChromeHidden }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge {
        viewerChromeHidden ? .bottom : []
    }

    private func applySystemBars(_ userInfo: [AnyHashable: Any]?) {
        func component(_ key: String) -> CGFloat {
            CGFloat((userInfo?[key] as? NSNumber)?.doubleValue ?? 0.0)
        }
        statusUsesDarkIcons = (userInfo?["darkIcons"] as? NSNumber)?.boolValue ?? false
        systemBarColor = UIColor(
            red: component("red"),
            green: component("green"),
            blue: component("blue"),
            alpha: 1.0
        )
        view.backgroundColor = systemBarColor
        setNeedsStatusBarAppearanceUpdate()
    }
}

struct ContentView: View {
    @ObservedObject private var savedHtmlStore = SavedHtmlDocumentStore.shared
    @AppStorage(ugcEulaDefaultsKey) private var acceptedEulaVersion = ""
    @State private var isUiTestEulaOverrideActive = ProcessInfo.processInfo.arguments.contains(
        "-review.force_ugc_eula"
    )

    var body: some View {
        Group {
            if acceptedEulaVersion == ugcEulaCurrentVersion && !isUiTestEulaOverrideActive {
                composeContent
            } else {
                UserGeneratedContentEulaView {
                    acceptedEulaVersion = ugcEulaCurrentVersion
                    isUiTestEulaOverrideActive = false
                }
            }
        }
        .sheet(item: $savedHtmlStore.document, onDismiss: savedHtmlStore.close) { document in
            SavedHtmlDocumentView(document: document)
        }
        .onAppear {
#if DEBUG
            SavedHtmlUITestFixture.openIfRequested()
#endif
        }
    }

    @ViewBuilder
    private var composeContent: some View {
        if UIDevice.current.userInterfaceIdiom == .pad {
            // iPadOS places window and tab controls in the top safe area. The
            // old host extended Compose underneath them, which could clip the
            // app's top menus on iPad. Keep iPhone's established status-bar
            // treatment while respecting the complete iPad safe area.
            ComposeView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ComposeView()
                .ignoresSafeArea(.container, edges: .top)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

private struct UserGeneratedContentEulaView: View {
    @State private var hasConfirmedAgreement = false
    let onAccept: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 44))
                            .foregroundStyle(.teal)
                            .accessibilityHidden(true)
                        Text("利用規約（EULA）")
                            .font(.largeTitle.bold())
                        Text("ユーザー投稿コンテンツに関する同意")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                    }

                    Text("本アプリは、外部サービス「ふたば☆ちゃんねる」のユーザー投稿コンテンツを表示し、投稿・通報を補助する専用ブラウザです。登録やログインはありませんが、コンテンツへアクセスする前に以下へ同意してください。")

                    TermsSection(
                        title: "不適切なコンテンツ・迷惑行為を一切容認しません",
                        body: "違法な内容、わいせつ・暴力的な内容、差別、嫌がらせ、脅迫、個人情報の暴露、その他他者を害する投稿や行為は禁止します。利用者はこれらを投稿せず、他の利用者を攻撃・虐待しないことに同意します。"
                    )
                    TermsSection(
                        title: "通報とブロック",
                        body: "投稿内の「通報・ブロック」（または投稿の長押し）から操作できます。不適切な投稿は「不適切な投稿を通報」で掲示板管理者へ報告できます。迷惑な利用者は「この利用者をブロック」からID・IP・名前などを端末内のNGへ登録し、その投稿を非表示にできます。"
                    )
                    TermsSection(
                        title: "違反時の対応",
                        body: "違反コンテンツは掲示板管理者により削除され、悪質な利用者はサービス側で利用を制限されることがあります。本アプリでもNGフィルターを利用して表示を制限できます。"
                    )
                    TermsSection(
                        title: "お問い合わせ",
                        body: "安全上の問題や本アプリについては admin@valoser.com へ連絡できます。投稿そのものの削除依頼は、各投稿の通報機能を利用してください。"
                    )

                    Button {
                        hasConfirmedAgreement.toggle()
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: hasConfirmedAgreement ? "checkmark.square.fill" : "square")
                                .font(.title2)
                                .foregroundStyle(hasConfirmedAgreement ? .teal : .secondary)
                            Text("上記の利用規約に同意し、不適切なコンテンツや迷惑行為を投稿しません")
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("ugc-eula-agreement")

                    Button("同意して利用を開始", action: onAccept)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        .frame(maxWidth: .infinity)
                        .disabled(!hasConfirmedAgreement)
                        .accessibilityIdentifier("ugc-eula-accept")

                    Text("同意しない場合、本アプリでユーザー投稿コンテンツを閲覧・投稿することはできません。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: 680, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.vertical, 28)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemBackground))
            .navigationTitle("安全に利用するために")
            .navigationBarTitleDisplayMode(.inline)
        }
        .accessibilityIdentifier("ugc-eula-screen")
    }
}

private struct TermsSection: View {
    let title: String
    let bodyText: String

    init(title: String, body: String) {
        self.title = title
        self.bodyText = body
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
            Text(bodyText)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
