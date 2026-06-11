import UIKit
import SwiftUI
import Foundation
import shared

private struct AdDisplayFlags: Equatable {
    let adsEnabled: Bool
    let threadScreenAdVisible: Bool

    var shouldLoadBanner: Bool {
        adsEnabled && threadScreenAdVisible
    }

    static func read() -> AdDisplayFlags {
        AdDisplayFlags(
            adsEnabled: UserDefaults.standard.object(forKey: "ads_enabled") == nil ?
                true :
                UserDefaults.standard.bool(forKey: "ads_enabled"),
            threadScreenAdVisible: UserDefaults.standard.bool(forKey: "thread_screen_ad_visible")
        )
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeViewControllerStore.shared
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@MainActor
private enum ComposeViewControllerStore {
    static let shared: UIViewController = MainViewControllerKt.MainViewController()
}

struct ContentView: View {
    @State private var adFlags = AdDisplayFlags.read()
    @State private var isBannerLoaded = false

    var body: some View {
        VStack(spacing: 0) {
            ComposeView()
                .ignoresSafeArea(.container, edges: .top)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if adFlags.shouldLoadBanner {
                AdMobBannerView { isLoaded in
                    DispatchQueue.main.async {
                        if isBannerLoaded != isLoaded {
                            isBannerLoaded = isLoaded
                        }
                    }
                }
                .frame(height: isBannerLoaded ? 50 : 0)
                .clipped()
                .background(Color(uiColor: .systemBackground))
            }
        }
        .onAppear {
            applyAdFlagsIfNeeded(AdDisplayFlags.read(), source: "onAppear")
        }
        .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
            applyAdFlagsIfNeeded(AdDisplayFlags.read(), source: "defaults changed")
        }
    }

    private func applyAdFlagsIfNeeded(_ nextFlags: AdDisplayFlags, source: String) {
        guard adFlags != nextFlags else {
            return
        }
        adFlags = nextFlags
        if !nextFlags.shouldLoadBanner {
            isBannerLoaded = false
        }
        NSLog(
            "ContentView %@ ads_enabled=%@ thread_screen_ad_visible=%@ shouldLoadBanner=%@",
            source,
            nextFlags.adsEnabled.description,
            nextFlags.threadScreenAdVisible.description,
            nextFlags.shouldLoadBanner.description
        )
    }
}
