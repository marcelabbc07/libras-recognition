package com.example.librasrecognition;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements HandDetectionProcessor.OnHandDetectionListener {

    private static final int LENS_FACING_BACK = CameraSelector.LENS_FACING_BACK;
    private static final int LENS_FACING_FRONT = CameraSelector.LENS_FACING_FRONT;

    private PreviewView previewView;
    private HandOverlayView handOverlayView;
    private TextView gestureTextView;
    private View cameraPlaceholder;
    private MaterialToolbar toolbar;
    private FloatingActionButton btnSwitchCamera;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private HandDetectionProcessor handDetectionProcessor;
    private GestureClassifier gestureClassifier;
    private ExecutorService cameraAnalysisExecutor;

    private int lensFacing = LENS_FACING_BACK;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    hidePlaceholderAndStartCamera();
                } else {
                    Toast.makeText(this, R.string.permission_denied_toast, Toast.LENGTH_SHORT).show();
                    updatePlaceholderVisibility();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        handOverlayView = findViewById(R.id.handOverlayView);
        gestureTextView = findViewById(R.id.gestureTextView);
        cameraPlaceholder = findViewById(R.id.cameraPlaceholder);
        toolbar = findViewById(R.id.toolbar);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);

        cameraAnalysisExecutor = Executors.newSingleThreadExecutor();
        gestureClassifier = new GestureClassifier(this);
        handDetectionProcessor = new HandDetectionProcessor(this, this, gestureClassifier);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_help) {
                startActivity(new android.content.Intent(this, HelpActivity.class));
                return true;
            }
            return false;
        });

        btnSwitchCamera.setOnClickListener(v -> {
            lensFacing = (lensFacing == LENS_FACING_BACK) ? LENS_FACING_FRONT : LENS_FACING_BACK;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                bindCameraUseCases();
            }
        });

        gestureTextView.setText(R.string.gesto_placeholder);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hidePlaceholderAndStartCamera();
        } else {
            updatePlaceholderVisibility();
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void hidePlaceholderAndStartCamera() {
        cameraPlaceholder.setVisibility(View.GONE);
        startCamera();
    }

    private void updatePlaceholderVisibility() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        cameraPlaceholder.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                android.util.Log.e("CameraX", "Erro ao iniciar a câmera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }

        Preview preview = new Preview.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraAnalysisExecutor, handDetectionProcessor::processImageFrame);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            boolean isFront = lensFacing == LENS_FACING_FRONT;
            handOverlayView.setCameraFacing(isFront);
        } catch (Exception e) {
            android.util.Log.e("CameraX", "Falha ao vincular câmera", e);
            Toast.makeText(this, R.string.camera_bind_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onHandDetected(HandsResult result) {
        if (result.multiHandLandmarks().isEmpty()) {
            handOverlayView.setLandmarks(Collections.emptyList());
        } else {
            handOverlayView.setLandmarks(result.multiHandLandmarks().get(0).getLandmarkList());
        }
    }

    @Override
    public void onGesturePredicted(GestureClassifier.GesturePrediction pred) {
        if (gestureTextView == null) {
            return;
        }
        if (pred == null) {
            gestureTextView.setText(R.string.gesto_placeholder);
            return;
        }
        gestureTextView.setText(pred.label);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handDetectionProcessor != null) {
            handDetectionProcessor.stop();
        }
        if (gestureClassifier != null) {
            gestureClassifier.close();
        }
        if (cameraAnalysisExecutor != null) {
            cameraAnalysisExecutor.shutdown();
        }
    }
}