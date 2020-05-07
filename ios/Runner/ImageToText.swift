//
//  ImageToText.swift
//  Runner
//
//  Created by Marc Wiggerman on 21/04/2020.
//  Copyright © 2020 The Chromium Authors. All rights reserved.
//

import Foundation
import Vision
import Firebase

protocol ImageToTextProtocol {
    var currentImageBeingAnalysedCropped: UIImage? { get set }
    var currentImageBeingAnalysedOriginal: UIImage? { get set }
    var completionHandler: ((String) -> Void) { get set }

    init(onComplete completionHandler: @escaping ((String) -> Void))
    func processImage(croppedImage: UIImage, originalImage: UIImage)
}

@available(iOS 13.0, *)
public class VisionImageToText: ImageToTextProtocol {
    internal var currentImageBeingAnalysedCropped: UIImage?
    internal var currentImageBeingAnalysedOriginal: UIImage?
    internal var completionHandler: ((String) -> Void)

    // MARK: - Vision
    fileprivate var textRecognitionRequest: VNRecognizeTextRequest!

    required init(onComplete completionHandler: @escaping ((String) -> Void)) {
        self.completionHandler = completionHandler
        self.configureVision()
    }

    /// Configures the Vision API
    public func configureVision() {
        self.textRecognitionRequest = VNRecognizeTextRequest(completionHandler: { (request, error) in
            if error == nil {
                self.textRecognizedHandler(request: request)
            } else {
                NSLog("Vision Error: \(error?.localizedDescription ?? "--")")
            }
        })

        self.textRecognitionRequest.recognitionLevel = .accurate
        self.textRecognitionRequest.usesLanguageCorrection = true
    }

    /// Handles the VNRequest results. The function unpacks the results and converts it to a Json String
    /// - Parameter request: the results of the Vision API
    private func textRecognizedHandler(request: VNRequest) {
        if let results = request.results as? [VNRecognizedTextObservation],
            !results.isEmpty,
            let originalImage = self.currentImageBeingAnalysedOriginal,
            let croppedImage = self.currentImageBeingAnalysedCropped {

            if let resultString = self.getJsonStringResults(results, croppedImage: croppedImage, originalImage: originalImage) {
                self.completionHandler(resultString)
            }
        }
    }

    /// Preprocesses the results by getting the top candidate (with the most confidence)
    /// after which they are turned into TextObservations
    /// - Parameters:
    ///   - results: Vision results
    ///   - sourceImage: The image in which the vision observations are made
    fileprivate func preprocessResults(_ results: [VNRecognizedTextObservation], sourceImage: UIImage) -> VisionResult {
        var textObservations = [VisionResult.TextObservation]()

        let boundingBoxes = results.compactMap { $0.boundingBox }
        let topCandidates = results.compactMap { $0.topCandidates(1).first }

        for (candidate, boundingBox) in zip(topCandidates, boundingBoxes) {
            textObservations.append(
                VisionResult.TextObservation(
                    text: candidate.string,
                    confidence: candidate.confidence,
                    normalizedRect: VisionResult.Rect(rect: boundingBox, invertCoordinates: true)
                )
            )
        }

        return VisionResult(
            sourceImageSize: VisionResult.Size(size: sourceImage.size),
            textObservations: textObservations
        )
    }

    /// Starts the text extraction process
    /// - Parameter image: The image which will be used to extract data
    /// Note: croppedImage is used for the OCR
    public func processImage(croppedImage: UIImage, originalImage: UIImage) {
        guard let cgImage = croppedImage.cgImage else {
            print("Failed to get cgimage from input image")
            return
        }

        guard let textRecognitionRequest = self.textRecognitionRequest else {
            print("Text Recognition Request not configured")
            return
        }

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        self.currentImageBeingAnalysedCropped = croppedImage
        self.currentImageBeingAnalysedOriginal = originalImage

        do {
            try handler.perform([textRecognitionRequest])
        } catch {
            print(error)
        }
    }

