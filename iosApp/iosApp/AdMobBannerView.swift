import SwiftUI
import GoogleMobileAds
import Foundation

struct AdMobBannerView: UIViewRepresentable {
    let adUnitID: String
    let onAdLoadStateChanged: (Bool) -> Void

    init(
        adUnitID: String = "ca-app-pub-6403856201304924/8764822238",
        onAdLoadStateChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.adUnitID = adUnitID
        self.onAdLoadStateChanged = onAdLoadStateChanged
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onAdLoadStateChanged: onAdLoadStateChanged)
    }

    func makeUIView(context: Context) -> BannerView {
        let bannerView = BannerView(adSize: AdSizeBanner)
        bannerView.adUnitID = adUnitID
        bannerView.delegate = context.coordinator

        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootViewController = windowScene.windows.first?.rootViewController {
            bannerView.rootViewController = rootViewController
            NSLog("AdMobBannerView rootViewController attached: %@", String(describing: type(of: rootViewController)))
        } else {
            NSLog("AdMobBannerView failed to resolve rootViewController before load")
        }

        NSLog("AdMobBannerView loading ad for unit id: %@", adUnitID)
        bannerView.load(Request())
        return bannerView
    }

    func updateUIView(_ uiView: BannerView, context: Context) {
        context.coordinator.onAdLoadStateChanged = onAdLoadStateChanged
        if uiView.rootViewController == nil,
           let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootViewController = windowScene.windows.first?.rootViewController {
            uiView.rootViewController = rootViewController
            NSLog("AdMobBannerView rootViewController reattached during update")
        }
    }

    final class Coordinator: NSObject, BannerViewDelegate {
        var onAdLoadStateChanged: (Bool) -> Void

        init(onAdLoadStateChanged: @escaping (Bool) -> Void) {
            self.onAdLoadStateChanged = onAdLoadStateChanged
        }

        func bannerViewDidReceiveAd(_ bannerView: BannerView) {
            NSLog("AdMobBannerView did receive ad")
            onAdLoadStateChanged(true)
        }

        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: any Error) {
            NSLog("AdMobBannerView failed to receive ad: %@", String(describing: error))
            onAdLoadStateChanged(false)
        }
    }
}
