import 'dart:math';

import 'package:doc_scan/algorithms/JenksBinning.dart';
import 'package:doc_scan/models/TextObservation.dart';
import 'package:doc_scan/models/ReceiptObjects.dart';
import 'package:flutter/material.dart';


class ReceiptDataExtractor {
  String countryCode = "NL-nl";
  final Map receiptKeywordData = {
   "keywords_per_country": {
        "NL-nl" : {
            "total_indication": ["totaal", "te betalen", "total", "tot", "tot."],
            "negative_patterns": ["subtotaal", "totaal korting", "totaalkorting"],
            "date-pattern": r"([0-2][0-9]|(3)[0-1]|[0-9])(-)(((0)[0-9])|((1)[0-2])|([1-9]))(-)\d{4}",
        },
        "UK-en" : {
            "total_indication": ["total", "amount due"],
            "negative_patterns": ["subtotal"]
        }
    },
    "currencies": [
        { "eur": "euro" },
        { "€": "euro" },
        { "euro": "euro" },
        { "pound": "pound" },
        { "£": "pound" },
        { "gbp": "pound" }
    ]
  };

  List<Object> getReceiptData(VisionResult visionResults) {
    if (visionResults.textObservations.length <= 0) return [null, null, null];

    // Calculate the size of small and large text (i.e. normal text and titles)
    List<Rect> boundingBoxes = visionResults.textObservations.map((i) => i.normalizedRect).toList();
    List<double> textHeightBinning = _getTextHeightMidpoint(boundingBoxes);
    double smallTextHeight = textHeightBinning[1];
    double largeTextHeight = textHeightBinning[2];

    // Group text observations found on a single line
    List<ReceiptContentLine> receiptLines = _groupVisionResultOnRow(visionResults.textObservations, smallTextHeight / 2, 0.2);

    // Fill in the cash amount values for the rows
    _fillAmountValues(receiptLines);
    // Filter the rows containing a cash abount
    List<ReceiptContentLine> receiptLinesContainingValue = receiptLines.where((line) => line.containsAmount).toList();
    if (receiptLinesContainingValue.length == 0) return []; // No values are found

    // Find the index a row containing a word in "total_indication"
    int receiptIndexOfTotalRow = _getReceiptIndexOfTotalRow(receiptLinesContainingValue);
    // Convert the index of within the lines containing cash value to the index within the whole receipt
    int valueLinesTotalIndex = 0;
    for (ReceiptContentLine line in receiptLinesContainingValue) {
      if (line.index == receiptIndexOfTotalRow) break;
      valueLinesTotalIndex++;
    }

    // Pair rows containing a value in product groups
    List<List<int>> allGroupedListIndices = _performPairingAnalysis(receiptLinesContainingValue.sublist(0, valueLinesTotalIndex));
    List<ReceiptProduct> products = _createProductsBasedOnGroups(receiptLines, allGroupedListIndices);

    // Try to find details like the store name and the purchase date
    ReceiptDetails receiptDetails = _getReceiptDetails(receiptLines, largeTextHeight);

    // Return the found ata
    if (receiptIndexOfTotalRow < 0) {
      return [null, products, receiptDetails];
    } else {
      return [receiptLines[receiptIndexOfTotalRow], products, receiptDetails];
    }
  }

  ReceiptDetails _getReceiptDetails(List<ReceiptContentLine> lines, double largeTextHeight) {
    String storeName = _findStoreName(lines, largeTextHeight);
    String purchaseDate = _findPurchaseDate(lines);
    return ReceiptDetails(storeName, purchaseDate);
  }

  String _findStoreName(List<ReceiptContentLine> lines, double largeTextHeight) {
    if (lines.length == 0) return "";

    List<ReceiptContentLine> topContent = lines.sublist(0, min(6, lines.length));
    List<String> storeNameElements = List<String>();

    for (ReceiptContentLine line in topContent) {
      if (line.boundingBox.size.height >= largeTextHeight) {
        storeNameElements.add(line.text);
      }
    }

    if (storeNameElements.length > 0) {
      return storeNameElements.join(" ");
    } else {
      return topContent.first.text;
    }
  }

