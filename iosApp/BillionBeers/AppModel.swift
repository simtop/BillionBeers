import BillionBeersData
import SwiftUI
import UIKit

final class AppModel: ObservableObject {
  let session: IosAppSession

  init() {
    let languageCode = Locale.current.language.languageCode?.identifier ?? "en"
    session = IosAppSession(languageCode: languageCode)
  }

  deinit {
    session.close()
  }
}

struct KotlinViewController: UIViewControllerRepresentable {
  let controller: UIViewController

  func makeUIViewController(context: Context) -> UIViewController {
    controller
  }

  func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct BillionBeersApp: App {
  @StateObject private var model = AppModel()

  var body: some Scene {
    WindowGroup {
      KotlinViewController(controller: model.session.viewController)
        .ignoresSafeArea()
        .onOpenURL { url in
          model.session.handleDeepLink(url: url.absoluteString)
        }
    }
  }
}
