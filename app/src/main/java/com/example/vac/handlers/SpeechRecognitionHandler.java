package com.example.vac.handlers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import com.example.vac.handlers.SpeechRecognitionCallbacks;  // Import the new interface

import androidx.annotation.NonNull;

import java.io.File;  // Import for File class
import com.example.vac.models.TranscriptionData;  // Import for TranscriptionData class
import com.example.vac.models.TranscriptionData.SpeakerType;  // Correct import for nested enum
import com.example.vac.managers.TranscriptionManager;  // Ensure TranscriptionManager is imported

import java.util.ArrayList;
import java.util.Locale;

/**
 * Handles speech recognition (STT) for the call screening service.
 */
public class SpeechRecognitionHandler {
    private static final String TAG = "SpeechRecHandler";

    private SpeechRecognizer speechRecognizer;
    private Context context;
    private SpeechRecognitionCallbacks listener;
    private boolean isListening = false;

    public SpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks listener) {
        this(context, listener, null);  // Default constructor calls the overloaded one
    }

    public SpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks listener, TranscriptionManager transcriptionManager) {
        this.context = context;
        this.listener = listener;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device.");
            if (listener != null) {
                listener.onSpeechError("Speech recognition not available", -1); // Custom error code
            }
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new VoiceRecognitionListener(listener, context));
    }

    public void startListening(String languageCode) {
        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer not initialized.");
            if (listener != null) {
                listener.onSpeechError("SpeechRecognizer not initialized", -2); // Custom error code
            }
            return;
        }
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring startListening call.");
            return;
        }

        Intent recognizerIntent = getIntent(languageCode);

        try {
            speechRecognizer.startListening(recognizerIntent);
            isListening = true;
            Log.d(TAG, "Started listening for language: " + languageCode);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting listening: " + e.getMessage());
            if (listener != null) {
                listener.onSpeechError("Permission denied for speech recognition.", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            }
        }
    }

    @NonNull
    private Intent getIntent(String languageCode) {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // Enable partial results
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        return recognizerIntent;
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            Log.d(TAG, "Stopped listening.");
        } else {
            Log.d(TAG, "Not listening or recognizer null, no action for stopListening.");
        }
    }

    public void release() {
        if (speechRecognizer != null) {
            // It's good practice to stop listening before destroying, 
            // though destroy() should handle ongoing operations.
            if (isListening) {
                try {
                    speechRecognizer.stopListening();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while stopping listening in release: " + e.getMessage());
                }
            }
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "SpeechRecognizer released.");
        }
        isListening = false; // Ensure isListening is reset
        context = null; // Release context
        listener = null; // Release listener
    }

    public boolean isListening() {
        return isListening;
    }
} 