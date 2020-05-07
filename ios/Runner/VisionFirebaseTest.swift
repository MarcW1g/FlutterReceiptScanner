////
////  VisionFirebaseTest.swift
////  Runner
////
////  Created by Marc Wiggerman on 28/04/2020.
////  Copyright © 2020 The Chromium Authors. All rights reserved.
////
//
//import Foundation
//
//@available(iOS 13.0, *)
//class VisionFirebaseTest {
//    var documentsUrl: URL {
//        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
//    }
//
//    private let BASE_PATH = "testImages"
//    private let imageNames = ["receipt1", "receipt2", "receipt3", "receipt4", "receipt5"]
//    private var images = [UIImage]()
//
//    private var appleResults = [String]()
//    private var firebaseResults = [String]()
//
//    var appleCount = 0
//    var firebaseCount = 0
//
//    public func run(completion: () -> ()) {
//        self.loadImages()
//
//        for (index, image) in images.enumerated() {
//            let firebaseOCR = FirebaseImageToText { (jsonString) in
//                self.firebaseResults.append(jsonString)
//                self.appleCount += 1
//            }
//            let appleOCR = VisionImageToText { (jsonString) in
//                self.appleResults.append(jsonString)
//                self.firebaseCount += 1
//            }
//
//            print("Working on \(index)....")
//            print("Start Apple Vision")
//            appleOCR.configureVision()
//            appleOCR.processImage(croppedImage: image, originalImage: image)
//
//            print("Start Firebase Vision")
//            firebaseOCR.processImage(croppedImage: image, originalImage: image)
//
//            print("Done!")
//        }
//
//
//        DispatchQueue.main.asyncAfter(deadline: .now() + 20) {
//            self.printResults()
//        }
//    }
//
//    private func printResults() {
//        DispatchQueue.main.async {
//            print("Apple Results:")
//            for string in self.appleResults {
//                print(string)
//                print("\n\n")
//            }
//
//            print("Google Results:")
//            for string in self.firebaseResults {
//                print(string)
//                print("\n\n")
//            }
//
//        }
//
//    }
//
//    private func loadImages() {
//        var optionalImages = [UIImage?]()
//        for imageName in imageNames {
//            optionalImages.append(self.load(fileName: imageName))
//        }
//        self.images = optionalImages.compactMap { $0 }
//    }
//
//    private func load(fileName: String) -> UIImage? {
////        let fileURL = documentsUrl.appendingPathComponent(BASE_PATH, isDirectory: true).appendingPathComponent(fileName)
////
////        do {
////            let imageData = try Data(contentsOf: fileURL)
////            return UIImage(data: imageData)
////        } catch {
////            print("Error loading image : \(error)")
////        }
//        return UIImage(named: fileName)
//    }
//}
