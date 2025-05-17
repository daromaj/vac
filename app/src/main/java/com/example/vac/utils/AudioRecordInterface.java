package com.example.vac.utils;

public interface AudioRecordInterface {
    int getState();
    void startRecording();
    void stop();
    void release();
    int read(short[] audioData, int offsetInShorts, int sizeInShorts, int readMode);
}