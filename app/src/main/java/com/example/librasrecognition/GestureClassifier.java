package com.example.librasrecognition;

import android.content.Context;
import android.graphics.Bitmap;
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
import java.util.List;

/**
 * Classificador de gestos em Libras usando modelo .tflite treinado com imagens 224x224.
 * Carrega gesture_model.tflite e labels.txt dos assets.
 */
public class GestureClassifier {

    private static final String MODEL_FILE = "gesture_model.tflite";
    private static final String LABELS_FILE = "labels.txt";
    private static final int IMG_SIZE = 224;
    private static final int NUM_CHANNELS = 3;

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();
    private boolean loaded;

    public GestureClassifier(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE);
            interpreter = new Interpreter(modelBuffer);
            labels = loadLabels(context, LABELS_FILE);
            loaded = true;
            Log.d("GestureClassifier", "Modelo carregado. Classes: " + labels.size());
        } catch (IOException e) {
            Log.e("GestureClassifier", "Erro ao carregar modelo ou labels", e);
            loaded = false;
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String path) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(
                context.getAssets().openFd(path).getFileDescriptor())) {
            FileChannel channel = fis.getChannel();
            long startOffset = context.getAssets().openFd(path).getStartOffset();
            long length = context.getAssets().openFd(path).getDeclaredLength();
            return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
        }
    }

    private List<String> loadLabels(Context context, String path) throws IOException {
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

    /** Redimensiona para 224x224, normaliza 0-1 e retorna a letra prevista. */
    public String predict(Bitmap bitmap) {
        if (!loaded || interpreter == null || labels.isEmpty()) return "";

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * IMG_SIZE * IMG_SIZE * NUM_CHANNELS * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[IMG_SIZE * IMG_SIZE];
        resized.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            inputBuffer.putFloat(((p >> 16) & 0xFF) / 255f);
            inputBuffer.putFloat(((p >> 8) & 0xFF) / 255f);
            inputBuffer.putFloat((p & 0xFF) / 255f);
        }
        inputBuffer.rewind();

        int numClasses = labels.size();
        float[][] output = new float[1][numClasses];
        interpreter.run(inputBuffer, output);

        int maxIdx = 0;
        for (int i = 1; i < numClasses; i++) {
            if (output[0][i] > output[0][maxIdx]) maxIdx = i;
        }
        return labels.get(maxIdx);
    }

    public boolean isLoaded() { return loaded; }

    public void close() {
        if (interpreter != null) interpreter.close();
    }
}