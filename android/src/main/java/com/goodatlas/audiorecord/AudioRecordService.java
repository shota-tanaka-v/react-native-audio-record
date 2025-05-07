package com.goodatlas.audiorecord;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class AudioRecordService extends Service {

    private static final String TAG = "AudioRecordService";
    private static final String CHANNEL_ID = "AudioRecordChannel";

    private AudioRecord recorder;
    private boolean isRecording = false;
    private Thread recordingThread;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        startRecording();
    }

    private void startRecording() {
        int sampleRate = 44100;
        int bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted");
            stopSelf();
            return;
        }

        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );
            recorder.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioRecord", e);
            stopSelf();
            return;
        }

        isRecording = true;
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            int count = 0;

            while (isRecording) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0 && ++count > 2) {
                    String base64 = Base64.encodeToString(buffer, Base64.NO_WRAP);
                    Intent intent = new Intent("AudioRecordData");
                    intent.setPackage(getPackageName());
                    intent.putExtra("data", base64);
                    sendBroadcast(intent);
                }
            }
        });
        recordingThread.start();
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    private void stopRecording() {
        isRecording = false;

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recorder", e);
            }
            recorder.release();
            recorder = null;
        }

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Recording thread join interrupted", e);
            }
            recordingThread = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音声録音サービス",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("バックグラウンドでの音声録音に使用されます");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("録音中")
                .setContentText("アプリがバックグラウンドで録音しています")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
