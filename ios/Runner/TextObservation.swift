//
//  TextObservation.swift
//  Runner
//
//  Created by Marc Wiggerman on 21/02/2020.
//  Copyright © 2020 The Chromium Authors. All rights reserved.
//

import Foundation
import Vision

/// A codable container for vision results
struct VisionResult: Codable {
    var sourceImageSize: Size
    var textObservations: [TextObservation]

    // TODO: Include image

    struct TextObservation: Codable {
        var text: String
        var confidence: Float
        var normalizedRect: Rect

        init(text: String, confidence: Float, normalizedRect: Rect) {
            self.text = text
            self.confidence = confidence
            self.normalizedRect = normalizedRect
        }
    }

    struct Rect: Codable {
        var xPos: Float
        var yPos: Float
        var size: Size

        init (rect: CGRect, invertCoordinates: Bool = false) {
            // Note that incoming positions go from 1.0 (top) to 0.0 (bottom)
            // This is converted the other way around
            self.xPos = Float(rect.minX)
            self.yPos = Float(rect.minY)
            self.size = Size(size: rect.size)

            if invertCoordinates {
//                self.xPos = 1.0 - self.xPos
                self.yPos = 1.0 - self.yPos
            }
        }
    }

    struct Size: Codable {
        var width: Float
        var height: Float

        init (size: CGSize) {
            self.width = Float(size.width)
            self.height = Float(size.height)
        }
    }
}
