package com.example.vac.models;

import androidx.annotation.NonNull;

/**
 * Data class representing a transcription snippet with speaker information.
 */
public class TranscriptionData {
    private final long timestamp;
    private final String text;
    private final String callId;
    private final SpeakerType speakerType;

    public enum SpeakerType {
        ASSISTANT,
        USER,
        CALLER
    }

    public TranscriptionData(long timestamp, String text, String callId, SpeakerType speakerType) {
        this.timestamp = timestamp;
        this.text = text;
        this.callId = callId;
        this.speakerType = speakerType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getText() {
        return text;
    }

    public String getCallId() {
        return callId;
    }

    public SpeakerType getSpeakerType() {
        return speakerType;
    }

    @NonNull
    @Override
    public String toString() {
        return "TranscriptionData{" +
                "timestamp=" + timestamp +
                ", text='" + text + '\'' +
                ", callId='" + callId + '\'' +
                ", speakerType=" + speakerType +
                '}';
    }
} 