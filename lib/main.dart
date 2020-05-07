import 'dart:typed_data';
// import 'dart:wasm';

import 'package:doc_scan/algorithms/ReceiptDataExtractor.dart';
import 'package:doc_scan/models/ScanResult.dart';
import 'package:doc_scan/models/ReceiptObjects.dart';
import 'package:flutter/material.dart';

import 'package:flutter/services.dart';

import 'dart:async';
import 'dart:convert';
import 'package:esys_flutter_share/esys_flutter_share.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'dart:ui';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

// import 'package:flutter_mailer/flutter_mailer.dart';
// import 'dart:io';
// import 'package:path_provider/path_provider.dart';
// import 'package:image_gallery_saver/image_gallery_saver.dart';
// import 'package:permission_handler/permission_handler.dart';

void main() => runApp(MyApp());

typedef StartScanCallback = void Function();

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: MyHomePage(title: 'Receipt Scanner'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);
  final String title;
  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform = const MethodChannel('tests.mwsd.dev/documentScanner');

  String jsonVisionResults;
  List<ReceiptProduct> products;
  ReceiptContentLine totalRow;
  ReceiptDetails details;

  Uint8List originalImageData;
  Uint8List croppedImageData;

  Future<void> _openDocumentScanner() async {
    try {
      final String jsonResults = await platform.invokeMethod('openDocumentScanner');
      if (jsonResults == null || jsonResults == "scanFailed") {
        this._presentWarningToast("Receipt Scan Failed");
        return;
      } else if (jsonResults == "scanCancelled") {
        this._presentWarningToast("Receipt Scan Cancelled");
        return;
      }

      var collection = json.decode(jsonResults);
      ScanResult _results = ScanResult.fromJson(collection);
  
      Uint8List decodedOriginal, decodedCropped;
      if (_results.originalImageBase64 != null) decodedOriginal = base64.decode(_results.originalImageBase64);
      if (_results.croppedImageBase64 != null) decodedCropped = base64.decode(_results.croppedImageBase64);

      List results = ReceiptDataExtractor().getReceiptData(_results.visionResult);
      ReceiptContentLine totalRow = results[0];
      List<ReceiptProduct> products = results[1];
      ReceiptDetails details = results[2];

      setState(() {
        this.originalImageData = decodedOriginal;
        this.croppedImageData = decodedCropped;
        this.products = products;
        this.totalRow = totalRow;
        this.details = details;
        this.jsonVisionResults = json.encode(_results.visionResult);
      });

    } on PlatformException catch (e) {
      print("Unable to load the docuemnt scanner");
    }
  }

  void _sendJsonDataToDev() {
    if (this.jsonVisionResults != null) {
      this._showDialog("Sending Data", "Thank you for submitting the scan data. We will now open the mail app. You will only have to press send!", "Ok", this._openEmailWithDevData);
    } else {
      this._showDialog("Sending Data", "Before you can send any data, you will have to scan a receipt first.", "Ok");
    }
  }

  void _openEmailWithDevData() async {
    var now = new DateTime.now();
    var formatter = new DateFormat('HH:mm:ss dd-MM-yyyy');
    String formattedDate = formatter.format(now);
    String toMailId = 'mwiggerm@icloud.com';
    String subject = '[$formattedDate] ReceiptScan Test Data';
    String body = 'The following data contain the scan results:<br>' + this.jsonVisionResults;
    var url = 'mailto:$toMailId?subject=$subject&body=$body';
    var encoded = Uri.encodeFull(url);

    if (await canLaunch(encoded)) {
      await launch(encoded);
    } else {
      throw 'Could not launch $url';
    }
  }

  void _presentWarningToast(String message) {
    Fluttertoast.showToast(
          msg: message,
          toastLength: Toast.LENGTH_SHORT,
          gravity: ToastGravity.CENTER,
          timeInSecForIosWeb: 1,
          backgroundColor: Colors.red,
          textColor: Colors.white,
          fontSize: 16.0
        );
  }

  void _showDialog(String title, String body, String closeButtonTitle, [Function onClose]) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: new Text(title),
          content: new Text(body),
          actions: <Widget>[
            new FlatButton(
              child: new Text(closeButtonTitle),
              onPressed: () {
                Navigator.of(context).pop();
                if (onClose != null) {
                  onClose();
                }
              },
            ),
          ],
        );
      }
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Container(
        child: SingleChildScrollView(
          child: Column(
            children: <Widget>[
              Padding(
                  padding: EdgeInsets.only(top: 10),
                  child: Text("Version: 0.0.9"),
              ),
              Container(
                child: Padding(
                  padding: EdgeInsets.all(10),
                  child: Column(
                    children: <Widget>[
                      if (this.details != null) Text(this.details.storeName, style: TextStyle(fontSize: 30.0, fontWeight: FontWeight.bold)),
                      if (this.details != null) Text(this.details.purchaseDate, style: TextStyle(fontSize: 15.0, fontWeight: FontWeight.normal)),
                    ],
                  ),
                ),
              ),
              SizedBox(
                height: 300.0,
                child: _buildProductSummary(),
              ),
              Row(
                children: <Widget>[
                  Padding(
                    padding: EdgeInsets.all(10),
                    child: Text(
                      "Total: ${totalRow != null ? totalRow.getAmount().toStringAsFixed(2) : ""}",
                      style: TextStyle(fontSize: 20.0, fontWeight: FontWeight.bold, color: Colors.blue),
                    ),
                  )
                ],
              ),
              Padding(
                padding: EdgeInsets.all(12),
                child: RaisedButton(
                  onPressed: _sendJsonDataToDev,
                  child: const Text(
                    'Send scan data to developer for analysis',
                  ),
                )
              ),
              if (this.croppedImageData != null) this._buildImage(this.croppedImageData),
              if (this.originalImageData != null) this._buildImage(this.originalImageData),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        child: Icon(Icons.scanner, color: Colors.white, size: 35.0,),
        onPressed: () {
          ScanningInformationRoute route = ScanningInformationRoute(
            onDismiss: () {
              _openDocumentScanner();
            }
          );
          Navigator.push(context, MaterialPageRoute(builder: (context) => route));
        },
      ),
    );
  }

  Widget _buildProductSummary() {
    if (products == null) {
      return SizedBox.shrink();
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16.0),
      itemBuilder: (context, i) {
        if (i.isOdd) return Divider();
        final index = i ~/ 2;
        if (index < products.length) return _buildRow(products[index]);
        return null;
      },
    );
  }

  Widget _buildRow(ReceiptProduct product) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: List.generate(product.lines.length, (index) {
        if (product.lines[index].containsAmount) {
          return Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(product.lines[index].text),
              Text(
                "€${product.lines[index].getAmount().toStringAsFixed(2)}",
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
            ],
          );
        } else {
          return Text(product.lines[index].text);
        }
      }),
    );
  }

  Widget _buildImage(Uint8List data) {
    // Image newImage = Image.memory(data);

    return Stack(
      children: <Widget>[
        Image.memory(data),
        RaisedButton(
          onPressed: () async {
            print("Shared the image");
            await Share.file("output.png", "image.png", data, "image/png");
          },
          child: const Text(
            'Save this image',
          ),
        )
      ],
    );
  }
}

