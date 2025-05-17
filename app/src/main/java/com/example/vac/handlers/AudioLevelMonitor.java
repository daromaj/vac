package com.example.vac.handlers;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitors local microphone audio levels to help identify speakers.
 */
public class AudioLevelMonitor {
    private static final String TAG = "AudioLevelMonitor";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final float SPEAKING_THRESHOLD = 0.3f; // Adjust this value based on testing
    private static final int UPDATE_INTERVAL_MS = 100; // Update every 100ms

    private final Context context;
    private AudioRecord audioRecord;
    private boolean isMonitoring;
    private float currentLevel;
    private Thread monitoringThread;

    public AudioLevelMonitor(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Starts monitoring the microphone audio levels.
     */
    public void startMonitoring() {
        if (isMonitoring) {
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord");
                return;
            }

            isMonitoring = true;
            audioRecord.startRecording();

            monitoringThread = new Thread(this::monitorAudioLevel);
            monitoringThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio monitoring", e);
            release();
        }
    }

    /**
     * Stops monitoring the microphone audio levels.
     */
    public void stopMonitoring() {
        isMonitoring = false;
        if (monitoringThread != null) {
            monitoringThread.interrupt();
            monitoringThread = null;
        }
        release();
    }

    /**
     * Gets the current audio level (0.0 to 1.0).
     *
     * @return Current audio level
     */
    public float getCurrentLevel() {
        return currentLevel;
    }

    /**
     * Checks if the user is currently speaking based on audio level threshold.
     *
     * @return true if user is speaking, false otherwise
     */
    public boolean isUserSpeaking() {
        return currentLevel > SPEAKING_THRESHOLD;
    }

    private void monitorAudioLevel() {
        short[] buffer = new short[BUFFER_SIZE];
        while (isMonitoring && !Thread.currentThread().isInterrupted()) {
            int readSize = audioRecord.read(buffer, 0, buffer.length);
            if (readSize > 0) {
                // Calculate RMS (Root Mean Square) of the audio buffer
                long sum = 0;
                for (int i = 0; i < readSize; i++) {
                    sum += buffer[i] * buffer[i];
                }
                double rms = Math.sqrt(sum / (double) readSize);
                
                // Normalize to 0.0-1.0 range
                currentLevel = (float) (rms / Short.MAX_VALUE);
            }

            try {
                Thread.sleep(UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void release() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
    }
} 