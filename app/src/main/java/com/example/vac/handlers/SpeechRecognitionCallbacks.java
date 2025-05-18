package com.example.vac.handlers;

public interface SpeechRecognitionCallbacks {
    void onReadyForSpeech();
    void onSpeechResult(String transcribedText);
    void onEndOfSpeech();
    void onSpeechError(String errorMessage, int errorCode);
}