import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

typedef void DocumentScannerViewCreatedCallback(DocumentScannerViewController controller);

class DocumentScannerView extends StatefulWidget {
  const DocumentScannerView({
    Key key,
    this.onDocumentScannerViewCreated,
  }) : super(key: key);

  final DocumentScannerViewCreatedCallback onDocumentScannerViewCreated;

  @override
  State<StatefulWidget> createState() => DocumentScannerViewState();
}

class DocumentScannerViewState extends State<DocumentScannerView> {
  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return AndroidView(
        viewType: 'documentscannerview',
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
        viewType: 'documentscannerview',
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    } else {
      return Text('$defaultTargetPlatform is not yet supported');
    }
  }

  void _onPlatformViewCreated(int id) {
    if (widget.onDocumentScannerViewCreated == null) return;
    widget.onDocumentScannerViewCreated(new DocumentScannerViewController(id));
  }
}

class DocumentScannerViewController {
  MethodChannel _channel;

  DocumentScannerViewController(int id) {
    this._channel = new MethodChannel('documentscannerview$id');
  }

  Future<void> scanDocument() async {
    return _channel.invokeMethod('scanDocument');
  }
}