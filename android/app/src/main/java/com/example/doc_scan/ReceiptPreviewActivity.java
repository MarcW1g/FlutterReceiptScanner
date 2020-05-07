package com.example.doc_scan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.doc_scan.model.ScanResult;
import com.example.doc_scan.model.VisionResult;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.gson.Gson;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public class ReceiptPreviewActivity extends AppCompatActivity  {
    private static final String TAG = "DocScanner::Preview";

    public static Mat croppedGrayImage;
    public static Mat croppedThresholdedImage;
    public static Boolean shouldShowThresholded;
    public static Mat originalImage;

    ImageButton submitReceiptImage;
    ImageButton enableDisableFilterButton;
    ImageButton prevButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_preview_activity);

        // Get the views
        PhotoView croppedImageView = findViewById(R.id.cropped_preview_image);

        submitReceiptImage = findViewById(R.id.submit_receipt_button);
        enableDisableFilterButton = findViewById(R.id.enable_disable_filter_button);
        prevButton = findViewById(R.id.back_to_crop_button);
        ProgressBar progressBar = findViewById(R.id.scan_processing_progress_bar);
        progressBar.setVisibility(View.GONE);

        // Present the cropped receipt image
        this.setPreviewImage(croppedImageView, enableDisableFilterButton);

        submitReceiptImage.setOnClickListener((View v) -> {
            progressBar.setVisibility(View.VISIBLE);

            Mat selectedImage = shouldShowThresholded ? croppedThresholdedImage : croppedGrayImage;

            Bitmap croppedBitmap = matToBitmap(selectedImage);
            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(croppedBitmap);
            FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
            detector.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText result) {
                        // Task completed successfully
                        VisionResult visionResults = processVisionResults(result, croppedBitmap);

                        Bitmap originalBitmap = matToBitmap(originalImage);
                        String jsonString = getJsonResults(originalBitmap, croppedBitmap, visionResults);
                        returnToFlutterWithResultString(jsonString);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        returnToFlutterWithResultString("scanFailed");
                    }
                });
        });

        enableDisableFilterButton.setOnClickListener((View view) -> {
            shouldShowThresholded = !shouldShowThresholded;
            this.setPreviewImage(croppedImageView, enableDisableFilterButton);
        });

        prevButton.setOnClickListener((View view) -> {
            finish();
        });
    }

    private void setPreviewImage(PhotoView imageView, ImageButton filterButton) {
        if (imageView == null || filterButton == null) return;

        if (shouldShowThresholded) {
            if (croppedThresholdedImage != null) {
                presentImageMat(croppedThresholdedImage, imageView);
                filterButton.setImageResource(R.drawable.no_filter);
            }
        } else {
            if (croppedGrayImage != null) {
                presentImageMat(croppedGrayImage, imageView);
                filterButton.setImageResource(R.drawable.photo_filter);
            }
        }
    }

    private void returnToFlutterWithResultString(String results) {
        Intent intent = new Intent("text-processing-finished");
        intent.putExtra("message", results);
        LocalBroadcastManager.getInstance(ReceiptPreviewActivity.this).sendBroadcast(intent);

        Intent i = new Intent(ReceiptPreviewActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void presentImageMat(Mat imageMat, PhotoView imageView) {
        // Present the cropped receipt image
        Bitmap croppedBitmap = matToBitmap(imageMat);
        imageView.setImageBitmap(croppedBitmap);
    }

    private Bitmap matToBitmap(Mat imageMat) {
        Bitmap croppedBitmap = Bitmap.createBitmap(imageMat.cols(), imageMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(imageMat, croppedBitmap);
        return croppedBitmap;
    }


    private String getJsonResults(Bitmap originalBitmap, Bitmap croppedBitmap, VisionResult visionResults) {
        String croppedImageBase64 = encodeBitmapBase64(croppedBitmap);
        String originalImageBase64 = encodeBitmapBase64(originalBitmap);

        Gson gson = new Gson();
        ScanResult scanResult = new ScanResult(visionResults, originalImageBase64, croppedImageBase64);
        return gson.toJson(scanResult);
    }

    private String encodeBitmapBase64(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] byteArrayImage = baos.toByteArray();
        return android.util.Base64.encodeToString(byteArrayImage, Base64.NO_WRAP);
    }

    private VisionResult processVisionResults(FirebaseVisionText results, Bitmap sourceImage) {
        ArrayList<VisionResult.TextObservation> textObservations = new ArrayList<VisionResult.TextObservation>();
        double imageWidth = sourceImage.getWidth();
        double imageHeight = sourceImage.getHeight();


        for (FirebaseVisionText.TextBlock block : results.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                Rect boundingBox = line.getBoundingBox();
                String text = line.getText();

//                Log.i(TAG, line.getCornerPoints());


                VisionResult.Rect normalizedBoundingBox = normalizeRect(boundingBox, imageWidth, imageHeight);
                VisionResult.TextObservation obs = new VisionResult.TextObservation(text, -1.0, normalizedBoundingBox);
                textObservations.add(obs);
            }
        }

        return new VisionResult(imageWidth, imageHeight, textObservations);
    }

    private VisionResult.Rect normalizeRect(Rect sourceRect, double spaceWidth, double spaceHeight) {
        double newLeft = ((double) sourceRect.left / spaceWidth);
        double newTop = ((double) sourceRect.top / spaceHeight);
        double newRight = ((double) sourceRect.right / spaceWidth);
        double newBottom = ((double) sourceRect.bottom / spaceHeight);
        return new VisionResult.Rect(newLeft, newRight, newTop, newBottom);
    }
}
