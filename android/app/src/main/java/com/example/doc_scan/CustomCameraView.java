package com.example.doc_scan;

//import android.graphics.Camera;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCameraView;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.List;

interface OnImageEventListener {
    void imageTaken(Mat image);
}


public class CustomCameraView extends JavaCameraView implements PictureCallback {

    private int flashState = 0;

    public OnImageEventListener mPictureListener;

    public void registerOnImageEventListener(OnImageEventListener mPictureListener) {
        this.mPictureListener = mPictureListener;
    }

    public CustomCameraView(Context context, AttributeSet attrs) { super(context, attrs); }

    public int circleFlashState() {
        if (flashState < 2) {
            flashState++;
        } else {
            flashState = 0;
        }

        setFlashToState(flashState);

        return flashState;
    }

    private void setFlashToState(int state) {
        if (mCamera == null) return;

        Camera.Parameters params = mCamera.getParameters();
        List<String> supportedFlashModes = params.getSupportedFlashModes();
        switch (state) {
            case 0:
                params.setFlashMode(params.FLASH_MODE_AUTO);
                break;
            case 1:
                if(supportedFlashModes.contains(params.FLASH_MODE_TORCH)) {
                    params.setFlashMode(params.FLASH_MODE_TORCH);
                } else if(supportedFlashModes.contains(params.FLASH_MODE_ON)) {
                    params.setFlashMode(params.FLASH_MODE_ON);
                }
                break;
            case 2:
                params.setFlashMode(params.FLASH_MODE_OFF);
                break;
            default: break;
        };
        mCamera.setParameters(params);
    }

    public void takePicture() {
        if (mCamera == null) return;
        mCamera.setPreviewCallback(null);
        mCamera.takePicture(null, null, this);
    }

    public void resetFlash() {
        flashState = 0;
    }

    public int currentFlashState() {
        return flashState;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Camera.Size pictureSize = camera.getParameters().getPictureSize();

        Log.i("XXX", "W: " + pictureSize.width + " - H: " + pictureSize.height);

        Mat mat = new Mat(pictureSize.height, pictureSize.width, CvType.CV_8U);
        mat.put(0,0, data);

        Mat pic = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);

        mat.release();

        Imgproc.cvtColor(pic, pic, Imgproc.COLOR_RGB2BGRA);

        mPictureListener.imageTaken(pic);
    }
}