  String _findPurchaseDate(List<ReceiptContentLine> lines) {
    RegExp dateRegex = new RegExp(this.receiptKeywordData["keywords_per_country"][this.countryCode]["date-pattern"]);
    List<String> possibleDates = new List<String>();

    for (ReceiptContentLine line in lines) {
      String lowercasedLine = line.text.toLowerCase();
      if (lowercasedLine.contains(dateRegex)) {
        String foundDateStr = dateRegex.stringMatch(lowercasedLine).toString();
        possibleDates.add(foundDateStr);
      }
    }

    if (possibleDates.length > 0) {
      return possibleDates.first;
    } else {
      return "";
    }
  }

  // Returns the indices in the receipts (thus not the list indices)
  List<List<int>> _performPairingAnalysis(List<ReceiptContentLine> productRows) {
    int productRowsLength = productRows.length;
    if (productRowsLength == 0) return []; 

    List<String> totalKeywords = this.receiptKeywordData["keywords_per_country"][this.countryCode]["total_indication"];
    List<String> negativePatterns = this.receiptKeywordData["keywords_per_country"][this.countryCode]["negative_patterns"];
    RegExp totalKeywordRegexPattern = new RegExp("(" + totalKeywords.join("|") + negativePatterns.join("|") + ")");

    List<List<int>> allGroupedIndices = new List<List<int>>();

    ReceiptContentLine lastItem = productRows.last;
    int currentStartListIndex = 0; // 0 is the base


    while (currentStartListIndex < productRowsLength) {
      ReceiptContentLine baseRow = productRows[currentStartListIndex];
      double additionCalculation = baseRow.getAmount();
      double negationCalculation = baseRow.getAmount();
      List<int> groupedIndices = [baseRow.index];
      currentStartListIndex++;

      int loopIndex = currentStartListIndex;

      for (ReceiptContentLine line in productRows.sublist(currentStartListIndex)) {
        String lowercasedLine = line.text.toLowerCase();
        if ((additionCalculation == line.getAmount() || negationCalculation == line.getAmount())
            && !lowercasedLine.contains(totalKeywordRegexPattern)) {
          // This line is the last line in the group
          groupedIndices.add(line.index);
          currentStartListIndex = loopIndex + 1;
          break;
        } else if (line == lastItem) {
          // This is the last line and no relationship has been found
          groupedIndices = [baseRow.index];
          break;
        } else {
          // Use absolute to remove negative numbers influence
          additionCalculation += line.getAmount().abs();
          negationCalculation -= line.getAmount().abs();
          groupedIndices.add(line.index);
          loopIndex++;
        }
      }
      allGroupedIndices.add(groupedIndices);
    }
    return allGroupedIndices;
  }




  List<ReceiptProduct> _createProductsBasedOnGroups(List<ReceiptContentLine> productRows, List<List<int>> groupIndices) {
    List<ReceiptProduct> products = new List<ReceiptProduct>();

    // The group indices only contain value. To also include intermediary rows with e.g. a product code
    // we take the start and end index of the group and include all rows inbetween in the product object.
    for (List<int> indexGroup in groupIndices) {
      int startIndex = indexGroup.first;
      int endIndex = indexGroup.last;
      ReceiptProduct newProduct = new ReceiptProduct();

      newProduct.setLines(productRows.sublist(startIndex, endIndex + 1));
      products.add(newProduct);
    }

    return products;
  }



  /// Calculates the midpoint of the text found in the receipt. This data can be used
  /// to separate large from small text.
  List<double> _getTextHeightMidpoint(List<Rect> boundingBoxes) {
    List<double> textHeights = new List<double>();
    for (Rect boundingBox in boundingBoxes) {
      textHeights.add(boundingBox.size.height);
    }

    List<double> binningBoundaries = JenksBinning.jenksBinning(textHeights, 2);

    return binningBoundaries;
  }

