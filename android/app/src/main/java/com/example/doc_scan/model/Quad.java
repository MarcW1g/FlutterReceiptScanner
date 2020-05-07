package com.example.doc_scan.model;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class Quad {
    private static final String TAG = "DocScanner::Quad";

    public List<MatOfPoint> contourList;
    public Point lt; // Left-top
    public Point rt; // Right-top
    public Point lb; // Left-bottom
    public Point rb; // Right-bottom

    public Quad(List<MatOfPoint> contour) {
        if (contour.size() > 0) {
            Point[] cornerPointsArray = contour.get(0).toArray();
            List<Point> cornerPoints = Arrays.asList(cornerPointsArray);

            this.randomlyAssignPointsToCorners(cornerPoints);
            this.resetCorrectPointPerCorner();
            contourList = contour;
        }
    }

    private void randomlyAssignPointsToCorners(List<Point> cornerPoints) {
        lt = cornerPoints.get(0);
        rt = cornerPoints.get(1);
        lb = cornerPoints.get(2);
        rb = cornerPoints.get(3);
    }

    public void resetCorrectPointPerCorner() {
        List<Point> cornerPoints = Arrays.asList(lt, rt, lb, rb);

        // Sort array based on X
        Collections.sort(cornerPoints, (p1, p2) -> Double.compare(p1.x, p2.x));

        List<Point> leftMost = cornerPoints.subList(0, 2);
        List<Point> rightMost = cornerPoints.subList(2, 4);

        Collections.sort(leftMost, (p1, p2) -> Double.compare(p1.y, p2.y));
        lt = leftMost.get(0);
        lb = leftMost.get(1);

        // May be incorrect
        Collections.sort(rightMost, (p1, p2) -> Double.compare(p1.y, p2.y));
        rt = rightMost.get(0);
        rb = rightMost.get(1);

//        Collections.sort(cornerPoints, (p1, p2) -> Double.compare(p1.x + p1.y, p2.x + p2.y));
//        lt = cornerPoints.get(0);
//        rb = cornerPoints.get(3);
//        Collections.sort(cornerPoints, (p1, p2) -> Double.compare(p1.x - p1.y, p2.x - p2.y));
//        rt = cornerPoints.get(3);
//        lb = cornerPoints.get(0);
    }

    public void rotateCornerPoints(Point centerPoint) {
        lt = rotatePointAround(lt, centerPoint, 90);
        rt = rotatePointAround(rt, centerPoint, 90);
        lb = rotatePointAround(lb, centerPoint, 90);
        rb = rotatePointAround(rb, centerPoint, 90);
    }

    public void moveCornerPoints(int transformX, int transformY) {
        lt = transformPointY(lt, transformX, transformY);
        rt = transformPointY(rt, transformX, transformY);
        lb = transformPointY(lb, transformX, transformY);
        rb = transformPointY(rb, transformX, transformY);
    }

    public void scaleCornerPoints(double scaleFactor, Point centerPoint) {
        lt = scalePoint(lt, centerPoint, scaleFactor);
        rt = scalePoint(rt, centerPoint, scaleFactor);
        lb = scalePoint(lb, centerPoint, scaleFactor);
        rb = scalePoint(rb, centerPoint, scaleFactor);
    }

    public Point rotatePointAround(Point point, Point center, double angle_deg) {
        double angle = angle_deg * (Math.PI / 180);
        double rotatedX = (Math.cos(angle) * (point.x - center.x)) - (Math.sin(angle) * (point.y-center.y)) + center.x;
        double rotatedY = (Math.sin(angle) * (point.x - center.x)) + (Math.cos(angle) * (point.y - center.y)) + center.y;
        return new Point(rotatedX, rotatedY);
    }

    Point scalePoint(Point point, Point center, double scale) {
        double scaledX = ((point.x - center.x) * scale) + center.x;
        double scaledY = ((point.y - center.y) * scale) + center.y;
        return new Point(scaledX, scaledY);
    }

    Point transformPointY(Point point, int transformX, int transformY) {
        return new Point(point.x + transformX, point.y + transformY);
    }

    public Point getCorner(Corner corner) {
        switch (corner) {
            case LEFT_TOP:
                return lt;
            case RIGHT_TOP:
                return rt;
            case LEFT_BOTTOM:
                return lb;
            case RIGHT_BOTTOM:
                return rb;
        }
        return new Point(0, 0);
    }

    public void setCornerXY(Corner corner, double x, double y) {
        switch (corner) {
            case LEFT_TOP:
                lt.x = x;
                lt.y = y;
                break;
            case RIGHT_TOP:
                rt.x = x;
                rt.y = y;
                break;
            case LEFT_BOTTOM:
                lb.x = x;
                lb.y = y;
                break;
            case RIGHT_BOTTOM:
                rb.x = x;
                rb.y = y;
                break;
        }
    }

}
