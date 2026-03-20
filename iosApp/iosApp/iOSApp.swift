import SwiftUI
import UIKit
import shared
import GoogleMobileAds
import FirebaseCore

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        registerIosBackgroundRefreshTaskIfAvailable()
        if let _ = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") {
            FirebaseApp.configure()
        }
        MobileAds.shared.start(completionHandler: nil)
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

private func registerIosBackgroundRefreshTaskIfAvailable() {
    let candidateClassNames = [
        "SharedMainViewControllerKt",
        "shared.SharedMainViewControllerKt",
        "MainViewControllerKt",
        "shared.MainViewControllerKt"
    ]
    let selector = NSSelectorFromString("registerIosBackgroundRefreshTask")

    for className in candidateClassNames {
        guard let type = NSClassFromString(className) as? NSObject.Type else {
            continue
        }
        guard type.responds(to: selector) else {
            continue
        }
        _ = type.perform(selector)
        return
    }

    NSLog("registerIosBackgroundRefreshTask entry point was not found in shared framework")
}
