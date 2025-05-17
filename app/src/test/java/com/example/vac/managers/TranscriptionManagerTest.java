package com.example.vac.managers;

import com.example.vac.models.TranscriptionData;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class TranscriptionManagerTest {
    private File tempDir;
    private TranscriptionManager manager;

    @Before
    public void setUp() throws IOException {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "test_transcriptions");
        if (tempDir.exists()) {
            java.nio.file.Files.deleteIfExists(new File(tempDir, "transcriptions.json").toPath());
        }
        tempDir.mkdirs();  // Use mkdirs() to ensure parent directories are created if needed
        manager = new TranscriptionManager(tempDir);
    }

    @Test
    public void test_saveTranscription() throws IOException {
        TranscriptionData data = new TranscriptionData(1627849200000L, "Test save", "call001", TranscriptionData.SpeakerType.ASSISTANT);
        manager.saveTranscription(data);
        
        List<TranscriptionData> transcriptions = manager.getTranscriptionsForCall("call001");
        assertEquals(1, transcriptions.size());
        assertEquals("Test save", transcriptions.get(0).getText());
    }

    @Test
    public void test_getTranscriptionsForCall() throws IOException {
        TranscriptionData data1 = new TranscriptionData(1627849200000L, "First transcription", "call002", TranscriptionData.SpeakerType.USER);
        TranscriptionData data2 = new TranscriptionData(1627849300000L, "Second transcription", "call002", TranscriptionData.SpeakerType.CALLER);
        TranscriptionData data3 = new TranscriptionData(1627849400000L, "Unrelated", "call003", TranscriptionData.SpeakerType.ASSISTANT);
        
        manager.saveTranscription(data1);
        manager.saveTranscription(data2);
        manager.saveTranscription(data3);
        
        List<TranscriptionData> transcriptions = manager.getTranscriptionsForCall("call002");
        assertEquals(2, transcriptions.size());
        assertTrue(transcriptions.stream().anyMatch(t -> t.getText().equals("First transcription")));
        assertTrue(transcriptions.stream().anyMatch(t -> t.getText().equals("Second transcription")));
    }

    @Test
    public void test_transcriptionAudioAssociation() throws IOException {
        TranscriptionData data = new TranscriptionData(1627849200000L, "Test audio association", "call004", TranscriptionData.SpeakerType.CALLER);
        File audioFile = manager.getAudioFileForTranscription(data);
        assertEquals(new File(tempDir, "call004_1627849200000.wav"), audioFile);
    }
}