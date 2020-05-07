package com.example.doc_scan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.doc_scan.model.Quad;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class CameraView extends Activity implements CvCameraViewListener2, OnImageEventListener, ContourFunnelListener {
    private static final String TAG = "DocScanner::Activity";
    private CustomCameraView mOpenCvCameraView;

    private TextView cameraHintLIGHTextView;
    private TextView cameraHintCONTRASTextView;

    int camHeight, camWidth;
    Mat mRgba;
    Mat mGray;
    Mat mBilateral;
    Mat mCanny;
    Mat mDilated;
    Mat kernel;
    ContourFunnel contourFunnel;

    Quad bestContourToBeDisplayed;
    FunnelResultAction contourFunnelAction;

    int noRectangleCount = 0;
    int noRectangleCountThreshold = 3;
    int frameCount = 0;
    Boolean shouldAutoShutter = true;
    Boolean takingPicture = false;

    final Scalar normalContourColor = new Scalar(108, 170, 236);
    final Scalar autoScanContourColor = new Scalar(46, 204, 113);

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                mOpenCvCameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    public CameraView() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    ImageButton shutterButton;
    ImageButton flashButton;
    Button autoShutter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera_view);

        mOpenCvCameraView = findViewById(R.id.show_camera_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.registerOnImageEventListener(this);

        cameraHintLIGHTextView = findViewById(R.id.camera_hint_LIGHT_text_view);
        cameraHintCONTRASTextView = findViewById(R.id.camera_hint_CONTRAST_text_view);

        shutterButton = findViewById(R.id.shutter_button);
        shutterButton.setOnClickListener((View v) -> takeScanPicture());

        flashButton = findViewById(R.id.flash_button);
        flashButton.setOnClickListener((View v) -> {
            int newState = mOpenCvCameraView.circleFlashState();
            updateFlashIcon(newState);
        });

        autoShutter = findViewById(R.id.auto_shutter_button);
        autoShutter.setOnClickListener((View v) -> {
            if (shouldAutoShutter) {
                autoShutter.setText("Manual");
            } else {
                autoShutter.setText("Auto Scan");
                contourFunnel.currentAutoScanPassCount = 0;
            }
            shouldAutoShutter = !shouldAutoShutter;
        });

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener((View v) -> returnToFlutterWithoutResult());
    }

    private void updateFlashIcon(int newState) {
        switch (newState) {
            case 0:
                flashButton.setImageResource(R.drawable.flash_auto);
                break;
            case 1:
                flashButton.setImageResource(R.drawable.flash_on);
                break;
            case 2:
                flashButton.setImageResource(R.drawable.flash_off);
                break;
            default: break;
        }
    }

    private void takeScanPicture() {
        if (!takingPicture) {
            takingPicture = true;
            playShutterSound();
            mOpenCvCameraView.takePicture();
        }
    }

    private void playShutterSound() {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        switch( audio.getRingerMode() ){
            case AudioManager.RINGER_MODE_NORMAL:
                MediaActionSound sound = new MediaActionSound();
                sound.play(MediaActionSound.SHUTTER_CLICK);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                break;
        }
    }

    public void onCameraViewStarted(int width, int height) {
        camHeight = height; // 1080
        camWidth = width;   // 1440

        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC4);
        mBilateral = new Mat(height, width, CvType.CV_8UC3);
        mCanny = new Mat(height, width, CvType.CV_8UC4);
        mDilated = new Mat(height, width, CvType.CV_8UC4);
        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9.0, 9.0));
        contourFunnel = new ContourFunnel();
        contourFunnel.registerContourFunnelListener(this);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }


    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        frameCount++;
        mRgba = inputFrame.rgba();

        if (frameCount - 5 == 0) {
            new DarknessCheckTask().execute(mRgba);
            frameCount = 0;
        }

        List<MatOfPoint> foundContour = this.findBestContour(mRgba);

        if (foundContour != null) {
            Quad contourQuad = new Quad(foundContour);
            noRectangleCount = 0;
            contourFunnel.add(contourQuad, bestContourToBeDisplayed);
        } else {
            noRectangleCount++;
            if (noRectangleCount > noRectangleCountThreshold) {
                contourFunnel.currentAutoScanPassCount = 0;
                bestContourToBeDisplayed = null;
            }
        }

        if (bestContourToBeDisplayed != null) {
            Scalar contourColor = contourFunnelAction == FunnelResultAction.SHOW ? normalContourColor : autoScanContourColor;
            if (!shouldAutoShutter) contourColor = normalContourColor;
            Imgproc.drawContours(mRgba, bestContourToBeDisplayed.contourList, 0, contourColor, 6);

            if (contourFunnelAction == FunnelResultAction.SHOW_AND_AUTO_SCAN && shouldAutoShutter) {
                this.takeScanPicture();
            }
        }

        return mRgba;
    }

    @Override
    public void currentContourIs(Quad contour, FunnelResultAction action) {
        bestContourToBeDisplayed = contour;
        contourFunnelAction = action;
    }

    List<MatOfPoint> findBestContour(Mat mRgbaImg) {
        Imgproc.cvtColor(mRgbaImg, mGray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(mGray, mGray, new Size(3.0, 3.0), 0.0);
        Imgproc.dilate(mGray, mDilated, kernel);
        Imgproc.Canny(mDilated, mCanny, 25, 200, 3, true);
//        Imgproc.dilate(mCanny, mCanny, kernel);

        Mat lines = new Mat();
        Imgproc.HoughLinesP(mCanny, lines, 1, 3.14/180, 25, 150, 10);//, 10, 250);
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            if (line != null) {
                Imgproc.line(mCanny, new Point(line[0], line[1]), new Point(line[2], line[3]), new Scalar(255,0,0), 4);
            }
        }

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mCanny, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_KCOS);

        Collections.sort(contours, (p1, p2) -> Double.compare(Imgproc.contourArea(p2), Imgproc.contourArea(p1)));

        // Select the best contour and return it
        MatOfPoint2f bestContour = null;
        for (MatOfPoint contour : contours) {
            MatOfPoint2f contour2f = new MatOfPoint2f( contour.toArray() );
//            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();

//            0.1 * peri
            Imgproc.approxPolyDP(contour2f, approx, 20, true);

            if (approx.toArray().length == 4 && Imgproc.contourArea(contour) > 100_000) {
                bestContour = approx;
                break;
            }
        }

        // Create a drawable contour
        if (bestContour != null ) {
            MatOfPoint approxf1 = new MatOfPoint();
            bestContour.convertTo(approxf1, CvType.CV_32S);
            List<MatOfPoint> contourTemp = new ArrayList<>();
            contourTemp.add(approxf1);

            return contourTemp;
        } else {
            return null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

            mOpenCvCameraView.resetFlash();
            updateFlashIcon(mOpenCvCameraView.currentFlashState());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void imageTaken(Mat image) {
        takingPicture = false;
        CropActivity.contour = this.bestContourToBeDisplayed;
        CropActivity.image = image;

        // Width = Height & Height = Width as the image in given rotated by OpenCV. Here we do the
        // rotation task.
        CropActivity.openCVCamWidth = this.camHeight;
        CropActivity.openCVCamHeight = this.camWidth;

        // Start an intent to the CropActivity
        Intent intent = new Intent(getBaseContext(), CropActivity.class);
        startActivity(intent);

        contourFunnel.reset();
        bestContourToBeDisplayed = null;
    }


    private void returnToFlutterWithoutResult() {
        Intent intent = new Intent("text-processing-finished");
        intent.putExtra("message", "scanCancelled");
        LocalBroadcastManager.getInstance(CameraView.this).sendBroadcast(intent);
        Intent i = new Intent(CameraView.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    // An async task to analyse the image on darkness. This is done by checking the grayscaled image
    // on the mean value
    @SuppressLint("StaticFieldLeak")
    private class DarknessCheckTask extends AsyncTask<Mat, Void, Dictionary<String, Boolean>> {
        // Threshold for when to classify an image feed as "Dark"
        double darknessThreshold = 70;
        double contrastThreshold = 40;

        String lightKey = "light";
        String contrastKey = "contrast";

        @Override
        protected Dictionary<String, Boolean> doInBackground(Mat... mats) {
            // Make sure only 1 mat is provided
            if (mats.length > 1) return null;
            Mat mainMat = mats[0];
            Mat grayFrame = new Mat(mainMat.size(), CvType.CV_8UC4);

            Imgproc.cvtColor(mainMat, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(grayFrame, grayFrame, new Size(5,5), 0.0);

            MatOfDouble mean = new MatOfDouble();
            MatOfDouble std = new MatOfDouble();
            Core.meanStdDev(grayFrame, mean, std);

            double meanValue = mean.get(0,0)[0];
            double stdValue = std.get(0,0)[0];

            // True if too dark
            Dictionary results = new Hashtable<String, Boolean>();
            results.put(lightKey, meanValue < darknessThreshold);
            results.put(contrastKey, stdValue < contrastThreshold);
            return results;
        }

        @Override
        protected void onPostExecute(Dictionary<String, Boolean> result) {
            if (result != null) {
                if (result.get(lightKey)) {
                    cameraHintLIGHTextView.setVisibility(View.VISIBLE);
                } else {
                    cameraHintLIGHTextView.setVisibility(View.GONE);
                }

                if (result.get(contrastKey)) {
                    cameraHintCONTRASTextView.setVisibility(View.VISIBLE);
                } else {
                    cameraHintCONTRASTextView.setVisibility(View.GONE);
                }
            }
        }
    }

//    @SuppressLint("StaticFieldLeak")
//    private class FindContourAsync extends AsyncTask<Mat, Void, List<MatOfPoint>> {
//        // Threshold for when to classify an image feed as "Dark"
//        double darknessThreshold = 70
//
//        @Override
//        protected List<MatOfPoint> doInBackground(Mat... mats) {
//            // Make sure only 1 mat is provided
//            if (mats.length > 1) return null;
//            Mat mainMat = mats[0];
//
//            Size matSize = mainMat.size();
//
//
//            return this.findBestContour(mainMat);
//        }
//
//        @Override
//        protected void onPostExecute(List<MatOfPoint> foundContour) {
//            if (foundContour != null) {
//                Quad contourQuad = new Quad(foundContour);
//                noRectangleCount = 0;
//                contourFunnel.add(contourQuad, bestContourToBeDisplayed);
//                Log.i(TAG, "Add contour to the funnel");
//            } else {
//                noRectangleCount++;
//                if (noRectangleCount > noRectangleCountThreshold) {
//                    contourFunnel.currentAutoScanPassCount = 0;
//                    bestContourToBeDisplayed = null;
//                }
//            }
//        }
//
//        List<MatOfPoint> findBestContour(Mat mRgbaImg) {
//            Imgproc.cvtColor(mRgbaImg, mGray, Imgproc.COLOR_RGBA2GRAY);
//            Imgproc.GaussianBlur(mGray, mGray, new Size(3.0, 3.0), 0.0);
//            Imgproc.dilate(mGray, mDilated, kernel);
//            Imgproc.Canny(mDilated, mCanny, 25, 200, 3, true);
//
//            Mat lines = new Mat();
//            Imgproc.HoughLinesP(mCanny, lines, 1, 3.14/180, 25, 150, 10);//, 10, 250);
//            for (int i = 0; i < lines.rows(); i++) {
//                double[] line = lines.get(i, 0);
//                if (line != null) {
//                    Imgproc.line(mCanny, new Point(line[0], line[1]), new Point(line[2], line[3]), new Scalar(255,0,0), 4);
//                }
//            }
//
//            List<MatOfPoint> contours = new ArrayList<>();
//            Mat hierarchy = new Mat();
//            Imgproc.findContours(mCanny, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_KCOS);
//
//            Collections.sort(contours, (p1, p2) -> Double.compare(Imgproc.contourArea(p2), Imgproc.contourArea(p1)));
//
//            // Select the best contour and return it
//            MatOfPoint2f bestContour = null;
//            for (MatOfPoint contour : contours) {
//                MatOfPoint2f contour2f = new MatOfPoint2f( contour.toArray() );
////            double peri = Imgproc.arcLength(contour2f, true);
//                MatOfPoint2f approx = new MatOfPoint2f();
//
////            0.1 * peri
//                Imgproc.approxPolyDP(contour2f, approx, 20, true);
//
//                if (approx.toArray().length == 4 && Imgproc.contourArea(contour) > 100_000) {
//                    bestContour = approx;
//                    break;
//                }
//            }
//
//            // Create a drawable contour
//            if (bestContour != null ) {
//                MatOfPoint approxf1 = new MatOfPoint();
//                bestContour.convertTo(approxf1, CvType.CV_32S);
//                List<MatOfPoint> contourTemp = new ArrayList<>();
//                contourTemp.add(approxf1);
//
//                return contourTemp;
//            } else {
//                return null;
//            }
//        }
//    }
}