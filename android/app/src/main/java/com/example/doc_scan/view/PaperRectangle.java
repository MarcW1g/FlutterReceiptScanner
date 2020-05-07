package com.example.doc_scan.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.example.doc_scan.CheckLineIntersect;
import com.example.doc_scan.model.Corner;
import com.example.doc_scan.model.Quad;

import org.opencv.core.Point;

import java.util.Arrays;
import java.util.List;

public class PaperRectangle extends View {
    // Properties
    private Paint contourPaint = new Paint();
    private Path contourPath = new Path();

    private int viewWidth, viewHeight;

    private Quad currentCorners;

    float touchXPos = 0;
    float touchYPos = 0;
    float xTouchToPointOffset = 0;
    float yTouchToPointOffset = 0;
    Corner cornerToMove;
    Boolean currentRectIsAllowed = true;

    private PaperRectangleCallback mListener;
    public void registerPaperRectangleCallback(PaperRectangleCallback mListener) {
        this.mListener = mListener;
    }

    // Constructors
    public PaperRectangle(Context context) {
        super(context);
        this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        init(context);
    }
    public PaperRectangle(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        init(context);
    }

    public PaperRectangle(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        init(context);
    }

    private void init(Context context) {
        contourPaint.setColor(Color.WHITE);
        contourPaint.setAntiAlias(true);
        contourPaint.setDither(true);
        contourPaint.setStrokeWidth(10);
        contourPaint.setStyle(Paint.Style.STROKE);
        contourPaint.setStrokeJoin(Paint.Join.ROUND);
        contourPaint.setStrokeCap(Paint.Cap.ROUND);
        contourPaint.setPathEffect(new CornerPathEffect(10));
    }

