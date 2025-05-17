package com.example.vac.utils;

import android.util.Log;

public class SpeakerIdentifier {
    private AudioLevelMonitor audioLevelMonitor;
    private boolean isAssistantSpeaking = false;  // Tracked externally, e.g., from AudioHandler
    private boolean isUserTakeOverActive = false; // Tracked externally, e.g., from CallSessionManager

    public SpeakerIdentifier(AudioLevelMonitor audioLevelMonitor) {
        this.audioLevelMonitor = audioLevelMonitor;
    }

    public void setAssistantSpeaking(boolean speaking) {
        this.isAssistantSpeaking = speaking;
    }

    public void setUserTakeOverActive(boolean active) {
        this.isUserTakeOverActive = active;
    }

    public SpeakerType identifySpeaker(String text, long timestamp, float localMicLevel) {
        if (isAssistantSpeaking) {
            return SpeakerType.ASSISTANT;  // Assistant is speaking
        } else if (audioLevelMonitor.isUserSpeaking(50.0f)) {  // Pass a sample threshold value, e.g., 50.0f
            return SpeakerType.USER;  // User is speaking based on audio levels
        } else {
            return SpeakerType.CALLER;  // By process of elimination, assume caller
        }
    }

    public boolean isAssistantSpeaking() {
        return isAssistantSpeaking;
    }

    public boolean isUserTakeOverActive() {
        return isUserTakeOverActive;
    }

    // Handle edge cases, e.g., transitions
    public void handleTransition() {
        // Logic to smooth transitions, e.g., log or flag changes
        Log.d("SpeakerIdentifier", "Speaker transition detected");
    }

    public enum SpeakerType {
        ASSISTANT,
        USER,
        CALLER
    }
}