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
public class SpeechRecognitionHandler implements RecognitionListener {
    private static final String TAG = "SpeechRecognitionHandler";
    
    private final Context context;
    private final SpeechRecognitionCallbacks callbacks;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    
    /**
     * Constructor for SpeechRecognitionHandler
     * 
     * @param context The context
     * @param callbacks The callbacks for speech recognition events
     */
    public SpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        
        initializeSpeechRecognizer();
    }
    
    /**
     * Initialize the speech recognizer
     */
    private void initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device");
            if (callbacks != null) {
                callbacks.onSpeechError("Speech recognition is not available on this device", -1);
            }
            return;
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(this);
    }
    
    /**
     * Start listening for speech
     * 
     * @param languageCode The language code to use (e.g., "pl-PL")
     */
    public void startListening(String languageCode) {
        if (speechRecognizer == null) {
            Log.e(TAG, "Speech recognizer is null");
            return;
        }
        
        if (isListening) {
            stopListening();
        }
        
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        
        // For call screening, we want continuous recognition
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        
        try {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Started listening for speech");
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            isListening = false;
            if (callbacks != null) {
                callbacks.onSpeechError("Error starting speech recognition: " + e.getMessage(), -1);
            }
        }
    }
    
    /**
     * Stop listening for speech
     */
    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
                Log.d(TAG, "Stopped listening for speech");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping speech recognition", e);
            } finally {
                isListening = false;
            }
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        stopListening();
        
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
                Log.d(TAG, "Speech recognizer destroyed");
            } catch (Exception e) {
                Log.e(TAG, "Error destroying speech recognizer", e);
            } finally {
                speechRecognizer = null;
            }
        }
    }
    
    // RecognitionListener implementation
    
    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "Ready for speech");
        if (callbacks != null) {
            callbacks.onReadyForSpeech();
        }
    }
    
    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "Beginning of speech");
    }
    
    @Override
    public void onRmsChanged(float rmsdB) {
        // Not used in MVP
    }
    
    @Override
    public void onBufferReceived(byte[] buffer) {
        // Not used in MVP
    }
    
    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "End of speech");
        isListening = false;
        
        if (callbacks != null) {
            callbacks.onEndOfSpeech();
        }
    }
    
    @Override
    public void onError(int error) {
        isListening = false;
        
        String errorMessage;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                errorMessage = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errorMessage = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errorMessage = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errorMessage = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                errorMessage = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errorMessage = "No recognition match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errorMessage = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errorMessage = "Server error";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errorMessage = "No speech input";
                break;
            default:
                errorMessage = "Unknown error";
                break;
        }
        
        Log.e(TAG, "Error in speech recognition: " + errorMessage + " (" + error + ")");
        
        if (callbacks != null) {
            callbacks.onSpeechError(errorMessage, error);
        }
    }
    
    @Override
    public void onResults(Bundle results) {
        isListening = false;
        
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String bestMatch = matches.get(0);
            Log.d(TAG, "Speech recognition result: " + bestMatch);
            
            if (callbacks != null) {
                callbacks.onSpeechResult(bestMatch);
            }
        } else {
            Log.d(TAG, "No speech recognition results");
            
            if (callbacks != null) {
                callbacks.onSpeechError("No speech recognition results", -1);
            }
        }
    }
    
    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String bestMatch = matches.get(0);
            Log.d(TAG, "Partial speech recognition result: " + bestMatch);
            
            if (callbacks != null) {
                callbacks.onSpeechResult(bestMatch);
            }
        }
    }
    
    @Override
    public void onEvent(int eventType, Bundle params) {
        // Not used in MVP
    }
    
    /**
     * Interface for SpeechRecognitionHandler to communicate with CallSessionManager
     */
    public interface SpeechRecognitionCallbacks {
        void onReadyForSpeech();
        void onSpeechResult(String transcribedText);
        void onEndOfSpeech();
        void onSpeechError(String errorMessage, int errorCode);
    }
} 