    public void previewCorners(Quad quad) {
        currentCorners = quad;

        contourPath.reset();
        contourPath.moveTo((float)quad.lt.x, (float)quad.lt.y);
        contourPath.lineTo((float)quad.rt.x, (float)quad.rt.y);
        contourPath.lineTo((float)quad.rb.x, (float)quad.rb.y);
        contourPath.lineTo((float)quad.lb.x, (float)quad.lb.y);
        contourPath.close();
        this.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        this.viewWidth = this.getWidth();
        this.viewHeight = this.getHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.argb(80, 0, 0, 0));
        overlayPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, this.getWidth(), this.getMeasuredHeight(), overlayPaint);

        overlayPaint.setAlpha(0xFF);
        overlayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        canvas.drawPath(contourPath, overlayPaint);
        canvas.drawPath(contourPath, contourPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchXPos = event.getX();
                touchYPos = event.getY();
                cornerToMove = getCornerToMove(touchXPos, touchYPos);
                Log.i("XXX", "Moved: "+ cornerToMove);
                break;
            case MotionEvent.ACTION_MOVE:
                touchXPos = event.getX() + xTouchToPointOffset;
                touchYPos = event.getY() + yTouchToPointOffset;

                // Make sure the points do not go out of bounds
                touchXPos = Math.min(this.viewWidth, Math.max(touchXPos, 0));
                touchYPos = Math.min(this.viewHeight, Math.max(touchYPos, 0));

                // Assume LB is moved
                if (!linesIntersects(currentCorners, cornerToMove, new Point(touchXPos, touchYPos))) {
                    currentCorners.setCornerXY(cornerToMove, touchXPos, touchYPos);
                    movePoints();
                }

                currentRectIsAllowed = quadAnglesAreAllowed();
                mListener.paperRectangleIsAllowed(currentRectIsAllowed);

                break;
            case MotionEvent.ACTION_UP:
                cornerToMove = null;
                if (currentRectIsAllowed) this.currentCorners.resetCorrectPointPerCorner();
                break;
        }
        return true;
    }

    private boolean quadAnglesAreAllowed() {
        double ltA = angleOfPoint(currentCorners.lt, currentCorners.lb,  currentCorners.rt);
        double rtA = angleOfPoint(currentCorners.rt, currentCorners.lt,  currentCorners.rb);
        double lbA = angleOfPoint(currentCorners.lb, currentCorners.lt,  currentCorners.rb);
        double rbA = angleOfPoint(currentCorners.rb, currentCorners.lb,  currentCorners.rt);

        if (ltA <= 40 || ltA >= 180) return false;
        if (rtA <= 40 || rtA >= 180) return false;
        if (lbA <= 40 || lbA >= 180) return false;
        if (rbA <= 40 || rbA >= 180) return false;
        return true;
    }

    private double angleOfPoint(Point origin, Point p1, Point p2) {
        double a = euclideanDistance(origin, p1);
        double b = euclideanDistance(origin, p2);
        double c = euclideanDistance(p1, p2);
        double angle = Math.pow(c, 2) - (Math.pow(a, 2) + Math.pow(b, 2));
        angle = angle / (-(2 * a * b));
        angle = Math.acos(angle) * 180 / Math.PI;
        return angle;
    }

    private void movePoints() {
        contourPath.reset();
        contourPath.moveTo((float)currentCorners.lt.x, (float)currentCorners.lt.y);
        contourPath.lineTo((float)currentCorners.rt.x, (float)currentCorners.rt.y);
        contourPath.lineTo((float)currentCorners.rb.x, (float)currentCorners.rb.y);
        contourPath.lineTo((float)currentCorners.lb.x, (float)currentCorners.lb.y);
        contourPath.close();
        this.invalidate();
    }


    private Corner getCornerToMove(float xPos, float yPos) {
        double minDistance = Double.MAX_VALUE;
        Corner minDistanceCorner = Corner.LEFT_BOTTOM;

        Point currentCheckingPoint;
        List<Corner> corners = Arrays.asList(Corner.LEFT_TOP, Corner.RIGHT_TOP, Corner.LEFT_BOTTOM, Corner.RIGHT_BOTTOM);
        Point touchPoint = new Point(xPos, yPos);

        double dist = 0.0;
        for (Corner corner : corners) {
            currentCheckingPoint = currentCorners.getCorner(corner);
            dist = euclideanDistance(touchPoint, currentCheckingPoint);
            if (dist < minDistance) {
                minDistance = dist;
                minDistanceCorner = corner;

                xTouchToPointOffset = (float) currentCheckingPoint.x - xPos;
                yTouchToPointOffset = (float) currentCheckingPoint.y - yPos;
            }
        }

        return minDistanceCorner;
    }

    // Checks if none of the lines of the rectangle intersect
    private boolean linesIntersects(Quad corners, Corner movedCorner, Point movedCornerNewPosition) {
        boolean horizontal, vertical;

        boolean exceedsOppositeCorner = false;
        Point oppositePointFromMoving;

        switch (movedCorner) {
            case LEFT_TOP:
                horizontal = CheckLineIntersect.doIntersect(currentCorners.lb, movedCornerNewPosition,
                        currentCorners.rb, currentCorners.rt);
                vertical = CheckLineIntersect.doIntersect(currentCorners.lb, currentCorners.rb,
                        movedCornerNewPosition, currentCorners.rt);

                oppositePointFromMoving = currentCorners.rb;
                exceedsOppositeCorner = (movedCornerNewPosition.x >= oppositePointFromMoving.x)
                        && (movedCornerNewPosition.y >= oppositePointFromMoving.y);
                break;
            case RIGHT_TOP:
                horizontal = CheckLineIntersect.doIntersect(currentCorners.lb, currentCorners.lt,
                        currentCorners.rb, movedCornerNewPosition);
                vertical = CheckLineIntersect.doIntersect(currentCorners.lb, currentCorners.rb,
                        currentCorners.lt, movedCornerNewPosition);

                oppositePointFromMoving = currentCorners.lb;
                exceedsOppositeCorner = (movedCornerNewPosition.x <= oppositePointFromMoving.x)
                        && (movedCornerNewPosition.y >= oppositePointFromMoving.y);
                break;
            case LEFT_BOTTOM:
                horizontal = CheckLineIntersect.doIntersect(movedCornerNewPosition, currentCorners.lt,
                        currentCorners.rb, currentCorners.rt);
                vertical = CheckLineIntersect.doIntersect(movedCornerNewPosition, currentCorners.rb,
                        currentCorners.lt, currentCorners.rt);

                oppositePointFromMoving = currentCorners.rt;
                exceedsOppositeCorner = (movedCornerNewPosition.x >= oppositePointFromMoving.x)
                        && (movedCornerNewPosition.y <= oppositePointFromMoving.y);
                break;
            case RIGHT_BOTTOM:
                horizontal = CheckLineIntersect.doIntersect(currentCorners.lb, currentCorners.lt,
                        movedCornerNewPosition, currentCorners.rt);
                vertical = CheckLineIntersect.doIntersect(currentCorners.lb, movedCornerNewPosition,
                        currentCorners.lt, currentCorners.rt);

                oppositePointFromMoving = currentCorners.lt;
                exceedsOppositeCorner = (movedCornerNewPosition.x <= oppositePointFromMoving.x)
                        && (movedCornerNewPosition.y <= oppositePointFromMoving.y);
                break;
            default:
                horizontal = vertical = false;
        }

        return horizontal || vertical || exceedsOppositeCorner;
    }

    private double euclideanDistance(Point p1, Point p2) {
        return Math.abs(Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2)));
    }
}
