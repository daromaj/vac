package com.example.vac.handlers;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Handles recording of messages from callers using MediaRecorder.
 * Enforces a 60-second recording limit.
 */
public class MessageRecorderHandler {
    private static final String TAG = "MessageRecorderHandler";
    private static final int MAX_RECORDING_DURATION_MS = 60 * 1000; // 60 seconds
    
    private final Context context;
    private final MessageRecorderListener listener;
    private final Handler handler;
    
    private MediaRecorder mediaRecorder;
    private String currentFilePath;
    private boolean isRecording = false;
    
    /**
     * Constructor for MessageRecorderHandler
     * 
     * @param context The context
     * @param listener The listener for recording events
     */
    public MessageRecorderHandler(Context context, MessageRecorderListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Start recording a message
     * 
     * @param outputFileName The filename for the recorded message
     */
    public void startRecording(String outputFileName) {
        if (isRecording) {
            stopRecording();
        }
        
        File outputDir = context.getFilesDir();
        File outputFile = new File(outputDir, outputFileName);
        currentFilePath = outputFile.getAbsolutePath();
        
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(currentFilePath);
            
            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
                
                Log.d(TAG, "Started recording to: " + currentFilePath);
                
                if (listener != null) {
                    listener.onRecordingStarted();
                }
                
                // Set a timer for the maximum recording duration
                handler.postDelayed(this::onRecordingLimitReached, MAX_RECORDING_DURATION_MS);
                
            } catch (IOException e) {
                Log.e(TAG, "Error preparing MediaRecorder", e);
                releaseMediaRecorder();
                
                if (listener != null) {
                    listener.onRecordingError("Error preparing MediaRecorder: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaRecorder", e);
            releaseMediaRecorder();
            
            if (listener != null) {
                listener.onRecordingError("Error setting up MediaRecorder: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop recording the message
     */
    public void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                Log.d(TAG, "Stopped recording");
                
                String finalPath = currentFilePath;
                
                // Remove the recording limit callback
                handler.removeCallbacksAndMessages(null);
                
                if (listener != null) {
                    listener.onRecordingStopped(finalPath, true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder", e);
                
                if (listener != null) {
                    listener.onRecordingError("Error stopping MediaRecorder: " + e.getMessage());
                }
            } finally {
                isRecording = false;
                releaseMediaRecorder();
            }
        }
    }
    
    /**
     * Called when the recording time limit is reached
     */
    private void onRecordingLimitReached() {
        Log.d(TAG, "Recording time limit reached");
        
        if (isRecording) {
            if (listener != null) {
                listener.onRecordingLimitReached();
            }
            
            stopRecording();
        }
    }
    
    /**
     * Release the MediaRecorder
     */
    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            } finally {
                mediaRecorder = null;
            }
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        handler.removeCallbacksAndMessages(null);
        
        if (isRecording) {
            stopRecording();
        } else {
            releaseMediaRecorder();
        }
    }
    
    /**
     * Interface for MessageRecorderHandler to communicate with CallSessionManager
     */
    public interface MessageRecorderListener {
        void onRecordingStarted();
        void onRecordingStopped(String filePath, boolean successfullyCompleted);
        void onRecordingError(String errorMessage);
        void onRecordingLimitReached();
    }
} 