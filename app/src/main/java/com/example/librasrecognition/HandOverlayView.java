package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.formats.proto.LandmarkProto;

import java.util.ArrayList;
import java.util.List;

public class HandOverlayView extends View {

    private final Paint paint = new Paint();
    private List<LandmarkProto.NormalizedLandmark> landmarks = new ArrayList<>();

    public HandOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStrokeWidth(10f);
    }

    public void setLandmarks(List<LandmarkProto.NormalizedLandmark> landmarks) {
        this.landmarks = landmarks;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int i = 0; i < landmarks.size(); i++) {
            LandmarkProto.NormalizedLandmark landmark = landmarks.get(i);
            float x = (1 - landmark.getX()) * getWidth();
            float y = landmark.getY() * getHeight();

            if (i == 0) {
                paint.setColor(Color.WHITE); // Palma
            } else if (i >= 1 && i <= 4) {
                paint.setColor(Color.RED); // Polegar
            } else if (i >= 5 && i <= 8) {
                paint.setColor(Color.GREEN); // Indicador
            } else if (i >= 9 && i <= 12) {
                paint.setColor(Color.BLUE); // Médio
            } else if (i >= 13 && i <= 16) {
                paint.setColor(Color.YELLOW); // Anelar
            } else if (i >= 17 && i <= 20) {
                paint.setColor(Color.MAGENTA); // Dedinho
            }
            canvas.drawCircle(x, y, 8, paint);
        }
    }
}