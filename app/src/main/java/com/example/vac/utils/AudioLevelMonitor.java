package com.example.vac.utils;

import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioLevelMonitor {
    private AudioRecordInterface audioRecord;
    private int sampleRate = 8000;  // Standard sample rate
    private int bufferSize = 1024;  // Fallback value
    private boolean isRecording = false;

    public AudioLevelMonitor(AudioRecordInterface audioRecord) {
        this.audioRecord = audioRecord;
    }

    public AudioLevelMonitor() {
        this(new AudioRecordWrapper(8000, 1024));  // Hardcode values to avoid constructor issues
    }

    private static class AudioRecordWrapper implements AudioRecordInterface {
        private AudioRecord realAudioRecord;
        private int sampleRate;
        private int bufferSize;

        public AudioRecordWrapper(int sampleRate, int bufferSize) {
            this.sampleRate = sampleRate;
            this.bufferSize = bufferSize;
            realAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, this.sampleRate, 16, 2, this.bufferSize);  // CHANNEL_IN_MONO=16, ENCODING_PCM_16BIT=2
        }

        @Override
        public int getState() {
            return realAudioRecord.getState();
        }

        @Override
        public void startRecording() {
            realAudioRecord.startRecording();
        }

        @Override
        public void stop() {
            realAudioRecord.stop();
        }

        @Override
        public void release() {
            realAudioRecord.release();
        }

        @Override
        public int read(short[] audioData, int offsetInShorts, int sizeInShorts, int readMode) {
            return realAudioRecord.read(audioData, offsetInShorts, sizeInShorts, readMode);
        }
    }

    public void startMonitoring() {
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            isRecording = true;
        }
    }

    public void stopMonitoring() {
        if (isRecording) {
            audioRecord.stop();
            audioRecord.release();
            isRecording = false;
        }
    }

    public int getAudioLevel() {
        short[] buffer = new short[bufferSize];
        if (isRecording) {
            int bytesRead = audioRecord.read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING);
            if (bytesRead > 0) {
                double sum = 0;
                for (short s : buffer) {
                    sum += s * s;
                }
                double rms = Math.sqrt(sum / bytesRead);
                return (int) rms;  // Return RMS as audio level
            }
        }
        return 0;
    }

    public boolean isAboveThreshold(int threshold) {
        int level = getAudioLevel();
        return level > threshold;
    }
}