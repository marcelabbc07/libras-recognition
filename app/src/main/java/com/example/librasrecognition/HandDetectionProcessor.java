package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class HandDetectionProcessor {

    private static final int CLASSIFY_EVERY_N_FRAMES = 2;
    private static final float MIN_CONFIDENCE = 0.45f;
    private static final float MIN_MARGIN = 0.08f;
    private static final int STREAK_TO_CONFIRM = 6;

    private Hands hands;
    private OnHandDetectionListener listener;
    private GestureClassifier gestureClassifier;
    private int classifyFrameCounter;
    private String confirmedGestureLabel = "";
    private String streakLabel = "";
    private int streakCount;

    public interface OnHandDetectionListener {
        void onHandDetected(HandsResult result);
        void onGesturePredicted(String label);
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
            if (handsResult.multiHandLandmarks().isEmpty()) {
                Log.d("HandDetectionProcessor", "Nenhuma mão detectada");
            } else {
                listener.onHandDetected(handsResult);
                for (LandmarkProto.NormalizedLandmark landmark :
                        handsResult.multiHandLandmarks().get(0).getLandmarkList()) {
                    Log.d("HandDetectionProcessor", String.format("Landmark: x=%.2f, y=%.2f", landmark.getX(), landmark.getY()));
                }
            }
        });

        hands.setErrorListener((message, e) -> Log.e("HandDetectionProcessor", "Erro no MediaPipe Hands: " + message, e));
    }

    public void processImageFrame(ImageProxy image) {
        if (hands != null && image != null) {
            Bitmap bitmap = imageProxyToBitmap(image);
            long timestamp = image.getImageInfo().getTimestamp();
            if (bitmap != null) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                bitmap = adjustBitmapOrientation(bitmap, rotationDegrees);
                if (gestureClassifier != null && gestureClassifier.isLoaded()) {
                    classifyFrameCounter++;
                    if (classifyFrameCounter % CLASSIFY_EVERY_N_FRAMES == 0) {
                        GestureClassifier.GesturePrediction pred =
                                gestureClassifier.predictWithScores(bitmap);
                        if (pred != null
                                && pred.confidence >= MIN_CONFIDENCE
                                && pred.margin >= MIN_MARGIN) {
                            if (pred.label.equals(streakLabel)) {
                                streakCount++;
                            } else {
                                streakLabel = pred.label;
                                streakCount = 1;
                            }
                            if (streakCount >= STREAK_TO_CONFIRM
                                    && !pred.label.equals(confirmedGestureLabel)) {
                                confirmedGestureLabel = pred.label;
                                listener.onGesturePredicted(confirmedGestureLabel);
                            }
                        } else {
                            streakCount = 0;
                            streakLabel = "";
                        }
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

    public void start() {
        if (hands == null) {
            Log.e("HandDetectionProcessor", "MediaPipe Hands não está inicializado.");
        }
    }

    public void stop() {
        if (hands != null) {
            hands.close();
        }
    }
}