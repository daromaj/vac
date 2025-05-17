package com.example.vac.utils;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpeakerIdentifierTest {
    @Mock
    private AudioLevelMonitor mockAudioLevelMonitor;
    @Mock
    private Context mockContext;

    private SpeakerIdentifier speakerIdentifier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        speakerIdentifier = new SpeakerIdentifier(mockAudioLevelMonitor);
    }

    @Test
    public void testIdentifySpeaker_AssistantSpeaking() {
        speakerIdentifier.setAssistantSpeaking(true);
        SpeakerIdentifier.SpeakerType result = speakerIdentifier.identifySpeaker("Test text", System.currentTimeMillis(), 0.5f);
        assertEquals(SpeakerIdentifier.SpeakerType.ASSISTANT, result);
    }

    @Test
    public void testIdentifySpeaker_UserSpeaking() {
        speakerIdentifier.setAssistantSpeaking(false);
        when(mockAudioLevelMonitor.isUserSpeaking(50.0f)).thenReturn(true);  // Match the threshold in SpeakerIdentifier.java
        SpeakerIdentifier.SpeakerType result = speakerIdentifier.identifySpeaker("Test text", System.currentTimeMillis(), 0.5f);
        System.out.println("Debug: Actual result = " + result);  // Add debug print for the actual value
        assertEquals(SpeakerIdentifier.SpeakerType.USER, result);  // Keep assertion but add debug
        // Duplicate call removed to fix assertion error
        // Removed duplicate assertion
    }

    @Test
    public void testIdentifySpeaker_CallerSpeaking() {
        speakerIdentifier.setAssistantSpeaking(false);
        SpeakerIdentifier.SpeakerType result = speakerIdentifier.identifySpeaker("Test text", System.currentTimeMillis(), 0.5f);
        assertEquals(SpeakerIdentifier.SpeakerType.CALLER, result);
    }

    @Test
    public void testIsAssistantSpeaking() {
        speakerIdentifier.setAssistantSpeaking(true);
        assertTrue(speakerIdentifier.isAssistantSpeaking());
        speakerIdentifier.setAssistantSpeaking(false);
        assertFalse(speakerIdentifier.isAssistantSpeaking());
    }

    @Test
    public void testIsUserTakeOverActive() {
        speakerIdentifier.setUserTakeOverActive(true);
        assertTrue(speakerIdentifier.isUserTakeOverActive());
        speakerIdentifier.setUserTakeOverActive(false);
        assertFalse(speakerIdentifier.isUserTakeOverActive());
    }

    @Test
    public void testHandleTransition() {
        // This is more of a logging method, so we'll verify it doesn't crash
        speakerIdentifier.handleTransition();  // No assertions needed, just ensure it runs
    }
}