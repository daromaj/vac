package com.example.vac.handlers;

import android.content.Context;
import android.telecom.Call;
import android.net.Uri;
import android.app.PendingIntent;

import com.example.vac.utils.PreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CallSessionManagerTest {

    @Mock
    private Context mockContext;
    @Mock
    private Call.Details mockCallDetails;
    @Mock
    private CallSessionManager.CallSessionListener mockSessionListener;
    @Mock
    private NotificationHandler mockNotificationHandler;
    @Mock
    private PreferencesManager mockPreferencesManager; // To be injected or mocked via context
    @Mock
    private PendingIntent mockTakeOverPendingIntent; // Added mock PendingIntent

    // We need to control the handlers CallSessionManager creates
    @Mock
    private AudioHandler mockAudioHandler;
    @Mock
    private SpeechRecognitionHandler mockSpeechRecognitionHandler;
    @Mock
    private MessageRecorderHandler mockMessageRecorderHandler;

    private CallSessionManager callSessionManager;

    private final String testUserName = "DarekTest";
    private final String expectedGreetingWithName = String.format("Hi, you've reached %s's phone. This is their virtual assistant. This call is being recorded. How can I help you?", testUserName);
    private final String expectedFallbackGreeting = "Hi, you've reached this phone. This is the virtual assistant. This call is being recorded. How can I help you?";

    @Before
    public void setUp() {
        // Removed unnecessary stub for getPackageName as PendingIntent creation is now mocked
        // when(mockContext.getPackageName()).thenReturn("com.example.vac.test"); 

        callSessionManager = new CallSessionManager(mockContext, mockCallDetails, mockSessionListener, mockNotificationHandler) {
            @Override
            protected PreferencesManager createPreferencesManager(Context context) {
                return mockPreferencesManager;
            }

            @Override
            protected AudioHandler createAudioHandler(Context context, AudioHandler.AudioHandlerListener listener) {
                return mockAudioHandler;
            }

            @Override
            protected SpeechRecognitionHandler createSpeechRecognitionHandler(Context context, SpeechRecognitionHandler.SpeechRecognitionCallbacks callbacks) {
                return mockSpeechRecognitionHandler;
            }

            @Override
            protected MessageRecorderHandler createMessageRecorderHandler(Context context, MessageRecorderHandler.MessageRecorderListener listener) {
                return mockMessageRecorderHandler;
            }

            @Override // Added override for PendingIntent creation
            protected PendingIntent createTakeOverPendingIntent() {
                return mockTakeOverPendingIntent;
            }
        };
        
        // callSessionManager.initializeComponents(); // This would be called internally, setting the handlers.
        // Ensure the call to initializeComponents happens in the constructor of CallSessionManager so mocks are used.
    }

    @Test
    public void test_startGreeting_constructsCorrectGreeting_withUserName() {
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        
        callSessionManager.startGreeting();
        
        ArgumentCaptor<String> greetingCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockAudioHandler).playGreeting(greetingCaptor.capture());
        assertEquals(expectedGreetingWithName, greetingCaptor.getValue());
    }

    @Test
    public void test_startGreeting_constructsCorrectFallbackGreeting_withoutUserName() {
        when(mockPreferencesManager.getUserName()).thenReturn(null); // Or empty
        callSessionManager.startGreeting();
        ArgumentCaptor<String> greetingCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockAudioHandler).playGreeting(greetingCaptor.capture());
        assertEquals(expectedFallbackGreeting, greetingCaptor.getValue());

        reset(mockAudioHandler); // Reset for next call
        when(mockPreferencesManager.getUserName()).thenReturn("  "); // Empty after trim
        callSessionManager.startGreeting();
        verify(mockAudioHandler).playGreeting(greetingCaptor.capture());
        assertEquals(expectedFallbackGreeting, greetingCaptor.getValue());
    }

    @Test
    public void test_startGreeting_callsAudioHandlerPlayGreeting() {
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        callSessionManager.startGreeting();
        verify(mockAudioHandler).playGreeting(expectedGreetingWithName);
    }

    @Test
    public void test_onPlaybackCompleted_afterGreeting_startsListening() {
        // Simulate state being GREETING when playback completes
        // Need to set internal state for this test, or ensure startGreeting sets it.
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        callSessionManager.startGreeting(); // This should set state to GREETING
        
        // Manually trigger onPlaybackCompleted (as AudioHandler would)
        callSessionManager.onPlaybackCompleted();
        
        verify(mockSpeechRecognitionHandler).startListening("pl-PL");
    }

    @Test
    public void test_onPlaybackError_notifiesListenerAndStopsSession() {
        String errorMessage = "TTS failed badly";
        callSessionManager.onPlaybackError(errorMessage); // Manually trigger
        
        verify(mockSessionListener).onSessionError(eq(callSessionManager), eq("Audio playback error: " + errorMessage));
        // Verify stopScreening was called (indirectly, by checking its effects e.g. handler releases)
        verify(mockAudioHandler).release(); // Assuming stopScreening calls release on handlers
        verify(mockSpeechRecognitionHandler).release();
        verify(mockMessageRecorderHandler).release();
    }
    
    @Test
    public void startScreening_showsNotificationAndStartsGreeting() {
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        callSessionManager.startScreening();
        
        verify(mockNotificationHandler).showScreeningNotification(eq("Starting call assistant..."), any());
        verify(mockAudioHandler).playGreeting(expectedGreetingWithName);
    }
} 