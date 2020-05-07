//
//  ScanResult.swift
//  Runner
//
//  Created by Marc Wiggerman on 26/03/2020.
//  Copyright © 2020 The Chromium Authors. All rights reserved.
//

import Foundation
import UIKit

struct ScanResult: Codable {
    var visionResult: VisionResult!
    var croppedImageBase64: String!
    var originalImageBase64: String!

    init (visionResult: VisionResult, croppedImage: UIImage, originalImage: UIImage) {
        self.visionResult = visionResult
        self.croppedImageBase64 = self.convertImageToBase64(croppedImage)
        self.originalImageBase64 = self.convertImageToBase64(originalImage)
    }

    func convertImageToBase64(_ image: UIImage) -> String {
        guard let imageData = image.pngData() else { return "" }
        return imageData.base64EncodedString()
    }
}
