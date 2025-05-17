package com.example.vac.handlers;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.vac.models.TranscriptionData.SpeakerType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class SpeakerIdentifierTest {
    private SpeakerIdentifier speakerIdentifier;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        speakerIdentifier = new SpeakerIdentifier(context);
    }

    @Test
    public void testInitialState() {
        assertFalse("Assistant should not be speaking initially", speakerIdentifier.isAssistantSpeaking());
        assertFalse("User should not have taken over initially", speakerIdentifier.isUserTakeOverActive());
    }

    @Test
    public void testAssistantSpeaking() {
        speakerIdentifier.setAssistantSpeaking(true);
        assertEquals("Should identify assistant when speaking", 
            SpeakerType.ASSISTANT, 
            speakerIdentifier.identifySpeaker("test", System.currentTimeMillis(), 0.0f));
    }

    @Test
    public void testUserTakeOver() {
        speakerIdentifier.setUserTakeOverActive(true);
        assertEquals("Should identify user when take-over is active", 
            SpeakerType.USER, 
            speakerIdentifier.identifySpeaker("test", System.currentTimeMillis(), 0.0f));
    }

    @Test
    public void testUserSpeaking() {
        // Simulate high local mic level
        assertEquals("Should identify user when local mic level is high", 
            SpeakerType.USER, 
            speakerIdentifier.identifySpeaker("test", System.currentTimeMillis(), 0.8f));
    }

    @Test
    public void testCallerSpeaking() {
        // Simulate low local mic level but audio detected
        assertEquals("Should identify caller when audio detected but not from local mic", 
            SpeakerType.CALLER, 
            speakerIdentifier.identifySpeaker("test", System.currentTimeMillis(), 0.1f));
    }

    @Test
    public void testStateTransitions() {
        // Test assistant speaking state
        speakerIdentifier.setAssistantSpeaking(true);
        assertTrue("Assistant should be speaking", speakerIdentifier.isAssistantSpeaking());
        
        // Test user take-over state
        speakerIdentifier.setUserTakeOverActive(true);
        assertTrue("User should have taken over", speakerIdentifier.isUserTakeOverActive());
        
        // Test state reset
        speakerIdentifier.setAssistantSpeaking(false);
        speakerIdentifier.setUserTakeOverActive(false);
        assertFalse("Assistant should not be speaking", speakerIdentifier.isAssistantSpeaking());
        assertFalse("User should not have taken over", speakerIdentifier.isUserTakeOverActive());
    }
} 