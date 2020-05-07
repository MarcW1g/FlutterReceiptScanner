package com.example.doc_scan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.doc_scan.model.Quad;
import com.example.doc_scan.view.PaperRectangle;
import com.example.doc_scan.view.PaperRectangleCallback;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

    public class CropActivity extends Activity implements PaperRectangleCallback {
    private static final String TAG = "DocScanner::CropAct";

    public static Quad contour;
    public static Mat image;
    public static int openCVCamWidth;
    public static int openCVCamHeight;

    private Bitmap bm;
    private Quad sharedQuad;

    private Mat croppedGrayImage;
    private Mat croppedThresholdImage;

    ImageView scanPreviewImageView;

    private ImageButton nextButton;
    private ImageButton prevButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        scanPreviewImageView = findViewById(R.id.preview_image_view);
        PaperRectangle paperRectangle = findViewById(R.id.quad_overlay);
        paperRectangle.registerPaperRectangleCallback(this);
        nextButton = findViewById(R.id.done_crop_button);
        prevButton = findViewById(R.id.back_to_scan_button);

        scanPreviewImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //Remove it here unless you want to get this callback for EVERY
                //layout pass, which can get you into infinite loops if you ever
                //modify the layout from within this method.
                scanPreviewImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Rotate image and convert to bitmap:
                Mat img = image;

                Core.flip(img.t(), img, 1);
                bm = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img, bm);

                // Scale, Rotate and Transform the contour to fit the screen/the receipt
                Quad drawableContour = prepareContour(contour, scanPreviewImageView);

                // Show the receipt image
                scanPreviewImageView.setImageBitmap(bm);

                // Present the quad contour on the image
                drawableContour.resetCorrectPointPerCorner();
                sharedQuad = drawableContour;
                paperRectangle.previewCorners(sharedQuad);
            }
        });

        nextButton.setOnClickListener((View view) -> {
            processImagesForPreview(this.image, sharedQuad);
            if (croppedGrayImage != null && croppedThresholdImage != null) {
                ReceiptPreviewActivity.croppedGrayImage = croppedGrayImage;
                ReceiptPreviewActivity.croppedThresholdedImage = croppedThresholdImage;
                ReceiptPreviewActivity.shouldShowThresholded = shouldShowThresholded(croppedGrayImage);
                ReceiptPreviewActivity.originalImage = this.image;
                Intent intent = new Intent(getBaseContext(), ReceiptPreviewActivity.class);
                startActivity(intent);
            }
        });

        prevButton.setOnClickListener((View view) -> {
            finish();
        });
    }

    private Quad prepareContour(Quad contour, ImageView imageView) {
        Quad drawableContour;
        int imageViewHeight = imageView.getHeight();
        int imageViewWidth = imageView.getWidth();

        if (contour != null && contour.contourList.size() > 0) {
            Point centerPoint = new Point(openCVCamWidth / 2, openCVCamHeight / 2);
            contour.rotateCornerPoints(centerPoint);

            // Rotate base
            Point newXY = contour.rotatePointAround(new Point(0, openCVCamWidth), centerPoint, 90);

            // Move base to center
            double targetX = ((double) imageViewWidth / 2);
            double baseX = ((double) openCVCamWidth / 2) + newXY.x;
            double moveX = targetX - baseX;

            double targetY = ((double) imageViewHeight / 2);
            double baseY = ((double) openCVCamHeight / 2) + newXY.y;
            double moveY = targetY - baseY;
            contour.moveCornerPoints((int) moveX, (int) moveY);

            // We assume the openCV scan view is centered in the view
            // Now we scale the contour to fit the receipt/screen height
            double scaleHeight = (double) imageViewHeight / (double) openCVCamHeight;
            Point scaleCenter = new Point(imageViewWidth / 2, imageViewHeight / 2);
            contour.scaleCornerPoints(scaleHeight, scaleCenter);
            drawableContour = contour;
        } else {
            int padding = 200;
            Point[] points = {
                    new Point(padding, padding),
                    new Point(imageViewWidth - padding, padding),
                    new Point(imageViewWidth - padding, imageViewHeight - padding),
                    new Point(padding, imageViewHeight - padding),
            };
            MatOfPoint simplePoints = new MatOfPoint();
            simplePoints.fromArray(points);
            List<MatOfPoint> contourTemp = new ArrayList<>();
            contourTemp.add(simplePoints);
            drawableContour = new Quad(contourTemp);
        }

        return drawableContour;
    }

    private void processImagesForPreview(Mat image, Quad cornerQuad) {
        // Crop the image
        Mat cropped = cropPicture(image, cornerQuad);
        if (cropped == null) return;

        // Grayscale the image
        Mat mGray = new Mat(cropped.size(), CvType.CV_8UC4);
        Imgproc.cvtColor(cropped, mGray, Imgproc.COLOR_RGBA2GRAY);

        Mat mThreshold = new Mat(mGray.size(), CvType.CV_8UC4);
        Imgproc.threshold(mGray, mThreshold, 150, 255, Imgproc.THRESH_BINARY +  Imgproc.THRESH_OTSU);

        this.croppedGrayImage = mGray;
        this.croppedThresholdImage = mThreshold;
    }

    private double grayMeanThreshold = 170;
    private boolean shouldShowThresholded(Mat grayImage) {
        Scalar mean = Core.mean(grayImage);
        double meanValue = mean.val[0];
        Log.i(TAG, "(mean) " + meanValue);
        return meanValue < grayMeanThreshold;
    }

    private Mat cropPicture(Mat sourceImage, Quad cornerQuad) {
        if (cornerQuad == null) { return null; }

        double widthBottom = Math.sqrt(Math.pow(cornerQuad.rb.x - cornerQuad.lb.x, 2) + Math.pow(cornerQuad.rb.y - cornerQuad.lb.y, 2));
        double widthTop = Math.sqrt(Math.pow(cornerQuad.rt.x - cornerQuad.lt.x, 2) + Math.pow(cornerQuad.rt.y - cornerQuad.lt.y, 2));
        int maxWidth = (int) Math.max(widthBottom, widthTop);

        double heightLeft = Math.sqrt(Math.pow(cornerQuad.lb.x - cornerQuad.lt.x, 2) + Math.pow(cornerQuad.lb.y - cornerQuad.lt.y, 2));
        double heightRight = Math.sqrt(Math.pow(cornerQuad.rb.x - cornerQuad.rt.x, 2) + Math.pow(cornerQuad.rb.y - cornerQuad.rt.y, 2));
        int maxHeight = (int) Math.max(heightLeft, heightRight);

        Mat croppedPicture = new Mat(maxHeight, maxWidth, CvType.CV_8UC4);

        Mat srcMat = new Mat(4, 1, CvType.CV_32FC2);
        Mat dstMat = new Mat(4, 1, CvType.CV_32FC2);

        double scaleX = (double) sourceImage.cols() / (double) scanPreviewImageView.getWidth();
        double scaleY = (double) sourceImage.rows() / (double) scanPreviewImageView.getHeight();

        srcMat.put(0, 0, cornerQuad.lt.x * scaleX, cornerQuad.lt.y * scaleY,
                cornerQuad.rt.x * scaleX, cornerQuad.rt.y * scaleY,
                cornerQuad.lb.x * scaleX, cornerQuad.lb.y * scaleY,
                cornerQuad.rb.x * scaleX, cornerQuad.rb.y * scaleY);

        dstMat.put(0, 0, 0.0              , 0.0,
                (double) maxWidth, 0.0,
                0.0              , (double) maxHeight,
                (double) maxWidth, (double) maxHeight);

        Mat pt = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Imgproc.warpPerspective(sourceImage, croppedPicture, pt, croppedPicture.size());
        pt.release();
        srcMat.release();

        dstMat.release();

        return croppedPicture;
    }

    @Override
    public void paperRectangleIsAllowed(boolean allowed) {
        if (!allowed) Log.i(TAG, "No");
        nextButton.setEnabled(allowed);

        if (allowed) nextButton.setAlpha(1f);
        else nextButton.setAlpha(.5f);
    }
}

