package com.example.vac.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import com.example.vac.models.TranscriptionData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages saving and retrieving transcriptions.
 */
public class TranscriptionManager {
    private static final String TAG = "TranscriptionManager";
    private static final String TRANSCRIPTIONS_DIR = "transcriptions";
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
     * Saves a transcription snippet.
     *
     * @param callId The ID of the call
     * @param text The transcribed text
     * @param timestamp The timestamp of the transcription
     * @param speakerType The type of speaker
     * @return true if saved successfully, false otherwise
     */
    public boolean saveTranscriptionSnippet(@NonNull String callId, @NonNull String text, long timestamp, @NonNull TranscriptionData.SpeakerType speakerType) {
        try {
            List<TranscriptionData> transcriptions = loadTranscriptions();
            transcriptions.add(new TranscriptionData(timestamp, text, callId, speakerType));
            saveTranscriptions(transcriptions);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving transcription snippet", e);
            return false;
        }
    }

    /**
     * Gets all transcriptions for a specific call.
     *
     * @param callId The ID of the call
     * @return List of transcriptions for the call
     */
    @NonNull
    public List<TranscriptionData> getTranscriptionForCall(@NonNull String callId) {
        try {
            List<TranscriptionData> allTranscriptions = loadTranscriptions();
            return allTranscriptions.stream()
                    .filter(t -> t.getCallId().equals(callId))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, "Error getting transcriptions for call: " + callId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Searches through all transcriptions for matching text.
     *
     * @param query The search query
     * @return List of matching transcriptions
     */
    @NonNull
    public List<TranscriptionData> searchTranscriptions(@NonNull String query) {
        try {
            List<TranscriptionData> allTranscriptions = loadTranscriptions();
            String lowerQuery = query.toLowerCase();
            return allTranscriptions.stream()
                    .filter(t -> t.getText().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.e(TAG, "Error searching transcriptions", e);
            return new ArrayList<>();
        }
    }

    @NonNull
    private List<TranscriptionData> loadTranscriptions() throws IOException {
        if (!transcriptionsFile.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(transcriptionsFile)) {
            Type type = new TypeToken<List<TranscriptionData>>(){}.getType();
            List<TranscriptionData> transcriptions = gson.fromJson(reader, type);
            return transcriptions != null ? transcriptions : new ArrayList<>();
        }
    }

    private void saveTranscriptions(@NonNull List<TranscriptionData> transcriptions) throws IOException {
        try (FileWriter writer = new FileWriter(transcriptionsFile)) {
            gson.toJson(transcriptions, writer);
        }
    }
} 