    /// Creates a JSON string from VNRecognizedTextObservation
    /// - Parameters:
    ///   - results: Vision results
    ///   - sourceImage: The image in which the vision observations are made
    fileprivate func getJsonStringResults(_ results: [VNRecognizedTextObservation], croppedImage: UIImage, originalImage: UIImage) -> String? {
        let visionResults = self.preprocessResults(results, sourceImage: croppedImage)
        let scanResult = ScanResult(visionResult: visionResults, croppedImage: croppedImage, originalImage: originalImage)

        do {
            let jsonData = try JSONEncoder().encode(scanResult)
            let jsonString = String(data: jsonData, encoding: .utf8)
            return jsonString
        } catch {
            NSLog("The results could not be converted to JSON")
            return nil
        }
    }
}

public class FirebaseImageToText: ImageToTextProtocol {
    internal var currentImageBeingAnalysedCropped: UIImage?
    internal var currentImageBeingAnalysedOriginal: UIImage?
    internal var completionHandler: ((String) -> Void)

    required init(onComplete completionHandler: @escaping ((String) -> Void)) {
        self.completionHandler = completionHandler
    }

    /// Starts the text extraction process
    /// - Parameter image: The image which will be used to extract data
    /// Note: the cropped image is used for OCR
    func processImage(croppedImage: UIImage, originalImage: UIImage) {
        let vision = Vision.vision()
        let recognizer = vision.onDeviceTextRecognizer()
        let visionImage = VisionImage(image: croppedImage)
        recognizer.process(visionImage) { result, error in
            guard error == nil, let result = result else {
                return
            }

            if let resultString = self.getJsonStringResults(result, croppedImage: croppedImage, originalImage: originalImage) {
                self.completionHandler(resultString)
            }
        }
    }

    /// Creates a JSON string from VNRecognizedTextObservation
    /// - Parameters:
    ///   - results: Vision results
    ///   - sourceImage: The image in which the vision observations are made
    fileprivate func getJsonStringResults(_ results: VisionText, croppedImage: UIImage, originalImage: UIImage) -> String? {
        let visionResults = self.preprocessResults(results, sourceImage: croppedImage)
        let scanResult = ScanResult(visionResult: visionResults, croppedImage: croppedImage, originalImage: originalImage)

        do {
            let jsonData = try JSONEncoder().encode(scanResult)
            let jsonString = String(data: jsonData, encoding: .utf8)
            return jsonString
        } catch {
            NSLog("The results could not be converted to JSON")
            return nil
        }
    }

    /// Preprocesses the results by getting the top candidate (with the most confidence)
    /// after which they are turned into TextObservations
    /// - Parameters:
    ///   - results: Vision results
    ///   - sourceImage: The image in which the vision observations are made
    fileprivate func preprocessResults(_ results: VisionText, sourceImage: UIImage) -> VisionResult {
        var textObservations = [VisionResult.TextObservation]()

        let imageWidth = sourceImage.size.width * sourceImage.scale
        let imageHeight = sourceImage.size.height * sourceImage.scale

        for block in results.blocks {
            for line in block.lines {
                textObservations.append(
                    VisionResult.TextObservation(
                        text: line.text,
                        confidence: -1.0,
                        normalizedRect: self.normalizeRect(
                            sourceRect: line.frame,
                            imageWidth: imageWidth,
                            imageHeight: imageHeight
                        )
                    )
                )
            }
        }

        return VisionResult(
            sourceImageSize: VisionResult.Size(size: sourceImage.size),
            textObservations: textObservations
        )
    }

    private func normalizeRect(sourceRect: CGRect, imageWidth: CGFloat, imageHeight: CGFloat) -> VisionResult.Rect {
        let newX = sourceRect.minX / imageWidth;
        let newY = sourceRect.minY / imageHeight;
        let newWidth = sourceRect.width / imageWidth;
        let newHeight = sourceRect.height / imageHeight;
        return VisionResult.Rect(rect: CGRect(x: newX, y: newY, width: newWidth, height: newHeight))
    }
}
