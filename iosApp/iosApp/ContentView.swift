import UIKit
import SwiftUI

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
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
