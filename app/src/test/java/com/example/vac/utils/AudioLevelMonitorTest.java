package com.example.vac.utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AudioLevelMonitorTest {
    @Mock
    private AudioRecordInterface mockAudioRecord;

    private AudioLevelMonitor monitor;

    @Before
    public void setUp() {
        monitor = new AudioLevelMonitor(mockAudioRecord);
    }

    @Test
    public void testStartAndStopMonitoring() {
        when(mockAudioRecord.getState()).thenReturn(1);  // 1 for STATE_INITIALIZED
        doNothing().when(mockAudioRecord).startRecording();
        monitor.startMonitoring();
        verify(mockAudioRecord).startRecording();

        doNothing().when(mockAudioRecord).stop();
        doNothing().when(mockAudioRecord).release();
        monitor.stopMonitoring();
        verify(mockAudioRecord).stop();
        verify(mockAudioRecord).release();
    }

    // No direct access to isRecording; use verify for behavior
}