  // alignmentAdjustmentPercentage: Value between 0 and 1
  List<ReceiptContentLine> _groupVisionResultOnRow(List<TextObservation> textObservations, double verticalAlignmentThreshold, double alignmentAdjustmentPercentage) {
    assert(alignmentAdjustmentPercentage >= 0.0 && alignmentAdjustmentPercentage <= 1.0, "alignmentAdjustmentPercentage should be between 0 (0%) and 1 (100%)");
    List<ReceiptContentLine> groups = new List<ReceiptContentLine>();
    List<TextObservation> groupItems = new List<TextObservation>();
    TextObservation previous;

    double adjustedVerticalThreshold = verticalAlignmentThreshold + (verticalAlignmentThreshold * alignmentAdjustmentPercentage);

    // sort the array such that the data is sorted on the y axis (small to large)
    textObservations.sort((a, b) => a.normalizedRect.yPos.compareTo(b.normalizedRect.yPos));

    int currentLineIndex = 0;

    // Loop trough the items and group the rows based on adjustedVerticalThreshold
    for (TextObservation item in textObservations) {
      if (groupItems.isEmpty) {
        groupItems.add(item);
        previous = item;
      } else {
        // Check if the last item in the list is on (about) the same height
        // as the new vision observation

        // print("${item.text} (${item.normalizedRect.yPos}) + ${previous.text} (${previous.normalizedRect.yPos}) = ${(item.normalizedRect.yPos - previous.normalizedRect.yPos).abs()} < $adjustedVerticalThreshold ($verticalAlignmentThreshold)");
        // print("${previous.normalizedRect.yPos - (adjustedVerticalThreshold/2)} - ${previous.normalizedRect.yPos + (adjustedVerticalThreshold/2)}");
        // print("");
        
        if ((item.normalizedRect.yPos - previous.normalizedRect.yPos).abs() < adjustedVerticalThreshold) {
          groupItems.add(item);
        } else {
          // sort the array such that the data is sorted on the x axis (small to large)
          groupItems.sort((a, b) => a.normalizedRect.xPos.compareTo(b.normalizedRect.xPos));
          String textOnLine = groupItems.map((i) => i.text).join(" ");

          List<Rect> boundingBoxes = groupItems.map((i) => i.normalizedRect).toList();
          Rect finalBoundingBox = union(boundingBoxes);
          ReceiptContentLine newLine = ReceiptContentLine(currentLineIndex, textOnLine, finalBoundingBox);
          groups.add(newLine);

          groupItems = [item];
          currentLineIndex++;
        }
        previous = item;
      }
    }

    // Add last items
    groupItems.sort((a, b) => a.normalizedRect.xPos.compareTo(b.normalizedRect.xPos));
    String textOnLine = groupItems.map((i) => i.text).join(" ");
    List<Rect> boundingBoxes = groupItems.map((i) => i.normalizedRect).toList();
    Rect finalBoundingBox = union(boundingBoxes);
    ReceiptContentLine newLine = ReceiptContentLine(currentLineIndex, textOnLine, finalBoundingBox);
    groups.add(newLine);
    // SHOULD WE ADD groups.add(newLine);

    return groups;
  }

  // This function fills in the prices on the rows in the receipt
  void _fillAmountValues(List<ReceiptContentLine> lines) {
    for (ReceiptContentLine line in lines) {
      double cashValue = getCashValue(line.text);
      if (cashValue != null) {
        line.setAmount(cashValue);
      }
    }
  }

  // Function returns the index of the row if it is able to find the most likely total value.
  // If no such value can be found, it will return -1 to indicate it was unable to find this value.
  int _getReceiptIndexOfTotalRow(List<ReceiptContentLine> productRows) {
    List<String> totalKeywords = this.receiptKeywordData["keywords_per_country"][this.countryCode]["total_indication"];
    List<String> negativePatterns = this.receiptKeywordData["keywords_per_country"][this.countryCode]["negative_patterns"];

    // Find values most occurring in the receipt (to collect evidence for a specific value)
    var valueCounts = new Map();

    // Join the keywords in a regex pattern
    RegExp regexPattern = new RegExp("(" + totalKeywords.join("|") + ")");
    // Remove all patterns contained in the negative patterns
    RegExp negativeRegexPattern = new RegExp("(" + negativePatterns.join("|") + ")");

    for (ReceiptContentLine line in productRows) {
      String lowercasedLine = line.text.toLowerCase();
      // If the text in the line matches the regex pattern
      if (lowercasedLine.contains(regexPattern) && (!lowercasedLine.contains(negativeRegexPattern))) {
        double value = line.getAmount();
        int index = line.index;
        if (valueCounts.containsKey(value)) {
          valueCounts[value]["count"] += 1;
          valueCounts[value]["indices"].add(index);
          valueCounts[value]["lineTexts"].add(lowercasedLine);
        } else {
          valueCounts[value] = {
            "count": 1,
            "indices": [index],
            "lineTexts": [lowercasedLine]
          };
        }
      }
    }

    if (valueCounts.length < 1) return -1;

    // Find the value most occurring in the receipt
    double maxKey;
    int maxCount = 0;
    for (MapEntry entry in valueCounts.entries) {
      Map value = entry.value;
      double key = entry.key;
      int count = value["count"];

      if (count > maxCount) {
        maxCount = count;
        maxKey = key;
      }
    }

    List<int> indices = valueCounts[maxKey]["indices"];
    return indices.first;
  }



