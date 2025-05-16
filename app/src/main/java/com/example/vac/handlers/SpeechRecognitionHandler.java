package com.example.vac.handlers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

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
    
    public interface SpeechRecognitionCallbacks {
        void onReadyForSpeech();
        void onSpeechResult(String transcribedText);
        void onEndOfSpeech();
        void onSpeechError(String errorMessage, int errorCode);
    }
    
    public SpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks listener) {
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
        speechRecognizer.setRecognitionListener(new VoiceRecognitionListener());
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

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // Enable partial results
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        // Additional flags might be useful, e.g., PREFER_OFFLINE if desired and available
        // recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

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
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "SpeechRecognizer released.");
        }
        context = null; // Release context
        listener = null; // Release listener
    }
    
    public boolean isListening() {
        return isListening;
    }
    
    private class VoiceRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
            if (listener != null) {
                listener.onReadyForSpeech();
            }
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
            // Listener doesn't have a direct callback for this, could be added if needed.
        }
        
        @Override
        public void onRmsChanged(float rmsdB) {
            // Listener doesn't have a direct callback for this.
        }
        
        @Override
        public void onBufferReceived(byte[] buffer) {
            // Listener doesn't have a direct callback for this.
        }
        
        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech");
            isListening = false; // Mark as not actively listening once speech ends
            if (listener != null) {
                listener.onEndOfSpeech();
            }
        }
        
        @Override
        public void onError(int error) {
            String errorMessage = getErrorText(error);
            Log.e(TAG, "onError: " + errorMessage + " (code: " + error + ")");
            isListening = false; // Stop listening on error
            if (listener != null) {
                listener.onSpeechError(errorMessage, error);
            }
        }
        
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.d(TAG, "onResults: " + text);
                if (listener != null) {
                    listener.onSpeechResult(text);
                }
            } else {
                Log.d(TAG, "onResults: No matches found.");
                 // If partial results are not enabled, this might be the only time results come.
                 // If no final match, it could be an implicit error or "no match".
                 // For robust handling, consider if this should also trigger onSpeechError or a specific "no match" callback.
            }
            // isListening = false; // Typically, stop listening after final results.
            // However, some implementations might want continuous listening until explicitly stopped.
            // For now, onEndOfSpeech handles isListening = false.
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.d(TAG, "onPartialResults: " + text);
                // For MVP, we primarily care about final results for transcription display.
                // If live update of partial results is needed for the notification,
                // the listener interface would need an onPartialSpeechResult callback.
                // For now, we will send partial results through the main onSpeechResult.
                 if (listener != null) {
                    listener.onSpeechResult(text);
                }
            }
        }
        
        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent: " + eventType);
        }
        
        private String getErrorText(int errorCode) {
            String message;
            switch (errorCode) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "Audio recording error";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "Client side error";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "Insufficient permissions";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "Network error";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "Network timeout";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "No match";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RecognitionService busy";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "error from server";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "No speech input";
                    break;
                default:
                    message = "Didn't understand, please try again.";
                    break;
            }
            return message;
        }
    }
} 