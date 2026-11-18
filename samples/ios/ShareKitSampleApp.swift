import ShareKitSample
import SwiftUI
import UIKit

private struct ShareKitSampleView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.shareSampleViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

@main
struct ShareKitSampleApp: App {
    var body: some Scene {
        WindowGroup {
            ShareKitSampleView()
                .ignoresSafeArea()
        }
    }
}
