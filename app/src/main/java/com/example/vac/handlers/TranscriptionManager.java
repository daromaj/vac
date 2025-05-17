package com.example.vac.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.vac.models.TranscriptionData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages saving and retrieving transcriptions with speaker information.
 */
public class TranscriptionManager {
    private static final String TAG = "TranscriptionManager";
    private static final String TRANSCRIPTIONS_FILE = "transcriptions.json";

    private final Context context;
    private final Gson gson;
    private final File transcriptionsFile;

    public TranscriptionManager(@NonNull Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.transcriptionsFile = new File(context.getFilesDir(), TRANSCRIPTIONS_FILE);
    }

    /**
     * Saves a transcription snippet with speaker information.
     *
     * @param callId The ID of the call
     * @param text The transcribed text
     * @param timestamp The timestamp of the transcription
     * @param speakerType The type of speaker
     * @return true if saved successfully, false otherwise
     */
    public boolean saveTranscriptionSnippet(@NonNull String callId, @NonNull String text, 
            long timestamp, @NonNull TranscriptionData.SpeakerType speakerType) {
        try {
            List<TranscriptionData> transcriptions = loadTranscriptions();
            transcriptions.add(new TranscriptionData(timestamp, text, callId, speakerType));
            return saveTranscriptions(transcriptions);
        } catch (IOException e) {
            Log.e(TAG, "Error saving transcription snippet", e);
            return false;
        }
    }

    /**
     * Gets all transcriptions for a specific call.
     *
     * @param callId The ID of the call
     * @return List of transcriptions for the call, sorted by timestamp
     */
    @NonNull
    public List<TranscriptionData> getTranscriptionForCall(@NonNull String callId) {
        try {
            List<TranscriptionData> transcriptions = loadTranscriptions();
            return transcriptions.stream()
                    .filter(t -> t.getCallId().equals(callId))
                    .sorted((t1, t2) -> Long.compare(t1.getTimestamp(), t2.getTimestamp()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, "Error getting transcriptions for call", e);
            return new ArrayList<>();
        }
    }

    /**
     * Searches transcriptions for a specific query.
     *
     * @param query The search query
     * @return List of matching transcriptions, sorted by timestamp
     */
    @NonNull
    public List<TranscriptionData> searchTranscriptions(@NonNull String query) {
        try {
            List<TranscriptionData> transcriptions = loadTranscriptions();
            String lowerQuery = query.toLowerCase();
            return transcriptions.stream()
                    .filter(t -> t.getText().toLowerCase().contains(lowerQuery))
                    .sorted((t1, t2) -> Long.compare(t1.getTimestamp(), t2.getTimestamp()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, "Error searching transcriptions", e);
            return new ArrayList<>();
        }
    }

    private List<TranscriptionData> loadTranscriptions() throws IOException {
        if (!transcriptionsFile.exists()) {
            return new ArrayList<>();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(transcriptionsFile))) {
            Type type = new TypeToken<List<TranscriptionData>>(){}.getType();
            List<TranscriptionData> transcriptions = gson.fromJson(reader, type);
            return transcriptions != null ? transcriptions : new ArrayList<>();
        }
    }

    private boolean saveTranscriptions(@NonNull List<TranscriptionData> transcriptions) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(transcriptionsFile))) {
            gson.toJson(transcriptions, writer);
            return true;
        }
    }
} 