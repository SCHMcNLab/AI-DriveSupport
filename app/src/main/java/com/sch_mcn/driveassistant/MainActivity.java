package com.sch_mcn.driveassistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final int PERMISSION_REQUEST_CODE = 1;

    private LocationManager locationManager;
    private TextView speedTextView, accelerationTextView, statusTextView;
    private ConstraintLayout layout;

    private float lastSpeed = 0.0f;
    private Queue<Float> accelerationQueue = new LinkedList<>();
    private Interpreter tflite;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speedTextView = findViewById(R.id.speedTextView);
        accelerationTextView = findViewById(R.id.accelerationTextView);
        statusTextView = findViewById(R.id.statusTextView);
        layout = findViewById(R.id.layout);

        try {
            tflite = new Interpreter(loadModelFile());
            Log.d("MainActivity", "TensorFlow Lite model initialized successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("MainActivity", "Error loading TensorFlow Lite model", e);
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("lstm_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        float currentSpeed = location.getSpeed();  // m/s 단위 속도
        float acceleration = (currentSpeed - lastSpeed) * 3.6f;  // 가속도(kph) 계산
        lastSpeed = currentSpeed;

        // 가속도 값 큐에 추가 및 슬라이딩 윈도우 관리
        if (accelerationQueue.size() >= 10) {
            accelerationQueue.poll();  // 오래된 값 제거
        }
        accelerationQueue.add(acceleration);

        updateUI(currentSpeed, acceleration);

        // 큐가 15개 값을 가질 때마다 모델 실행
        if (accelerationQueue.size() == 10) {
            float[][][] input = new float[1][10][1];
            int i = 0;
            for (Float acc : accelerationQueue) {
                input[0][i++][0] = acc;
            }

            // for test - irregular case
            //input = new float[][][]{{{1}, {2}, {-2}, {2}, {1}, {-1},{1}, {2}, {-1}, {1}, {1}}};

            // 모델 출력 저장
            float[][] output = new float[1][1];
            if (tflite != null) {
                tflite.run(input, output);
            } else {
                Log.e("MainActivity", "TensorFlow Lite Interpreter is not initialized.");
            }

            // 결과에 따라 화면 업데이트
            if (output[0][0] > 0.66) {  // 예: 0.5 기준값
                setStatus("비정상주행중", 0xFF550000);
            } else {
                setStatus("정상주행중", Color.BLACK);
            }
        }
    }

    private void setStatus(String status, int color) {
        statusTextView.setText(status);
        layout.setBackgroundColor(color);
    }

    private void updateUI(float speed, float acceleration) {
        speedTextView.setText(String.format("%.1f", speed * 3.6));
        accelerationTextView.setText(String.format("%.1f", acceleration * 3.6));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
}

