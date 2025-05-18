package com.example.vac.handlers;

import android.content.Context;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.example.vac.models.TranscriptionData;
import com.example.vac.models.TranscriptionData.SpeakerType;
import com.example.vac.managers.TranscriptionManager;

import java.util.ArrayList;
import java.io.IOException;

public class VoiceRecognitionListener implements RecognitionListener {
    private static final String TAG = "SpeechRecHandler";
    private SpeechRecognitionCallbacks listener;
    private Context context;

    public VoiceRecognitionListener(SpeechRecognitionCallbacks listener, Context context) {
        this.listener = listener;
        this.context = context;
    }

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
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // No action needed
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        // No action needed
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(int error) {
        String errorMessage = getErrorText(error);
        Log.e(TAG, "onError: " + errorMessage + " (code: " + error + ")");
        if (listener != null) {
            listener.onSpeechError(errorMessage, error);
        }
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            long timestamp = System.currentTimeMillis();
            String callId = "default_call_id";
            TranscriptionData transcription = new TranscriptionData(timestamp, text, callId, SpeakerType.CALLER);
            try {
                TranscriptionManager.getInstance().saveTranscription(transcription);
            } catch (IOException e) {
                Log.e(TAG, "Failed to save transcription: " + e.getMessage());
            }
            if (listener != null) {
                listener.onSpeechResult(text);
            }
        } else {
            Log.d(TAG, "onResults: No matches found.");
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            long timestamp = System.currentTimeMillis();
            String callId = "default_call_id";
            TranscriptionData transcription = new TranscriptionData(timestamp, text, callId, SpeakerType.CALLER);
            try {
                TranscriptionManager.getInstance().saveTranscription(transcription);
            } catch (IOException e) {
                Log.e(TAG, "Failed to save partial transcription: " + e.getMessage());
            }
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