  // MARK: - Helper functions

  Rect union(List<Rect> rects) {
    if (rects.length <= 0) {
      return new Rect(0, 0, new Size(0, 0));
    }
    // assert(rects.length > 0, "Unable to union 0 rects");
    // Rect baseRect = rects.first;

    // rects.first.xPos
    Rect firstRect = rects.first;
    double xMin = firstRect.xMin;
    double yMin = firstRect.yMin;
    double xMax = firstRect.xMax;
    double yMax = firstRect.yMax;


    for (Rect rect in rects.sublist(1)) {
      // x -> Smaller => Replace
      if (rect.xMin < xMin) { xMin = rect.xMin; }
      if (rect.yMin < yMin) { yMin = rect.yMin; }
      
      if (rect.xMax > xMax) { xMax = rect.xMax; }
      if (rect.yMax > yMax) { yMax = rect.yMax; }
      
      // y -> Smaller => Replace
      // if (rect.yPos < baseRect.yPos) { baseRect.yPos = rect.yPos; }

      // // width -> Larger => Replace
      // if (rect.xPos + rect.size.width > baseRect.size.width) { baseRect.size.width = rect.size.width; }

      // // height -> Larger => Replace
      // if (rect.size.height > baseRect.size.height) { baseRect.size.height = rect.size.height; }
    }

    return new Rect(xMin, yMin, new Size(xMax - xMin, yMax - yMin));
  }

  double getCashValue(String inString) {
    List<double> possibleValues = List<double>();

    Iterable<String> _allStringMatches(String text, RegExp regExp) => 
      regExp.allMatches(text).map((m) => m.group(0));

    RegExp regex = new RegExp(
      r"(-)?\d+(\.|,)(\ ){0,2}\d{2}",
      caseSensitive: false,
      multiLine: false,
    );

    Iterable<String> matches = _allStringMatches(inString, regex);
    int searchStartIndex = 0;
    var stringLength = inString.length;
  
    for (String match in matches) {
      var matchLength = match.length;
      
      // The length of a cash amout has to be larger than 3
      if (matchLength >= 3) {
        match = match.replaceAll(",", ".");

        var subString = inString.substring(searchStartIndex, stringLength);
        var startIndex = subString.indexOf(new RegExp(match));
        var endIndex = startIndex + matchLength;

        // Remove matches like "123.45.67" and percentages like "12,34%"
        if (endIndex < stringLength) {
          String nextChar = subString[endIndex];
          if (nextChar == "." || nextChar == "," || nextChar == "%" || _isNumeric(nextChar)) {
            continue;
          }
        }

        // Remove matches like "123.45.67" and percentages like "%12,34"
        if (startIndex > 0) {
          String prevChar = subString[startIndex - 1];
          if (prevChar == "." || prevChar == "," || prevChar == "%" || _isNumeric(prevChar)) {
            continue;
          }
        }

        // Check if the found value contains a negation 
        match = match.replaceAll(" ", "");
        possibleValues.add(double.parse(match));
      }
    }
  
    // MARK: - Currently the last cash value is returned
    if (possibleValues.length > 0) {
      return possibleValues.last;
    } else {
      return null;
    }
  }

  bool _isNumeric(String str) {
    if(str == null) {
      return false;
    }
    return double.tryParse(str) != null;
  }
}


