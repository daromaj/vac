package com.example.vac.handlers;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.example.vac.models.TranscriptionData;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class TranscriptionManagerTest {
    private TranscriptionManager transcriptionManager;
    private File testFile;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        transcriptionManager = new TranscriptionManager(context);
        testFile = new File(context.getFilesDir(), "transcriptions.json");
        if (testFile.exists()) {
            testFile.delete();
        }
    }

    @Test
    public void testSaveAndRetrieveTranscription() {
        // Save a transcription
        String callId = "test-call-1";
        String text = "Hello, this is a test";
        long timestamp = System.currentTimeMillis();
        TranscriptionData.SpeakerType speakerType = TranscriptionData.SpeakerType.CALLER;

        boolean saved = transcriptionManager.saveTranscriptionSnippet(callId, text, timestamp, speakerType);
        assertTrue("Transcription should be saved successfully", saved);

        // Retrieve the transcription
        List<TranscriptionData> transcriptions = transcriptionManager.getTranscriptionForCall(callId);
        assertEquals("Should retrieve exactly one transcription", 1, transcriptions.size());

        TranscriptionData retrieved = transcriptions.get(0);
        assertEquals("Call ID should match", callId, retrieved.getCallId());
        assertEquals("Text should match", text, retrieved.getText());
        assertEquals("Timestamp should match", timestamp, retrieved.getTimestamp());
        assertEquals("Speaker type should match", speakerType, retrieved.getSpeakerType());
    }

    @Test
    public void testSearchTranscriptions() {
        // Save multiple transcriptions
        String callId1 = "test-call-1";
        String callId2 = "test-call-2";
        
        transcriptionManager.saveTranscriptionSnippet(callId1, "Hello world", System.currentTimeMillis(), TranscriptionData.SpeakerType.CALLER);
        transcriptionManager.saveTranscriptionSnippet(callId2, "Goodbye world", System.currentTimeMillis(), TranscriptionData.SpeakerType.USER);

        // Search for "world"
        List<TranscriptionData> results = transcriptionManager.searchTranscriptions("world");
        assertEquals("Should find two transcriptions containing 'world'", 2, results.size());

        // Search for "hello"
        results = transcriptionManager.searchTranscriptions("hello");
        assertEquals("Should find one transcription containing 'hello'", 1, results.size());
        assertEquals("Should match the first call", callId1, results.get(0).getCallId());

        // Search for non-existent text
        results = transcriptionManager.searchTranscriptions("nonexistent");
        assertTrue("Should return empty list for non-existent text", results.isEmpty());
    }

    @Test
    public void testEmptyTranscriptions() {
        String callId = "non-existent-call";
        List<TranscriptionData> transcriptions = transcriptionManager.getTranscriptionForCall(callId);
        assertTrue("Should return empty list for non-existent call", transcriptions.isEmpty());
    }
} 