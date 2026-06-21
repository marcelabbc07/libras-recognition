package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GestureClassifier {

    private static final String MODEL_FILE = "best_float32.tflite";
    private static final String LABELS_FILE = "labels.txt";
    private static final int INPUT_SIZE = 640;
    private static final int NUM_CHANNELS = 3;
    private static final float CONF_THRESHOLD = 0.35f;
    private static final float NMS_IOU_THRESHOLD = 0.45f;

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();
    private boolean loaded;
    private boolean channelsFirst = true;
    private int numClasses = 25;
    private int numAnchors = 8400;
    private final Object interpreterLock = new Object();

    public GestureClassifier(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE);
            Interpreter.Options opts = new Interpreter.Options();
            opts.setNumThreads(4);
            interpreter = new Interpreter(modelBuffer, opts);
            labels = loadLabels(context, LABELS_FILE);
            parseOutputShape();
            loaded = interpreter != null && labels.size() == numClasses;
            Log.d("GestureClassifier", "Labels: " + labels.size());
            if (!loaded) {
                Log.e("GestureClassifier", "Esperado " + numClasses + " labels; arquivo tem " + labels.size());
            }
            Log.d("GestureClassifier", "YOLO ok=" + loaded + " nc=" + numClasses + " anchors=" + numAnchors
                    + " channelsFirst=" + channelsFirst);
        } catch (Exception e) {
            Log.e("GestureClassifier", "Erro ao carregar YOLO", e);
            loaded = false;
        }
    }

    private void parseOutputShape() {
        int[] shape = interpreter.getOutputTensor(0).shape();
        if (shape.length != 3) {
            Log.w("GestureClassifier", "Saída inesperada: " + java.util.Arrays.toString(shape));
            return;
        }
        int nc = labels.size();
        int feat = 4 + nc;
        if (shape[1] == feat && shape[2] != feat) {
            channelsFirst = true;
            numClasses = nc;
            numAnchors = shape[2];
        } else if (shape[2] == feat && shape[1] != feat) {
            channelsFirst = false;
            numAnchors = shape[1];
            numClasses = nc;
        } else {
            Log.w("GestureClassifier", "Não bate 4+nc: shape=" + java.util.Arrays.toString(shape) + " nc=" + nc);
        }
    }

    private static MappedByteBuffer loadModelFile(Context context, String path) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(
                context.getAssets().openFd(path).getFileDescriptor())) {
            FileChannel channel = fis.getChannel();
            long startOffset = context.getAssets().openFd(path).getStartOffset();
            long length = context.getAssets().openFd(path).getDeclaredLength();
            return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
        }
    }

    private static List<String> loadLabels(Context context, String path) throws IOException {
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) list.add(line);
            }
        }
        return list;
    }

    public static final class GesturePrediction {
        public final String label;
        public final float confidence;
        public final float margin;

        public GesturePrediction(String label, float confidence, float margin) {
            this.label = label;
            this.confidence = confidence;
            this.margin = margin;
        }
    }

    public boolean isLoaded() {
        return loaded && interpreter != null;
    }

    public void close() {
        synchronized (interpreterLock) {
            if (interpreter != null) {
                interpreter.close();
                interpreter = null;
            }
        }
    }

    private float readOut(float[][][] out, int feature, int anchor) {
        if (channelsFirst) {
            return out[0][feature][anchor];
        }
        return out[0][anchor][feature];
    }

    private static float sigmoid(float x) {
        return 1f / (1f + (float) Math.exp(-Math.min(Math.max(x, -80f), 80f)));
    }

    private Bitmap letterbox(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        float r = Math.min((float) INPUT_SIZE / w, (float) INPUT_SIZE / h);
        int nw = Math.round(w * r);
        int nh = Math.round(h * r);
        Bitmap scaled = Bitmap.createScaledBitmap(src, nw, nh, true);
        Bitmap out = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(114, 114, 114));
        int padX = (INPUT_SIZE - nw) / 2;
        int padY = (INPUT_SIZE - nh) / 2;
        canvas.drawBitmap(scaled, padX, padY, null);
        if (scaled != src) scaled.recycle();
        return out;
    }

    private ByteBuffer bitmapToNchwFloat(Bitmap bitmap640) {
        int px = INPUT_SIZE * INPUT_SIZE;
        ByteBuffer buf = ByteBuffer.allocateDirect(1 * NUM_CHANNELS * px * 4);
        buf.order(ByteOrder.nativeOrder());
        int[] pixels = new int[px];
        bitmap640.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int c = 0; c < NUM_CHANNELS; c++) {
            for (int i = 0; i < px; i++) {
                int p = pixels[i];
                float v;
                if (c == 0) v = ((p >> 16) & 0xFF) / 255f;
                else if (c == 1) v = ((p >> 8) & 0xFF) / 255f;
                else v = (p & 0xFF) / 255f;
                buf.putFloat(v);
            }
        }
        buf.rewind();
        return buf;
    }

    private static final class Det {
        final float x1, y1, x2, y2;
        final float score;
        final int classId;
        final float margin;

        Det(float x1, float y1, float x2, float y2, float score, int classId, float margin) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.score = score;
            this.classId = classId;
            this.margin = margin;
        }
    }

    public GestureClassifier.GesturePrediction predictWithScores(Bitmap bitmap) {
        final String threadName = Thread.currentThread().getName();
        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        Log.d("GestureClassifier", "predictWithScores ENTER thread=" + threadName + " bmp=" + bw + "x" + bh);

        if (!isLoaded() || labels.isEmpty()) {
            Log.w("GestureClassifier", "predict: não carregado");
            return null;
        }

        Bitmap lb = letterbox(bitmap);
        ByteBuffer input = bitmapToNchwFloat(lb);
        if (lb != bitmap) lb.recycle();

        int d1 = channelsFirst ? (4 + numClasses) : numAnchors;
        int d2 = channelsFirst ? numAnchors : (4 + numClasses);
        float[][][] output = new float[1][d1][d2];

        synchronized (interpreterLock) {
            if (interpreter == null) {
                Log.w("GestureClassifier", "predict: interpreter null dentro do lock");
                return null;
            }
            Log.d("GestureClassifier", "before run thread=" + threadName
                    + " inPos=" + input.position() + " inLim=" + input.limit() + " cap=" + input.capacity());
            interpreter.run(input, output);
            Log.d("GestureClassifier", "after run thread=" + threadName + " out[0,0,0]=" + readOut(output, 0, 0));
        }

        List<Det> dets = new ArrayList<>();
        for (int i = 0; i < numAnchors; i++) {
            float cx = readOut(output, 0, i);
            float cy = readOut(output, 1, i);
            float bwDet = readOut(output, 2, i);
            float bhDet = readOut(output, 3, i);

            float best = -1f;
            float second = -1f;
            int cls = 0;
            for (int c = 0; c < numClasses; c++) {
                float raw = readOut(output, 4 + c, i);
                float s = raw;
                if (raw < 0f || raw > 1.0001f) {
                    s = sigmoid(raw);
                }
                if (s > best) {
                    second = best;
                    best = s;
                    cls = c;
                } else if (s > second) {
                    second = s;
                }
            }
            if (best < CONF_THRESHOLD) continue;

            if (cx <= 1.5f && cy <= 1.5f && bwDet <= 1.5f && bhDet <= 1.5f) {
                cx *= INPUT_SIZE;
                cy *= INPUT_SIZE;
                bwDet *= INPUT_SIZE;
                bhDet *= INPUT_SIZE;
            }
            float x1 = cx - bwDet / 2f;
            float y1 = cy - bhDet / 2f;
            float x2 = cx + bwDet / 2f;
            float y2 = cy + bhDet / 2f;
            float margin = best - Math.max(second, 0f);
            dets.add(new Det(x1, y1, x2, y2, best, cls, margin));
        }

        List<Det> kept = nms(dets, NMS_IOU_THRESHOLD);
        if (kept.isEmpty()) {
            Log.d("GestureClassifier", "NMS: 0 detecções");
            Log.d("GestureClassifier", "predictWithScores LEAVE (sem det)");
            return null;
        }

        Det top = kept.get(0);
        float r = Math.min((float) INPUT_SIZE / bw, (float) INPUT_SIZE / bh);
        int nw = Math.round(bw * r);
        int nh = Math.round(bh * r);
        int padX = (INPUT_SIZE - nw) / 2;
        int padY = (INPUT_SIZE - nh) / 2;
        float bx1 = (top.x1 - padX) / r;
        float by1 = (top.y1 - padY) / r;
        float bx2 = (top.x2 - padX) / r;
        float by2 = (top.y2 - padY) / r;

        String label = labels.get(Math.min(top.classId, labels.size() - 1));
        Log.d("GestureClassifier", "YOLO " + label + " conf=" + top.score + " margin=" + top.margin);
        Log.d("GestureClassifier", "predictWithScores LEAVE thread=" + threadName);
        return new GestureClassifier.GesturePrediction(label, top.score, top.margin);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float iou(Det a, Det b) {
        float ix1 = Math.max(a.x1, b.x1);
        float iy1 = Math.max(a.y1, b.y1);
        float ix2 = Math.min(a.x2, b.x2);
        float iy2 = Math.min(a.y2, b.y2);
        float iw = Math.max(0f, ix2 - ix1);
        float ih = Math.max(0f, iy2 - iy1);
        float inter = iw * ih;
        float areaA = Math.max(0f, a.x2 - a.x1) * Math.max(0f, a.y2 - a.y1);
        float areaB = Math.max(0f, b.x2 - b.x1) * Math.max(0f, b.y2 - b.y1);
        return inter / (areaA + areaB - inter + 1e-6f);
    }

    private static List<Det> nms(List<Det> dets, float iouThresh) {
        Collections.sort(dets, new Comparator<Det>() {
            @Override
            public int compare(Det a, Det b) {
                return Float.compare(b.score, a.score);
            }
        });
        List<Det> out = new ArrayList<>();
        boolean[] removed = new boolean[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            if (removed[i]) continue;
            Det a = dets.get(i);
            out.add(a);
            for (int j = i + 1; j < dets.size(); j++) {
                if (removed[j]) continue;
                Det b = dets.get(j);
                if (a.classId == b.classId && iou(a, b) > iouThresh) {
                    removed[j] = true;
                }
            }
        }
        return out;
    }
}