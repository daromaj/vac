package com.example.vac.models;

import java.io.File;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;

public class Message {
    private File file;
    private List<TranscriptionData> transcriptions;  // Field for transcriptions

    public Message(File file) {
        this.file = file;
        this.transcriptions = new ArrayList<>();  // Default empty list
    }

    public Message(File file, List<TranscriptionData> transcriptions) {
        this.file = file;
        this.transcriptions = transcriptions;
    }

    public File getFile() {
        return file;
    }

    public String getFilename() {
        return file.getName();
    }

    public List<TranscriptionData> getTranscriptions() {
        return transcriptions;
    }

    public String getFormattedDate() {  // Changed to return String
        try {
            String filename = file.getName();
            String timestampStr = filename.split("_")[1].split("\\.")[0];  // Extract timestamp
            long timestamp = Long.parseLong(timestampStr) * 1000;  // Convert to milliseconds
            Date date = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(date);
        } catch (Exception e) {
            return "Unknown date";
        }
    }
}
