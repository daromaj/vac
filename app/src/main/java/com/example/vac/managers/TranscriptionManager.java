package com.example.vac.managers;

import com.example.vac.models.TranscriptionData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TranscriptionManager {
    private static final String TRANSCRIPTIONS_FILE = "transcriptions.json";
    private final File storageDir;
    private final Gson gson = new Gson();

    public TranscriptionManager(File storageDir) {
        this.storageDir = storageDir;
    }

    public void saveTranscription(TranscriptionData transcription) throws IOException {
        List<TranscriptionData> transcriptions = loadTranscriptions();
        transcriptions.add(transcription);
        writeTranscriptions(transcriptions);
    }

    public List<TranscriptionData> getTranscriptionsForCall(String callId) throws IOException {
        List<TranscriptionData> allTranscriptions = loadTranscriptions();
        List<TranscriptionData> filtered = new ArrayList<>();
        for (TranscriptionData t : allTranscriptions) {
            if (t.getCallId().equals(callId)) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    private List<TranscriptionData> loadTranscriptions() throws IOException {
        File file = new File(storageDir, TRANSCRIPTIONS_FILE);
        if (file.exists()) {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            Type listType = new TypeToken<ArrayList<TranscriptionData>>(){}.getType();
            return gson.fromJson(json, listType);
        } else {
            return new ArrayList<>();
        }
    }

    private void writeTranscriptions(List<TranscriptionData> transcriptions) throws IOException {
        File file = new File(storageDir, TRANSCRIPTIONS_FILE);
        String json = gson.toJson(transcriptions);
        java.nio.file.Files.write(file.toPath(), json.getBytes());
    }

    // Additional method for audio association
    public File getAudioFileForTranscription(TranscriptionData transcription) {
        return transcription.getAssociatedAudioFile(storageDir);
    }

    private static TranscriptionManager instance;
    private static File defaultStorageDir = new File(System.getProperty("java.io.tmpdir"), "vac_transcriptions");

    public static TranscriptionManager getInstance() {
        if (instance == null) {
            instance = new TranscriptionManager(defaultStorageDir);
        }
        return instance;
    }
}