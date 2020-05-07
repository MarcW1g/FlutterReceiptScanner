package com.example.doc_scan.model;

public class ScanResult {
    public VisionResult visionResult;
    public String originalImageBase64;
    public String croppedImageBase64;

    public ScanResult(VisionResult visionResults, String originalImageBase64, String croppedImageBase64) {
        this.visionResult = visionResults;
        this.originalImageBase64 = originalImageBase64;
        this.croppedImageBase64 = croppedImageBase64;
    }
}
