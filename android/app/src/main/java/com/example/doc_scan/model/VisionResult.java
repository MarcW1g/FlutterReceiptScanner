package com.example.doc_scan.model;

import java.util.ArrayList;

public class VisionResult {
    public Size sourceImageSize;
    public ArrayList<TextObservation> textObservations;

    public VisionResult(double imageWidth, double imageHeight, ArrayList<TextObservation> textObservations) {
        this.sourceImageSize = new Size(imageWidth, imageHeight);
        this.textObservations = textObservations;
    }

    // Required child classes
    public static class TextObservation {
        public String text;
        public double confidence;
        public Rect normalizedRect;

        public TextObservation(String text, double confidence, Rect normalizedRect) {
            this.text = text;
            this.confidence = confidence;
            this.normalizedRect = normalizedRect;
        }
    }

    public static class Rect {
        public double xPos;
        public double yPos;
        public Size size;

        public Rect(double left, double right, double top, double bottom) {
            double width = right - left;
            double height = bottom - top;
            Size size = new Size(width, height);

            this.xPos = left;
            this.yPos = top;
            this.size = size;
        }
    }

    public static class Size {
        public double width;
        public double height;

        public Size(double width, double height) {
            this.width = width;
            this.height = height;
        }
    }
}
