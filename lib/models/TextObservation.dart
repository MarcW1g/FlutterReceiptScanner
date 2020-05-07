import 'dart:convert';

import 'package:equatable/equatable.dart';

class VisionResult extends Equatable {
  final Size sourceImageSize;
  final List<TextObservation> textObservations;

  VisionResult(this.sourceImageSize, this.textObservations);

  factory VisionResult.fromJson(Map<String, dynamic> json){
    var list = json['textObservations'] as List;
    List<TextObservation> observationList = list.map((i) =>
      TextObservation.fromJson(i)
    ).toList();

    return VisionResult(
      Size.fromJson(json['sourceImageSize']),
      observationList
    );
  }

  Map<String, dynamic> toJson() => {
    'sourceImageSize': sourceImageSize.toString(),
    'textObservations': jsonEncode(textObservations),
  };

  @override
  List<Object> get props => [sourceImageSize, textObservations];
}

class TextObservation extends Equatable {
  final String text;
  final double confidence;
  final Rect normalizedRect;

  TextObservation(this.text, this.confidence, this.normalizedRect);

  factory TextObservation.fromJson(Map<String, dynamic> json){
    return TextObservation(
      json['text'],
      json['confidence'].toDouble(),
      Rect.fromJson(json['normalizedRect'])
    );
  }

  Map<String, dynamic> toJson() => {
    'text': text,
    'confidence': confidence,
    'normalizedRect': normalizedRect.toJson(),
  };

  @override
  List<Object> get props => [text, confidence, normalizedRect];
}

class Rect extends Equatable {
  double xPos;
  double yPos;
  Size size;

  Rect(this.xPos, this.yPos, this.size);

  double get xMin => xPos;
  double get yMin => yPos;
  double get xMax => xPos + size.width;
  double get yMax => yPos + size.height;

  factory Rect.fromJson(Map<String, dynamic> json){
    return Rect(
      json['xPos'].toDouble(),
      json['yPos'].toDouble(),
      Size.fromJson(json['size'])
    );
  }

  Map<String, dynamic> toJson() => {
    'xPos': xPos,
    'yPos': yPos,
    'size': size.toJson(),
  };

  @override
  List<Object> get props => [xPos, yPos, size];
}

class Size extends Equatable {
  double width;
  double height;

  Size(this.width, this.height);

  factory Size.fromJson(Map<String, dynamic> json){
    return Size(
      json['width'].toDouble(), 
      json['height'].toDouble()
    );
  }

  Map<String, dynamic> toJson() => {
    'width': width,
    'heigth': height,
  };

  @override
  List<Object> get props => [width, height];
}