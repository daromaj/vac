package com.example.vac.models;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model class for recorded message files
 */
public class Message {
    
    private final File file;
    private final String filename;
    private final String formattedDate;
    
    /**
     * Constructor for a Message
     * 
     * @param file The audio file containing the recorded message
     */
    public Message(File file) {
        this.file = file;
        this.filename = file.getName();
        
        // Extract timestamp from filename (format: message_[timestamp].3gp)
        long timestamp = extractTimestampFromFilename(filename);
        this.formattedDate = formatTimestamp(timestamp);
    }
    
    /**
     * Get the audio file
     * 
     * @return The File object for the audio recording
     */
    public File getFile() {
        return file;
    }
    
    /**
     * Get the filename
     * 
     * @return The filename of the audio recording
     */
    public String getFilename() {
        return filename;
    }
    
    /**
     * Get the formatted date
     * 
     * @return The formatted date string
     */
    public String getFormattedDate() {
        return formattedDate;
    }
    
    /**
     * Format a timestamp as a readable date
     * 
     * @param timestamp The timestamp to format
     * @return The formatted date string
     */
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * Extract the timestamp from a filename
     * 
     * @param filename The filename (format: message_[timestamp].3gp)
     * @return The extracted timestamp, or current time if extraction fails
     */
    private long extractTimestampFromFilename(String filename) {
        try {
            // Extract timestamp from message_1234567890.3gp format
            String[] parts = filename.split("_");
            if (parts.length >= 2) {
                String timestampStr = parts[1].split("\\.")[0];
                return Long.parseLong(timestampStr);
            }
        } catch (Exception e) {
            // If parsing fails, just return current time
        }
        
        return System.currentTimeMillis();
    }
} 