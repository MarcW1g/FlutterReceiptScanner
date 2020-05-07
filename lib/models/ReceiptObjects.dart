import 'package:doc_scan/models/TextObservation.dart';

// Represents the details of the receipt (store name and purchase date)
class ReceiptDetails {
  String storeName;
  String purchaseDate;
  ReceiptDetails(this.storeName, this.purchaseDate);
}

// Represents the content on a single line in the receipt
class ReceiptContentLine {
  int index;
  String text;
  Rect boundingBox;

  double _amount = 0.0;
  bool containsAmount = false;
  ReceiptContentLine(this.index, this.text, this.boundingBox);

  void setAmount(double amount) {
    this._amount = amount;
    this.containsAmount = true;
  }

  double getAmount() {
    return this._amount;
  }
}

// Represents a product (i.e. multiple ReceiptContentLines)
class ReceiptProduct {
  List<ReceiptContentLine> lines;

  void setLines(List<ReceiptContentLine> lines) {
    this.lines = lines;
  }

  List<Rect> getBoundingBoxes() {
    List<Rect> rects = new List<Rect>();

    for (ReceiptContentLine line in lines) {
      rects.add(line.boundingBox);
    }

    return rects;
  }

  ReceiptContentLine getMainProduct() {
    return lines.last;
  }
  
  double getMainPrice() {
    return this.getMainProduct().getAmount();
  }
}