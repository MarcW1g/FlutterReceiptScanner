import 'package:doc_scan/models/TextObservation.dart';

class ScanResult {
  final VisionResult visionResult;
  final String originalImageBase64;
  final String croppedImageBase64;

  ScanResult(this.visionResult, this.originalImageBase64, this.croppedImageBase64);

factory ScanResult.fromJson(Map<String, dynamic> json){
    return ScanResult(
      VisionResult.fromJson(json['visionResult']),
      json['originalImageBase64'],
      json['croppedImageBase64']
    );
  }
}