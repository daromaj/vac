package com.example.vac.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.vac.models.TranscriptionData.SpeakerType;

/**
 * Determines the speaker for each transcription snippet using audio levels,
 * TTS state, and user take-over state.
 */
public class SpeakerIdentifier {
    private static final String TAG = "SpeakerIdentifier";
    private static final float CALLER_SPEAKING_THRESHOLD = 0.2f; // Lower threshold for caller detection

    private final AudioLevelMonitor audioLevelMonitor;
    private boolean isAssistantSpeaking;
    private boolean isUserTakeOverActive;

    public SpeakerIdentifier(@NonNull Context context) {
        this.audioLevelMonitor = new AudioLevelMonitor(context);
        this.isAssistantSpeaking = false;
        this.isUserTakeOverActive = false;
    }

    /**
     * Starts monitoring audio levels for speaker identification.
     */
    public void startMonitoring() {
        audioLevelMonitor.startMonitoring();
    }

    /**
     * Stops monitoring audio levels.
     */
    public void stopMonitoring() {
        audioLevelMonitor.stopMonitoring();
    }

    /**
     * Identifies the speaker for a given transcription snippet.
     *
     * @param text The transcribed text
     * @param timestamp The timestamp of the transcription
     * @param localMicLevel The current local microphone level
     * @return The identified speaker type
     */
    public SpeakerType identifySpeaker(String text, long timestamp, float localMicLevel) {
        if (isAssistantSpeaking) {
            return SpeakerType.ASSISTANT;
        }

        if (isUserTakeOverActive) {
            return SpeakerType.USER;
        }

        // If local mic level is high, it's the user speaking
        if (localMicLevel > audioLevelMonitor.getCurrentLevel()) {
            return SpeakerType.USER;
        }

        // If we detect audio but it's not from local mic, it's the caller
        if (audioLevelMonitor.getCurrentLevel() > CALLER_SPEAKING_THRESHOLD) {
            return SpeakerType.CALLER;
        }

        // Default to caller if we can't determine
        return SpeakerType.CALLER;
    }

    /**
     * Sets whether the assistant is currently speaking (TTS active).
     *
     * @param speaking true if assistant is speaking, false otherwise
     */
    public void setAssistantSpeaking(boolean speaking) {
        this.isAssistantSpeaking = speaking;
        Log.d(TAG, "Assistant speaking state: " + speaking);
    }

    /**
     * Sets whether the user has taken over the call.
     *
     * @param active true if user has taken over, false otherwise
     */
    public void setUserTakeOverActive(boolean active) {
        this.isUserTakeOverActive = active;
        Log.d(TAG, "User take-over state: " + active);
    }

    /**
     * Checks if the assistant is currently speaking.
     *
     * @return true if assistant is speaking, false otherwise
     */
    public boolean isAssistantSpeaking() {
        return isAssistantSpeaking;
    }

    /**
     * Checks if the user has taken over the call.
     *
     * @return true if user has taken over, false otherwise
     */
    public boolean isUserTakeOverActive() {
        return isUserTakeOverActive;
    }
} 