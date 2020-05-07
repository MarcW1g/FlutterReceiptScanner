package com.example.doc_scan;

import com.example.doc_scan.model.Quad;

import org.opencv.core.Point;

import java.util.ArrayList;

interface ContourFunnelListener {
    void currentContourIs(Quad contour, FunnelResultAction action);
}

class ContourFunnel {
    // Save a contour with it's quad and matching score.
    public class ContourMatch {
        Quad contour;

        int matchingScore = 0;

        ContourMatch(Quad contour) {
            this.contour = contour;
        }

        boolean contourMatches(Quad otherContour, double threshold) {
            return contourIsWithin(this, otherContour, threshold);
        }
    }

    // The 'funnel' to which all contours are added to
    private ArrayList<ContourMatch> contours = new ArrayList<>();

    // The maximum number of contours to be compared to each other.
    // Warning: Increasing this value will impact performance
    private int maxNumberOfContours = 8;

    // The minimum number of contours needed to start comparing them to find the contour which
    // has to be displayed.
    // Warning: This number should always be below 'maxNumberOfContours'
    private int minNumberOfContours = 3;

    // The value in pixels used to determine if two contours match or not.
    // A higher value will prevent displayed contours to be refreshed.
    // On the opposite, a smaller value will make new contours be displayed constantly.
    private double matchingThreshold = 20.0;

    // The number of similar contours that need to be found to auto scan.
    private int autoScanThreshold = 30;

    // Determines the threshold to let a contour qualify as 'similar enough' to the current contour
    // Note: A higher value means the auto scan is quicker, but the contour will be less accurate.
    //       A lower value means the auto scan is slower, but it'll be way more accurate
    private double autoScanMatchingThreshold = 6.0;

    // The number of times the contours has passed the autoScanMatchingThreshold to be auto-scanned
    int currentAutoScanPassCount = 0;

    private ContourFunnelListener mListener;
    void registerContourFunnelListener(ContourFunnelListener mListener) {
        this.mListener = mListener;
    }

    // Add a new contour to the funnel
    // 1. The function checks if 'minNumberOfContours' and
    //    removes contours if 'maxNumberOfContours' is exceeded
    // 2. Update the matching score for all contours
    // 3. Get's the best contour
    // 4. Check auto-scan
    void add(Quad newContour, Quad currentDisplayedQuad) {
        ContourMatch contourMatch = new ContourMatch(newContour);
        contours.add(contourMatch);

        int contoursLength = contours.size();
        if (contoursLength < minNumberOfContours) return;
        if (contoursLength > maxNumberOfContours) contours.remove(0);

        this.updateContourMatches();

        ContourMatch bestMatch = this.getBestContour(currentDisplayedQuad);
        if (bestMatch != null && currentDisplayedQuad != null) {
            if (this.contourIsWithin(bestMatch, currentDisplayedQuad, autoScanMatchingThreshold)) {
                currentAutoScanPassCount++;
                if (currentAutoScanPassCount > autoScanThreshold) {
                    currentAutoScanPassCount = 0;
                    mListener.currentContourIs(bestMatch.contour, FunnelResultAction.SHOW_AND_AUTO_SCAN);
                }
                return;
            }
        }

        mListener.currentContourIs(bestMatch.contour, FunnelResultAction.SHOW);
    }

    // Resets the funnel
    void reset() {
        contours.clear();
        currentAutoScanPassCount = 0;
    }

    // Calculates the new contour matching scores for all contours in 'contours'. It counts
    // the times each contour matches with another contour.
    private void updateContourMatches() {
        if (contours.size() == 0) return;
        this.resetContourMatchingScores();

        int i, j;
        i = j = 0;

        for (ContourMatch currentMatch: contours) {
            for (ContourMatch rect: contours) {
                if (j > i && currentMatch.contourMatches(rect.contour, matchingThreshold)) {
                    currentMatch.matchingScore++;
                    rect.matchingScore++;
                }
                j++;
            }
            i++;
        }
    }

    // Resents all contour matching scores to 0
    private void resetContourMatchingScores() {
        if (contours.size() == 0) return;
        for (ContourMatch contour : contours) {
            contour.matchingScore = 1;
        }
    }

    // Returns the best contour in the contours array (funnel). It loops trough the contour array
    // in reversed order (from newest to oldest contour), and determines the best contour based
    // on the matching score.
    private ContourMatch getBestContour(Quad currentDisplayedQuad) {
        int contoursLength = contours.size();
        if (contoursLength == 0) return null;
        ContourMatch bestMatch = null;

        for (int i = contoursLength - 1; i >= 0; i--) {
            ContourMatch contour = contours.get(i);
            if (bestMatch == null) bestMatch = contour;

            if (contour.matchingScore > bestMatch.matchingScore) {
                bestMatch = contour;
            } else if (contour.matchingScore == bestMatch.matchingScore && currentDisplayedQuad != null) {
                bestMatch = this.breakTieBetweenMatches(bestMatch, contour, currentDisplayedQuad);
            }
        }

        return bestMatch;
    }

    // If the matching score of two contours is equal, this function is used to determine the best
    // one. 'Best' is determined based on the fact if a contour is within the current displayed
    // contour.
    private ContourMatch breakTieBetweenMatches(ContourMatch rect1, ContourMatch rect2, Quad currentDisplayedQuad) {
        if (this.contourIsWithin(rect1, currentDisplayedQuad, matchingThreshold)) {
            return rect1;
        } else if (this.contourIsWithin(rect2, currentDisplayedQuad, matchingThreshold)) {
            return rect2;
        }
        return rect1;
    }

    // Checks if the contour Quad is within the bounds of another contour Quad. The threshold
    // is used to reduce or increase the required similarity
    boolean contourIsWithin(ContourMatch baseContour, Quad currentDisplayedQuad, double matchingThreshold) {
        Quad baseQuad = baseContour.contour;

        double topLeftDistance = euclideanDistance(baseQuad.lt, currentDisplayedQuad.lt);
        if (topLeftDistance > matchingThreshold) return false;

        double topRightDistance = euclideanDistance(baseQuad.rt, currentDisplayedQuad.rt);
        if (topRightDistance > matchingThreshold) return false;

        double bottomLeftDistance = euclideanDistance(baseQuad.lb, currentDisplayedQuad.lb);
        if (bottomLeftDistance > matchingThreshold) return false;

        double bottomRightDistance = euclideanDistance(baseQuad.rb, currentDisplayedQuad.rb);
        if (bottomRightDistance > matchingThreshold) return false;

        return true;
    }

    // TODO: Generalise in math class
    private double euclideanDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }
}
