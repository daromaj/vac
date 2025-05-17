package com.example.vac.handlers;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class AudioLevelMonitorTest {
    private AudioLevelMonitor audioLevelMonitor;
    private ShadowAudioRecord shadowAudioRecord;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        audioLevelMonitor = new AudioLevelMonitor(context);
        shadowAudioRecord = shadowOf(AudioRecord.class);
    }

    @Test
    public void testInitialState() {
        assertFalse("Should not be monitoring initially", audioLevelMonitor.isUserSpeaking());
        assertEquals("Initial level should be 0", 0.0f, audioLevelMonitor.getCurrentLevel(), 0.001f);
    }

    @Test
    public void testStartStopMonitoring() {
        // Start monitoring
        audioLevelMonitor.startMonitoring();
        assertTrue("AudioRecord should be initialized", shadowAudioRecord.getState() == AudioRecord.STATE_INITIALIZED);

        // Stop monitoring
        audioLevelMonitor.stopMonitoring();
        assertFalse("AudioRecord should be released", shadowAudioRecord.getState() == AudioRecord.STATE_INITIALIZED);
    }

    @Test
    public void testAudioLevelDetection() {
        audioLevelMonitor.startMonitoring();

        // Simulate low audio level
        shadowAudioRecord.setAudioData(new short[1024]); // Silent buffer
        assertFalse("Should not detect speaking at low level", audioLevelMonitor.isUserSpeaking());

        // Simulate high audio level
        short[] loudBuffer = new short[1024];
        for (int i = 0; i < loudBuffer.length; i++) {
            loudBuffer[i] = Short.MAX_VALUE; // Maximum amplitude
        }
        shadowAudioRecord.setAudioData(loudBuffer);
        assertTrue("Should detect speaking at high level", audioLevelMonitor.isUserSpeaking());

        audioLevelMonitor.stopMonitoring();
    }

    @Test
    public void testMultipleStartStop() {
        // Start monitoring multiple times
        audioLevelMonitor.startMonitoring();
        audioLevelMonitor.startMonitoring();
        assertTrue("Should still be monitoring after multiple starts", shadowAudioRecord.getState() == AudioRecord.STATE_INITIALIZED);

        // Stop monitoring
        audioLevelMonitor.stopMonitoring();
        assertFalse("Should be stopped after stop", shadowAudioRecord.getState() == AudioRecord.STATE_INITIALIZED);

        // Start again
        audioLevelMonitor.startMonitoring();
        assertTrue("Should be monitoring after restart", shadowAudioRecord.getState() == AudioRecord.STATE_INITIALIZED);
    }
} 