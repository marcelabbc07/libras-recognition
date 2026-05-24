package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.google.mediapipe.formats.proto.LandmarkProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HandOverlayView extends View {

    private static final int[][] HAND_CONNECTIONS = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {0, 9}, {9, 10}, {10, 11}, {11, 12},
            {0, 13}, {13, 14}, {14, 15}, {15, 16},
            {0, 17}, {17, 18}, {18, 19}, {19, 20},
            {5, 9}, {9, 13}, {13, 17}
    };

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<LandmarkProto.NormalizedLandmark> landmarks = new ArrayList<>();

    private final float lineWidthPx;
    private final float jointRadiusPx;
    private final float jointRingWidthPx;
    private boolean invertLandmarkX = true;
    private boolean mirrorCanvasForFront;

    public HandOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        lineWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.5f,
                context.getResources().getDisplayMetrics());
        jointRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f,
                context.getResources().getDisplayMetrics());
        jointRingWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                context.getResources().getDisplayMetrics());

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(Color.argb(230, 255, 255, 255));

        jointFillPaint.setStyle(Paint.Style.FILL);
        jointFillPaint.setColor(Color.BLACK);

        jointRingPaint.setStyle(Paint.Style.STROKE);
        jointRingPaint.setColor(Color.argb(200, 255, 255, 255));
    }

    public void setLandmarks(List<LandmarkProto.NormalizedLandmark> landmarks) {
        this.landmarks = landmarks != null ? landmarks : Collections.emptyList();
        invalidate();
    }

    public void setCameraFacing(boolean isFrontCamera) {
        mirrorCanvasForFront = isFrontCamera;
        invertLandmarkX = true;
        invalidate();
    }

    private float nx(LandmarkProto.NormalizedLandmark lm, int viewW) {
        float x = lm.getX();
        return (invertLandmarkX ? (1f - x) : x) * viewW;
    }

    private static float ny(LandmarkProto.NormalizedLandmark lm, int viewH) {
        return lm.getY() * viewH;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (landmarks.isEmpty()) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        canvas.save();
        if (mirrorCanvasForFront) {
            canvas.scale(-1f, 1f, w * 0.5f, h * 0.5f);
        }

        linePaint.setStrokeWidth(Math.max(2f, lineWidthPx * Math.min(w, h) / 480f));

        for (int[] conn : HAND_CONNECTIONS) {
            if (conn[0] >= landmarks.size() || conn[1] >= landmarks.size()) {
                continue;
            }
            LandmarkProto.NormalizedLandmark a = landmarks.get(conn[0]);
            LandmarkProto.NormalizedLandmark b = landmarks.get(conn[1]);
            canvas.drawLine(nx(a, w), ny(a, h), nx(b, w), ny(b, h), linePaint);
        }

        float r = Math.max(jointRadiusPx * 0.85f, Math.min(w, h) * 0.014f);
        float ring = Math.max(1f, jointRingWidthPx);
        jointRingPaint.setStrokeWidth(ring);

        for (LandmarkProto.NormalizedLandmark landmark : landmarks) {
            float x = nx(landmark, w);
            float y = ny(landmark, h);
            canvas.drawCircle(x, y, r + ring * 0.5f, jointRingPaint);
            canvas.drawCircle(x, y, r, jointFillPaint);
        }

        canvas.restore();
    }
}