package com.example.vac.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class TranscriptionData {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public enum SpeakerType {
        @SerializedName("assistant") ASSISTANT,
        @SerializedName("user") USER,
        @SerializedName("caller") CALLER
    }

    @SerializedName("timestamp")
    private long timestamp;
    
    @SerializedName("text") 
    private String text;
    
    @SerializedName("callId")
    private String callId;
    
    @SerializedName("speakerType")
    private SpeakerType speakerType;

    public TranscriptionData(long timestamp, String text, String callId, SpeakerType speakerType) {
        this.timestamp = timestamp;
        this.text = text;
        this.callId = callId;
        this.speakerType = speakerType;
    }

    // Getters
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

    // Setters
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public void setSpeakerType(SpeakerType speakerType) {
        this.speakerType = speakerType;
    }

    @Override
    public String toString() {
        return "TranscriptionData{" +
                "timestamp=" + timestamp +
                ", text='" + text + '\'' +
                ", callId='" + callId + '\'' +
                ", speakerType=" + speakerType +
                '}';
    }

    // JSON Serialization
    public String toJson() {
        return gson.toJson(this);
    }

    public static TranscriptionData fromJson(String json) {
        return gson.fromJson(json, TranscriptionData.class);
    }

    // File persistence
    public void saveToFile(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(this.toJson());
        }
    }

    public static TranscriptionData loadFromFile(File file) throws IOException {
        String json = new String(Files.readAllBytes(file.toPath()));
        return fromJson(json);
    }

    // Audio association helper
    public File getAssociatedAudioFile(File storageDir) {
        return new File(storageDir, callId + "_" + timestamp + ".wav");
    }
}