import 'package:test/test.dart';
import 'package:doc_scan/algorithms/ReceiptDataExtractor.dart';
import 'package:doc_scan/models/TextObservation.dart';
import 'package:doc_scan/models/ScanResult.dart';
import 'dart:convert';


void main() {

  group('test_get_receipt_data', () {
    test('test_no_data', () {
      final String jsonResults ='{"visionResult":{"textObservations":[],"sourceImageSize":{"width":996,"height":3290}},"croppedImageBase64":"","originalImageBase64":""}';
      var collection = json.decode(jsonResults);
      ScanResult _results = ScanResult.fromJson(collection);

      final receiptDataExtractor = ReceiptDataExtractor();
      List<Object> results = receiptDataExtractor.getReceiptData(_results.visionResult);

      expect(results, [null, null, null]);
    });

    // test('test_one_row_no_total', () {
    //   final String jsonResults ='{"visionResult":{"textObservations":[{"text":"11,29","confidence":-1,"normalizedRect":{"xPos":0.7609970674486803,"yPos":0.12608695652173912,"size":{"width":0.06891495601173026,"height":0.060869565217391314}}},{"text":"BEFCHONING CREME","confidence":-1,"normalizedRect":{"xPos":0.20674486803519063,"yPos":0.12608695652173912,"size":{"width":0.23753665689149558,"height":0.06521739130434781}}}],"sourceImageSize":{"width":996,"height":3290}},"croppedImageBase64":"","originalImageBase64":""}';
    //   var collection = json.decode(jsonResults);
    //   ScanResult _results = ScanResult.fromJson(collection);

    //   final receiptDataExtractor = ReceiptDataExtractor();
    //   List<Object> results = receiptDataExtractor.getReceiptData(_results.visionResult);

    //   expect(results[0], null);

    //   print("$results");
    //   // expect(results, [null, null, null]);
    // });
  });




  group('test_get_cash', () {
    test('test_get_cash_3_2', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      double value = receiptDataExtractor.getCashValue("€123,00");

      expect(value, 123);
    });

    test('test_get_cash_2_2', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      double value = receiptDataExtractor.getCashValue("€12,00");

      expect(value, 12);
    });

    test('test_get_cash_1_2', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      double value = receiptDataExtractor.getCashValue("€1,00");

      expect(value, 1);
    });

    test('test_get_cash_1_1', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      double value = receiptDataExtractor.getCashValue("€1,0");

      expect(value, null);
    });
  });

  group('test_union_rects', () {
    test('test_none', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      Rect expected = new Rect(0, 0, new Size(0, 0));
      Rect fin = receiptDataExtractor.union([]);
      expect(fin, expected);
    });

    test('test_single', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      Rect r1 = new Rect(0, 0, new Size(100, 100));
      Rect fin = receiptDataExtractor.union([r1]);
      expect(fin, r1);
    });

    test('test_double', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      Rect r1 = new Rect(0, 0, new Size(100, 100));
      Rect r2 = new Rect(50, 50, new Size(200, 200));
      Rect fin = receiptDataExtractor.union([r1, r2]);
      Rect expected = new Rect(0, 0, new Size(250, 250));
      expect(fin, expected);
    });

    test('test_tripple', () {
      final receiptDataExtractor = ReceiptDataExtractor();
      Rect r1 = new Rect(0, 10, new Size(100, 100));
      Rect r2 = new Rect(50, 50, new Size(200, 200));
      Rect r3 = new Rect(300, 50, new Size(200, 200));
      Rect fin = receiptDataExtractor.union([r1, r2, r3]);

      Rect expected = new Rect(0, 10, new Size(500, 240));

      print(fin.size.width);
      print(fin.size.height);

      expect(fin, expected);
    });
  });
}