import UIKit
import Flutter
import Firebase

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {

    private var documentScannerViewController: DocumentScannerViewController = {
        let documentScannerView = DocumentScannerViewController()
        documentScannerView.modalPresentationStyle = .overCurrentContext
        return documentScannerView
    }()

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        FirebaseApp.configure()

        GeneratedPluginRegistrant.register(with: self)

        if let controller: FlutterViewController = window?.rootViewController as? FlutterViewController {
            let documentScannerChannel = FlutterMethodChannel(
                name: "tests.mwsd.dev/documentScanner",
                binaryMessenger: controller.binaryMessenger
            )

            documentScannerChannel.setMethodCallHandler { (call, result) in
                if call.method == "openDocumentScanner" {
                    controller.present(self.documentScannerViewController, animated: true) {
                        self.documentScannerViewController.startDocumentScanner { (results) in
                            DispatchQueue.main.async {
                                controller.dismiss(animated: true)
                            }
                            result(results)
                        }
                    }
                } else {
                    result(FlutterMethodNotImplemented)
                    return
                }
            }
        }

        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
