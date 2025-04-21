package com.example.visionandroid;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.util.concurrent.ExecutorService;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture; // Cần cho CameraProvider

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    private PreviewView previewView;
    private TextView infoTextView;
    private ExecutorService cameraExecutor; // Luồng nền cho Camera và TFLite
    private ObjectDetector objectDetector; // Đối tượng xử lý model TFLite
    private boolean objectDetectorInitialized = false; // Cờ kiểm tra khởi tạo
    private OverlayView overlayView; // Tham chiếu đến custom view vẽ kết quả
    private ProcessCameraProvider cameraProvider; // Quản lý lifecycle camera

    // Trình xử lý kết quả yêu cầu quyền
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "Quyền Camera đã được cấp.");
                    startCamera(); // Khởi động camera nếu quyền được cấp
                } else {
                    Log.e(TAG, "Quyền Camera bị từ chối.");
                    Toast.makeText(this, "Bạn cần cấp quyền Camera để ứng dụng hoạt động", Toast.LENGTH_LONG).show();
                    finish(); // Đóng ứng dụng nếu không có quyền
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        overlayView = findViewById(R.id.overlayView);

        // Khởi tạo ExecutorService cho luồng nền
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Bắt đầu quá trình khởi tạo ObjectDetector trên luồng nền
        setupObjectDetector();

        // Kiểm tra và yêu cầu quyền Camera
        requestCameraPermission();
    }

    // Kiểm tra và yêu cầu quyền Camera
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Quyền Camera đã có.");
            startCamera(); // Đã có quyền, khởi động camera
        } else {
            Log.i(TAG, "Yêu cầu quyền Camera.");
            requestPermissionLauncher.launch(CAMERA_PERMISSION); // Chưa có quyền, yêu cầu
        }
        // Lưu ý: Xử lý trường hợp shouldShowRequestPermissionRationale có thể thêm vào đây nếu cần
    }

    // Khởi tạo ObjectDetector trên luồng nền
    private void setupObjectDetector() {
        cameraExecutor.execute(() -> {
            try {
                // Cấu hình cơ bản cho ObjectDetector
                BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();
                // baseOptionsBuilder.useGpu(); // Bỏ comment nếu muốn dùng GPU và đã thêm dependency
                 baseOptionsBuilder.setNumThreads(1); // Tùy chỉnh số luồng

                // Cấu hình chi tiết cho ObjectDetector
                ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder =
                        ObjectDetector.ObjectDetectorOptions.builder()
                                .setBaseOptions(baseOptionsBuilder.build())
                                .setMaxResults(5)       // Số kết quả tối đa hiển thị
                                .setScoreThreshold(0.5f); // Ngưỡng tin cậy (0.0 -> 1.0)

                // !! THAY THẾ BẰNG TÊN FILE MODEL CỦA BẠN !!
                String modelName = "yolov5.tflite";

                // Tạo ObjectDetector từ file model trong assets
                objectDetector = ObjectDetector.createFromFileAndOptions(
                        this, // Context
                        modelName,
                        optionsBuilder.build());

                objectDetectorInitialized = true; // Đánh dấu đã khởi tạo thành công
                Log.d(TAG, "ObjectDetector đã khởi tạo thành công.");

            } catch (IOException e) {
                objectDetectorInitialized = false;
                Log.e(TAG, "Lỗi TFLite khi khởi tạo ObjectDetector: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Lỗi khởi tạo mô hình TFLite: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) { // Bắt các lỗi khác (ví dụ: IllegalArgumentException)
                objectDetectorInitialized = false;
                Log.e(TAG, "Lỗi không xác định khi khởi tạo ObjectDetector: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Lỗi không xác định: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    // Khởi động và cấu hình CameraX
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Cấu hình Preview UseCase
                Preview preview = new Preview.Builder().build();
                // --- Thay đổi 3: Sử dụng biến previewView đã tìm bằng findViewById ---
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Cấu hình ImageAnalysis UseCase (Giữ nguyên)
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalyzer.setAnalyzer(cameraExecutor, this::analyzeImage);

                // Chọn camera sau (Giữ nguyên)
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Bind các UseCase vào Lifecycle (Giữ nguyên)
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
                Log.d(TAG, "Camera Use Cases đã được bind thành công.");

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khi lấy CameraProvider: " + e.getMessage(), e);
                Toast.makeText(this, "Không thể khởi tạo CameraProvider.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi bind Camera Use Cases: " + e.getMessage(), e);
                Toast.makeText(this, "Không thể khởi động camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Phương thức xử lý từng frame ảnh từ CameraX
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!objectDetectorInitialized || objectDetector == null) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = imageProxy.toBitmap();
        if (bitmap == null) {
            Log.e(TAG, "Không thể chuyển ImageProxy sang Bitmap");
            imageProxy.close();
            return;
        }
        final int imageProxyHeight = imageProxy.getHeight();
        final int imageProxyWidth = imageProxy.getWidth();
        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

        List<Detection> results = null;
        long inferenceTime = 0L;
        try {
            long startTime = System.currentTimeMillis();
            results = objectDetector.detect(tensorImage);
            inferenceTime = System.currentTimeMillis() - startTime;

        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi chạy ObjectDetector: " + e.getMessage(), e);
        } finally {
            final List<Detection> finalResults = results;
            final long finalInferenceTime = inferenceTime;

            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) {
                    return;
                }
                // --- Thay đổi 4: Sử dụng biến overlayView và infoTextView đã tìm bằng findViewById ---
                // Cập nhật OverlayView
                if (overlayView != null) { // Vẫn nên kiểm tra null
                    overlayView.setResults(
                            finalResults != null ? finalResults : new LinkedList<>(),
                            imageProxyHeight,
                            imageProxyWidth
                    );
                }

                // Cập nhật TextView thông tin
                if (infoTextView != null) { // Vẫn nên kiểm tra null
                    float fps = (finalInferenceTime > 0) ? 1000f / finalInferenceTime : 0f;
                    infoTextView.setText(
                            String.format(Locale.US, "Time: %d ms\nFPS: %.1f", finalInferenceTime, fps)
                    );
                }
            });

            imageProxy.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng ExecutorService (Giữ nguyên)
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            Log.d(TAG, "cameraExecutor đã shutdown.");
        }
    }
}