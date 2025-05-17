package com.example.vac.handlers;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.vac.models.TranscriptionData;
import com.example.vac.models.TranscriptionData.SpeakerType;

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
    private Context context;
    private File transcriptionsFile;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        transcriptionManager = new TranscriptionManager(context);
        transcriptionsFile = new File(context.getFilesDir(), "transcriptions.json");
        if (transcriptionsFile.exists()) {
            transcriptionsFile.delete();
        }
    }

    @Test
    public void testSaveTranscriptionSnippet() {
        String callId = "test-call-1";
        String text = "Hello, this is a test";
        long timestamp = System.currentTimeMillis();
        SpeakerType speakerType = SpeakerType.CALLER;

        boolean saved = transcriptionManager.saveTranscriptionSnippet(callId, text, timestamp, speakerType);
        assertTrue("Should save transcription successfully", saved);
        assertTrue("Transcriptions file should exist", transcriptionsFile.exists());
    }

    @Test
    public void testGetTranscriptionForCall() {
        // Save multiple transcriptions for different calls
        String callId1 = "test-call-1";
        String callId2 = "test-call-2";
        
        transcriptionManager.saveTranscriptionSnippet(callId1, "First message", 1000, SpeakerType.CALLER);
        transcriptionManager.saveTranscriptionSnippet(callId2, "Second message", 2000, SpeakerType.USER);
        transcriptionManager.saveTranscriptionSnippet(callId1, "Third message", 3000, SpeakerType.ASSISTANT);

        // Get transcriptions for callId1
        List<TranscriptionData> transcriptions = transcriptionManager.getTranscriptionForCall(callId1);
        assertEquals("Should return correct number of transcriptions", 2, transcriptions.size());
        assertEquals("First transcription should be from caller", SpeakerType.CALLER, transcriptions.get(0).getSpeakerType());
        assertEquals("Second transcription should be from assistant", SpeakerType.ASSISTANT, transcriptions.get(1).getSpeakerType());
    }

    @Test
    public void testSearchTranscriptions() {
        // Save test transcriptions
        transcriptionManager.saveTranscriptionSnippet("call-1", "Hello world", 1000, SpeakerType.CALLER);
        transcriptionManager.saveTranscriptionSnippet("call-2", "Goodbye world", 2000, SpeakerType.USER);
        transcriptionManager.saveTranscriptionSnippet("call-3", "Hello there", 3000, SpeakerType.ASSISTANT);

        // Search for "hello"
        List<TranscriptionData> results = transcriptionManager.searchTranscriptions("hello");
        assertEquals("Should find 2 matching transcriptions", 2, results.size());
        assertTrue("Results should be sorted by timestamp", 
            results.get(0).getTimestamp() < results.get(1).getTimestamp());
    }

    @Test
    public void testEmptyTranscriptions() {
        List<TranscriptionData> transcriptions = transcriptionManager.getTranscriptionForCall("non-existent");
        assertTrue("Should return empty list for non-existent call", transcriptions.isEmpty());

        List<TranscriptionData> searchResults = transcriptionManager.searchTranscriptions("test");
        assertTrue("Should return empty list for no matches", searchResults.isEmpty());
    }

    @Test
    public void testTranscriptionOrdering() {
        // Save transcriptions out of order
        transcriptionManager.saveTranscriptionSnippet("call-1", "Third", 3000, SpeakerType.CALLER);
        transcriptionManager.saveTranscriptionSnippet("call-1", "First", 1000, SpeakerType.USER);
        transcriptionManager.saveTranscriptionSnippet("call-1", "Second", 2000, SpeakerType.ASSISTANT);

        List<TranscriptionData> transcriptions = transcriptionManager.getTranscriptionForCall("call-1");
        assertEquals("Should return correct number of transcriptions", 3, transcriptions.size());
        assertEquals("First transcription should be 'First'", "First", transcriptions.get(0).getText());
        assertEquals("Second transcription should be 'Second'", "Second", transcriptions.get(1).getText());
        assertEquals("Third transcription should be 'Third'", "Third", transcriptions.get(2).getText());
    }
} 