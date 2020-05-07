//
//  DocumentScannerViewController.swift
//  Runner
//
//  Created by Marc Wiggerman on 20/02/2020.
//  Copyright © 2020 The Chromium Authors. All rights reserved.
//

import UIKit
import AVKit
import WeScan

enum ScannerError: String {
    case noCameraPermission
    case scanCancelled
    case scanFailed
}

/// The main document scanner view controller
/// - Warning: Completion handlers are used to communicate back the scanner results.
class DocumentScannerViewController: UIViewController {
    // Completion
    fileprivate var completionHandler: ((String) -> Void)?

    @available(iOS 13.0, *)
    fileprivate var visionImageToText: VisionImageToText {
        let vitt =  VisionImageToText { (resultsString) in
            print("Received the results from Vision")
            self.completionHandler?(resultsString)
        }
        return vitt
    }

    fileprivate var firebaseImageToText: FirebaseImageToText {
        let fitt = FirebaseImageToText { (resultsString) in
            print("Received the results from firebase")
            self.completionHandler?(resultsString)
        }
        return fitt
    }

    internal let receiptPreviewImageView: UIImageView = {
        let imageView = UIImageView()
        imageView.backgroundColor = .clear
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFill
        return imageView
    }()

    internal let blackImageOverlay: UIView = {
        let view = UIView()
        view.backgroundColor = .black
        view.alpha = 0.6
        view.isHidden = true
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    internal let loadingLabel: UILabel = {
        let label = UILabel()
        label.font = UIFont.systemFont(ofSize: 20, weight: .black)
        label.textColor = .cbsPurple
        label.text = "Analysing the receipt..."
        label.textAlignment = .center
        label.isHidden = true
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()
    

    override func viewDidLoad() {
        super.viewDidLoad()
        self.setupViews()
        self.setupConstraints()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        self.resetLoadingView()
    }

    // MARK: - Interfacing
    /// Starts the document scanner
    /// - Parameter completion: The completion handler will return a ScannerError if something went wrong. Otherwise
    ///                         it will return the Json String
    public func startDocumentScanner(completion: @escaping (String) -> Void) {
        self.completionHandler = completion
        AVCaptureDevice.requestAccess(for: .video) { (response) in
            if !response {
                completion(ScannerError.noCameraPermission.rawValue)
            } else {
                DispatchQueue.main.async {
                    let scannerViewController = ImageScannerController()
                    scannerViewController.modalPresentationStyle = .fullScreen
                    scannerViewController.imageScannerDelegate = self
                    self.present(scannerViewController, animated: true)
                }
            }
        }
    }

    // MARK: - UI
    fileprivate func setupViews() {
        self.view.addSubview(receiptPreviewImageView)
        self.view.addSubview(blackImageOverlay)
        self.view.addSubview(loadingLabel)
    }

    fileprivate func setupConstraints() {
        let receiptPreviewImageViewConstraints = [
            receiptPreviewImageView.topAnchor.constraint(equalTo: view.topAnchor),
            receiptPreviewImageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            receiptPreviewImageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            receiptPreviewImageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ]

        let blackImageOverlayConstraints = [
            blackImageOverlay.topAnchor.constraint(equalTo: receiptPreviewImageView.topAnchor),
            blackImageOverlay.bottomAnchor.constraint(equalTo: receiptPreviewImageView.bottomAnchor),
            blackImageOverlay.leadingAnchor.constraint(equalTo: receiptPreviewImageView.leadingAnchor),
            blackImageOverlay.trailingAnchor.constraint(equalTo: receiptPreviewImageView.trailingAnchor),
        ]

        let loadingLabelConstraints = [
            loadingLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            loadingLabel.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.8),
        ]

        NSLayoutConstraint.activate(receiptPreviewImageViewConstraints)
        NSLayoutConstraint.activate(blackImageOverlayConstraints)
        NSLayoutConstraint.activate(loadingLabelConstraints)
    }

    fileprivate func enableLoadingView(imageScan: UIImage) {
        DispatchQueue.main.async {
            self.receiptPreviewImageView.image = imageScan
            self.blackImageOverlay.isHidden = false
            self.loadingLabel.isHidden = false
        }
    }

    fileprivate func resetLoadingView() {
        DispatchQueue.main.async {
            self.receiptPreviewImageView.image = nil
            self.blackImageOverlay.isHidden = true
            self.loadingLabel.isHidden = true
        }
    }
}

extension DocumentScannerViewController: ImageScannerControllerDelegate {
    func imageScannerController(_ scanner: ImageScannerController, didFinishScanningWithResults results: ImageScannerResults) {
        let originalImage: UIImage = results.originalScan.image
        let croppedImage: UIImage
        if let enhancedImage = results.enhancedScan {
            croppedImage = enhancedImage.image
        } else {
            croppedImage = results.croppedScan.image
        }
        self.enableLoadingView(imageScan: originalImage)

        scanner.dismiss(animated: true) {
            DispatchQueue.global(qos: .userInitiated).async {
                // Use vision for iOS 13 and up, otherwise use firebase
                if #available(iOS 13.0, *) {
                    self.visionImageToText.processImage(croppedImage: croppedImage, originalImage: originalImage)
                } else {
                    self.firebaseImageToText.processImage(croppedImage: croppedImage, originalImage: originalImage)
                }
            }
        }
    }

    func imageScannerControllerDidCancel(_ scanner: ImageScannerController) {
        scanner.dismiss(animated: true)
        completionHandler?(ScannerError.scanCancelled.rawValue)
    }

    func imageScannerController(_ scanner: ImageScannerController, didFailWithError error: Error) {
        scanner.dismiss(animated: true)
        completionHandler?(ScannerError.scanFailed.rawValue)
    }
}



//@available(iOS 13.0, *)
//extension DocumentScannerViewController: VNDocumentCameraViewControllerDelegate {
//    func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
//        controller.dismiss(animated: true)
//        completionHandler?(ScannerError.scanCancelled.rawValue)
//    }
//
//    func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
//        controller.dismiss(animated: true)
//        completionHandler?(ScannerError.scanFailed.rawValue)
//    }
//
//    func documentCameraViewController(
//        _ controller: VNDocumentCameraViewController,
//        didFinishWith scan: VNDocumentCameraScan) {
//
//        let pageCount = scan.pageCount
//        guard pageCount >= 1 else { self.completionHandler?(ScannerError.scanFailed.rawValue); return }
//        let image = scan.imageOfPage(at: 0)
//
//        self.enableLoadingView(imageScan: image)
//
//        controller.dismiss(animated: true) {
//            DispatchQueue.global(qos: .userInitiated).async {
//                self.visionImageToText.processImage(image)
//            }
//        }
//    }
//}
