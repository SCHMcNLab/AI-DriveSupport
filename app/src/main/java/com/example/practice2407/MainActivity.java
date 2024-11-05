package com.example.practice2407;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView speedTextView, gyroTextView, delta, memo;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private FileWriter fileWriter;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private boolean isRecording = false;
    private float currentSpeed = 0.0f;
    private float[] currentGyroValues = new float[3];
    private Handler handler = new Handler();
    private boolean record = false;
    private float previousSpeed = 0.0f;
    private float deltaSpeed = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speedTextView = findViewById(R.id.speedTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        delta = findViewById(R.id.delta);
        memo = findViewById(R.id.memo);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button recordButton = findViewById(R.id.recordButton);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        recordButton.setOnClickListener(v -> setRecord());
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            currentSpeed = location.getSpeed(); // m/s
            deltaSpeed = (currentSpeed - previousSpeed) * 3.6f;
            speedTextView.setText(String.format(Locale.getDefault(), "%.2f", currentSpeed * 3.6));
            delta.setText(String.format(Locale.getDefault(), "%.2f", deltaSpeed));

            previousSpeed = currentSpeed;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            currentGyroValues = event.values.clone();
            gyroTextView.setText(String.format(Locale.getDefault(), "X: %5.2f\n Y: %5.2f\nZ: %5.2f", currentGyroValues[0], currentGyroValues[1], currentGyroValues[2]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(recordDataRunnable);
    }

    private void startRecording() {
        try {
            // 현재 날짜와 시간을 파일명에 포함하여 생성
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

            // 내부 저장소의 "com.example.gpsspeed" 디렉토리 생성
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), timeStamp + ".csv");

            fileWriter = new FileWriter(file);
            fileWriter.write("Timestamp,Speed,Delta,GyroX,GyroY,GyroZ,Record\n");
            isRecording = true;
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

            handler.post(recordDataRunnable);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }

    }

    private void stopRecording() {
        try {
            if (fileWriter != null) {
                fileWriter.close();
            }
            isRecording = false;
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to stop recording", Toast.LENGTH_SHORT).show();
        }
        handler.removeCallbacks(recordDataRunnable);
    }

    private void setRecord() {
        record = true;
        Toast.makeText(this, "Record this point", Toast.LENGTH_SHORT).show();
    }

    private Runnable recordDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                writeDataToFile();
                handler.postDelayed(this, 1000); // 1000ms 후에 다시 실행
            }
        }
    };

    private void writeDataToFile() {
        String recordSign = "";
        if (record) recordSign = memo.getText().toString();
        try {
            if (fileWriter != null) {
                long time = System.currentTimeMillis();

                SimpleDateFormat year = new SimpleDateFormat("YYYY");
                SimpleDateFormat month = new SimpleDateFormat("MM");
                SimpleDateFormat day = new SimpleDateFormat("dd");
                SimpleDateFormat hour = new SimpleDateFormat("HH");
                SimpleDateFormat min = new SimpleDateFormat("mm");
                SimpleDateFormat sec = new SimpleDateFormat("ss");
                SimpleDateFormat mil = new SimpleDateFormat("SSS");

                String timestamp = year.format(time) + "-" + month.format(time) + "-" + day.format(time) + " " +
                        hour.format(time) + ":" + min.format(time) + ":" + sec.format(time) + " " + mil.format(time) + "\"";

                String speedStr = String.format(Locale.getDefault(), "%.2f", currentSpeed * 3.6);
                String deltaStr = String.format(Locale.getDefault(), "%.2f", deltaSpeed);
                String gyroStr = String.format(Locale.getDefault(), "%.2f,%.2f,%.2f", currentGyroValues[0], currentGyroValues[1], currentGyroValues[2]);
                fileWriter.write(String.format(Locale.getDefault(), "%s,%s,%s,%s,%s\n", timestamp, speedStr, deltaStr, gyroStr, recordSign));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(record == true) {
            record = false;
            memo.setText("V");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
