import UIKit
import SwiftUI
import Foundation

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        resolveComposeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private func resolveComposeViewController() -> UIViewController {
    let candidateClassNames = [
        "SharedMainViewControllerKt",
        "shared.SharedMainViewControllerKt",
        "MainViewControllerKt",
        "shared.MainViewControllerKt"
    ]
    let selector = NSSelectorFromString("MainViewController")

    for className in candidateClassNames {
        guard let type = NSClassFromString(className) as? NSObject.Type else {
            continue
        }
        guard type.responds(to: selector), let unmanaged = type.perform(selector) else {
            continue
        }
        if let viewController = unmanaged.takeUnretainedValue() as? UIViewController {
            return viewController
        }
    }

    fatalError("Unable to resolve Compose entry point from shared framework")
}

struct ContentView: View {
    @State private var adsEnabled = UserDefaults.standard.object(forKey: "ads_enabled") == nil ? true : UserDefaults.standard.bool(forKey: "ads_enabled")
    @State private var threadScreenAdVisible = UserDefaults.standard.bool(forKey: "thread_screen_ad_visible")
    @State private var isBannerLoaded = false
    @State private var shouldLoadBanner = false

    var body: some View {
        VStack(spacing: 0) {
            ComposeView()
                .ignoresSafeArea(.container, edges: .top)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if shouldLoadBanner {
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
            reloadAdFlags()
            shouldLoadBanner = adsEnabled && threadScreenAdVisible
            NSLog(
                "ContentView onAppear ads_enabled=%@ thread_screen_ad_visible=%@ shouldLoadBanner=%@",
                adsEnabled.description,
                threadScreenAdVisible.description,
                shouldLoadBanner.description
            )
        }
        .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
            DispatchQueue.main.async {
                reloadAdFlags()
                let nextShouldLoadBanner = adsEnabled && threadScreenAdVisible
                if shouldLoadBanner != nextShouldLoadBanner {
                    shouldLoadBanner = nextShouldLoadBanner
                }
                NSLog(
                    "ContentView defaults changed ads_enabled=%@ thread_screen_ad_visible=%@ shouldLoadBanner=%@",
                    adsEnabled.description,
                    threadScreenAdVisible.description,
                    shouldLoadBanner.description
                )
                if !shouldLoadBanner {
                    isBannerLoaded = false
                }
            }
        }
    }

    private func reloadAdFlags() {
        adsEnabled = UserDefaults.standard.object(forKey: "ads_enabled") == nil ? true : UserDefaults.standard.bool(forKey: "ads_enabled")
        threadScreenAdVisible = UserDefaults.standard.bool(forKey: "thread_screen_ad_visible")
    }
}