class ScanningInformationRoute extends StatelessWidget {

  const ScanningInformationRoute({ this.onDismiss });
  final StartScanCallback onDismiss;
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            _buildInfoRow(Icons.flash_on, "Enough light", "Make sure to have enough light for the best results. Using the fash light results in the best results."),
            _buildInfoRow(Icons.tonality, "High Contrast", "The best results are achieved when there is a high contrast between the receipt and the surface."),
            _buildInfoRow(Icons.sort, "Horizontal text lines", "Make sure to align the text lines horizontally to make sure all information can be extracted from the receipt."),
            Padding(
              padding: EdgeInsets.only(top: 16.0),
              child: RaisedButton(
                onPressed: () {
                  Navigator.pop(context);
                  onDismiss();
                },
                child: Text(
                  "Let's scan!",
                  style: TextStyle(color: Colors.white),
                ),
                color: Colors.blue,
              ),
            )
          ],
        )
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String iconSemanticLabel, String text) {
    return Padding(
      padding: EdgeInsets.all(16.0),
      child: Row(
        children: <Widget>[
          Padding(
            padding: EdgeInsets.only(right: 8.0),
            child: Icon(
              icon,
              color: Colors.blue,
              size: 50.0,
              semanticLabel: iconSemanticLabel,
            ),
          ),
          Flexible(
            child: Text(
              text,
              maxLines: 4,
            )
          )
        ],
      )
    );
  }
}