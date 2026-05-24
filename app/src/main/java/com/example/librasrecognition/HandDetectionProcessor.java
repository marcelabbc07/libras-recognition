package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class HandDetectionProcessor {

    private Hands hands;
    private OnHandDetectionListener listener;
    private GestureClassifier gestureClassifier;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnHandDetectionListener {
        void onHandDetected(HandsResult result);
        void onGesturePredicted(GestureClassifier.GesturePrediction pred);
    }

    public HandDetectionProcessor(Context context, OnHandDetectionListener listener) {
        this(context, listener, null);
    }

    public HandDetectionProcessor(Context context, OnHandDetectionListener listener, GestureClassifier gestureClassifier) {
        this.listener = listener;
        this.gestureClassifier = gestureClassifier;

        hands = new Hands(
                context,
                HandsOptions.builder()
                        .setStaticImageMode(false)
                        .setMaxNumHands(1)
                        .setMinDetectionConfidence(0.8f)
                        .setMinTrackingConfidence(0.8f)
                        .setRunOnGpu(true)
                        .build());

        hands.setResultListener(handsResult -> {
            Log.d("HandDetectionProcessor", "Nenhuma mão detectada");
                mainHandler.post(() -> {
                listener.onHandDetected(handsResult);
                if (handsResult.multiHandLandmarks().isEmpty()) {
                    listener.onGesturePredicted(null);
                }
            });
        });

        hands.setErrorListener((message, e) -> Log.e("HandDetectionProcessor", "Erro no MediaPipe Hands: " + message, e));
    }

    public void processImageFrame(ImageProxy image) {
        if (image == null) {
            return;
        }
        long timestamp = image.getImageInfo().getTimestamp();
        Log.d("HandDetectionProcessor", "processImageFrame START thread=" + Thread.currentThread().getName() + " ts=" + timestamp);

        if (hands != null) {
            Bitmap bitmap = imageProxyToBitmap(image);
            if (bitmap != null) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                bitmap = adjustBitmapOrientation(bitmap, rotationDegrees);
                if (gestureClassifier != null && gestureClassifier.isLoaded()) {
                    GestureClassifier.GesturePrediction pred =
                            gestureClassifier.predictWithScores(bitmap);
                    if (pred != null) {
                        Log.d("HandDetectionProcessor", "pred label=" + pred.label + " conf=" + pred.confidence);
                        mainHandler.post(() -> listener.onGesturePredicted(pred));
                    }
                }
                hands.send(bitmap, timestamp);
            } else {
                Log.e("HandDetectionProcessor", "Bitmap convertido está nulo.");
            }
            image.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        if (image.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
            byte[] jpegBytes = out.toByteArray();

            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } else {
            Log.e("HandDetectionProcessor", "Formato de imagem não suportado: " + image.getFormat());
            return null;
        }
    }

    private Bitmap adjustBitmapOrientation(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        matrix.postScale(-1, 1);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public void stop() {
        if (hands != null) {
            hands.close();
        }
    }
}