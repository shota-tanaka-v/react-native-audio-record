package com.goodatlas.audiorecord;

import android.app.*;
import android.content.Intent;
import android.media.*;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.FileOutputStream;

public class AudioRecordService extends Service {
    private static final String CHANNEL_ID = "AudioRecordServiceChannel";
    private static final String TAG = "AudioRecordService";

    private AudioRecord recorder;
    private Thread recordingThread;
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        startRecording();
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("録音中")
                .setContentText("アプリが音声を録音しています")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Audio Record Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void startRecording() {
        int sampleRate = 16000;
        int bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        recorder.startRecording();
        isRecording = true;

        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try {
                FileOutputStream os = new FileOutputStream(getFilesDir() + "/temp_bg.pcm");

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        os.write(buffer, 0, read);
                        // base64送信する場合はBroadcastなどでJSへ通知可能
                    }
                }

                os.close();
            } catch (Exception e) {
                Log.e(TAG, "録音中にエラー", e);
            }
        });

        recordingThread.start();
    }

    private void stopRecording() {
        isRecording = false;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
