import 'dart:math';

class JenksBinning {
  
  // Apply jenks binning on the given list (heights).
  // binCount can be used to regulate the number of resulting bins
  static List<double> jenksBinning(List<double> heights, int binCount) {
    if (heights.isEmpty) { return []; }

    // Sort the doubles from small to large
    heights.sort();

    int listsLength = heights.length + 1;
    List<List<double>> mat1 = new List<List<double>>.filled(listsLength ,
       new List<double>.filled(listsLength, 0.0)
    );
    List<List<double>> mat2 = new List<List<double>>.filled(listsLength ,
       new List<double>.filled(listsLength, 0.0)
    );

    for (var i = 1; i < binCount + 1; i++) {
      mat1[1][i] = 1.0;
      mat2[1][i] = 0.0;

      for (var j = 2; j < listsLength; j++) {
        mat2[j][i] = double.infinity;
      }
    }

    double v = 0.0;
    for (int l = 2; l < listsLength; l++) {
      double s1 = 0.0;
      double s2 = 0.0;
      double w = 0.0;

      for (int m = 1; m < l + 1; m++) {
        int i3 = l - m + 1;
        double val = heights[i3 - 1];

        s1 += val;
        s2 += pow(val, 2);

        w += 1;
        v = s2 - (pow(s1, 2) / w);

        int i4 = i3 - 1;

        if (i4.toDouble() != 0.0) {
          for (int j = 2; j < binCount + 1; j++) {
            if (mat2[l][j] >= (v + mat2[i4][j - 1])) {
              mat1[l][j] = i3.toDouble();
              mat2[l][j] = v + mat2[i4][j - 1];
            }
          }
        }
      }
    }

    int k = heights.length;
    List<double> kclass = new List<double>.filled(binCount + 1, 0.0);
    kclass[binCount] = heights[k - 1];

    int countNum = binCount;
    while (countNum >= 2) {
      int id = (mat1[k][countNum] - 2).toInt();
      kclass[countNum - 1] = heights[id];
      k = (mat1[k][countNum] - 1).toInt();
      countNum--;
    }

    return kclass;
  }
}