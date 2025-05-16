package com.example.vac.handlers;

import android.content.Context;
import android.os.Looper; // Ensure this is present for ShadowLooper
import android.speech.SpeechRecognizer; // Import for SpeechRecognizer.ERROR_CLIENT
import android.telecom.Call;
import android.net.Uri;
import android.app.PendingIntent;

import com.example.vac.R; // For R.string.default_greeting
import com.example.vac.utils.PreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper; // Import for controlling Handler posts

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk = Config.NEWEST_SDK) // Added SDK for consistency, though might not be strictly needed for these tests
public class CallSessionManagerTest {

    @Mock private Context mockContext;
    @Mock private Call.Details mockCallDetails;
    @Mock private CallSessionManager.CallSessionListener mockSessionListener;
    @Mock private NotificationHandler mockNotificationHandler;
    @Mock private AudioHandler mockAudioHandler;
    @Mock private PreferencesManager mockPreferencesManager;
    @Mock private SpeechRecognitionHandler mockSpeechRecognitionHandler;
    @Mock private MessageRecorderHandler mockMessageRecorderHandler;

    private CallSessionManager callSessionManager;
    private String defaultGreetingFormatString = "Witaj, dodzwoniłeś się do %1$s. Jestem jego wirtualnym asystentem. Uprzedzam, że rozmowa jest nagrywana. Powiedz proszę w jakiej sprawie dzwonisz a ja postaram Ci się pomóc.";
    private static final String POLISH_RECORDING_NOTICE = " Ta rozmowa jest nagrywana.";
    private static final String POLISH_RECORDING_KEYPHRASE = "rozmowa jest nagrywana";
    private static final long STT_SILENCE_TIMEOUT_MS_TEST = 3000; // Match constant in SUT

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock context to return a real application context for resource loading if needed,
        // but primarily, we'll mock specific calls like getString.
        // We need a real context for getString in the code under test.
        // Let's use Robolectric's application context but ensure getString is handled if directly called on mockContext.
        Context realContext = RuntimeEnvironment.getApplication();
        when(mockContext.getApplicationContext()).thenReturn(realContext);
        when(mockContext.getString(R.string.default_greeting)).thenReturn(defaultGreetingFormatString);
        // Ensure Looper is prepared for the sttTimeoutHandler if not already by default in Robolectric
        // ShadowLooper.pauseMainLooper(); // Might be useful to control execution precisely

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
        };
    }

    // Test Scenarios for Task 4.1.4

    @Test
    public void test_callScreeningUsesGeneratedFile_whenEnabledAndFileExists() throws IOException {
        String fakeFilePath = new File(RuntimeEnvironment.getApplication().getFilesDir(), "test_greeting.wav").getAbsolutePath();
        File dummyFile = new File(fakeFilePath);
        dummyFile.createNewFile(); // Ensure file exists for the test
        dummyFile.deleteOnExit(); // Clean up

        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(true);
        when(mockPreferencesManager.getCustomGreetingFilePath()).thenReturn(fakeFilePath);

        callSessionManager.startGreeting();

        verify(mockAudioHandler, times(1)).playAudioFile(eq(Uri.fromFile(dummyFile)));
        verify(mockAudioHandler, never()).playGreeting(anyString());
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
    }

    @Test
    public void test_callScreeningUsesTTS_whenGeneratedFileDisabled() {
        String testUserName = "Darek";
        String testBaseGreeting = "Hi there!"; // Custom greeting, does not contain Polish notice
        String expectedTTSGreeting = testBaseGreeting + POLISH_RECORDING_NOTICE;

        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(false);
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        when(mockPreferencesManager.getGreetingText()).thenReturn(testBaseGreeting);

        callSessionManager.startGreeting();

        verify(mockAudioHandler, times(1)).playGreeting(eq(expectedTTSGreeting));
        verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
    }
    
    @Test
    public void test_callScreeningUsesTTS_whenGeneratedFileDisabled_defaultGreetingUsed() {
        String testUserName = "Darek";
        String expectedTTSGreeting = String.format(defaultGreetingFormatString, testUserName);

        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(false);
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        when(mockPreferencesManager.getGreetingText()).thenReturn(""); // Empty base greeting

        callSessionManager.startGreeting();

        verify(mockAudioHandler, times(1)).playGreeting(eq(expectedTTSGreeting));
        verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
    }


    @Test
    public void test_callScreeningUsesTTS_whenGeneratedFileEnabledButFileMissing() {
        String fakeFilePath = new File(RuntimeEnvironment.getApplication().getFilesDir(), "missing_greeting.wav").getAbsolutePath();
        File missingFile = new File(fakeFilePath);
        if (missingFile.exists()) {
            missingFile.delete();
        }

        String testUserName = "Tester";
        String expectedTTSGreeting = String.format(defaultGreetingFormatString, testUserName);

        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(true);
        when(mockPreferencesManager.getCustomGreetingFilePath()).thenReturn(fakeFilePath);
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        when(mockPreferencesManager.getGreetingText()).thenReturn(null); // Or empty, for default TTS

        callSessionManager.startGreeting();

        verify(mockAudioHandler, times(1)).playGreeting(eq(expectedTTSGreeting));
        verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
    }

     @Test
    public void test_callScreeningUsesTTS_whenGeneratedFileEnabledButFilePathNull() {
        String testUserName = "PathUser";
        String expectedTTSGreeting = String.format(defaultGreetingFormatString, testUserName);

        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(true);
        when(mockPreferencesManager.getCustomGreetingFilePath()).thenReturn(null); // Null path
        when(mockPreferencesManager.getUserName()).thenReturn(testUserName);
        when(mockPreferencesManager.getGreetingText()).thenReturn(""); 

        callSessionManager.startGreeting();

        verify(mockAudioHandler, times(1)).playGreeting(eq(expectedTTSGreeting));
        verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
    }
    
    // Helper to access currentState if it's not public (it should be for testing or have a getter)
    // For now, assuming we can directly access or add a getter if needed.
    // public CallSessionManager.State getCurrentStateDirectly() { return callSessionManager.currentState; }

    @Test
    public void test_afterGreetingPlaybackCompletes_startsListeningForCaller() {
        // Arrange: Use TTS for greeting for simplicity
        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(false);
        when(mockPreferencesManager.getUserName()).thenReturn("TestUser");
        when(mockPreferencesManager.getGreetingText()).thenReturn("Hello");

        // Act 1: Start the greeting
        callSessionManager.startGreeting();
        assertEquals("State should be GREETING after starting greeting", 
                     CallSessionManager.State.GREETING, callSessionManager.getCurrentState());
        verify(mockAudioHandler).playGreeting(anyString()); // Verify greeting started

        // Act 2: Simulate audio playback completion
        callSessionManager.onPlaybackCompleted();

        // Assert
        verify(mockSpeechRecognitionHandler, times(1)).startListening(eq("pl-PL"));
        assertEquals("State should be LISTENING after greeting playback completes", 
                     CallSessionManager.State.LISTENING, callSessionManager.getCurrentState());
        verify(mockNotificationHandler, times(1)).updateNotification(eq("Listening to caller..."));
    }

    // --- Tests for STT Silence Timeout Logic (Task 5.2) ---

    private void setupSessionForListeningState() {
        // Reach GREETING state
        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(false);
        callSessionManager.startGreeting();
        // Simulate greeting playback completion to reach LISTENING state
        callSessionManager.onPlaybackCompleted();
        // Verify we are in LISTENING state and STT started
        assertEquals(CallSessionManager.State.LISTENING, callSessionManager.getCurrentState());
        verify(mockSpeechRecognitionHandler).startListening(eq("pl-PL"));
        // Clear interactions that happened during setup to focus on the specific test part
        org.mockito.Mockito.clearInvocations(mockAudioHandler, mockNotificationHandler, mockSpeechRecognitionHandler);
    }

    @Test
    public void test_onEndOfSpeech_startsTimeout_thenTimeoutOccurs_playsFollowUp() {
        setupSessionForListeningState();

        // Act: Simulate end of speech
        callSessionManager.onEndOfSpeech();
        verify(mockAudioHandler, never()).playFollowUpResponse(); // Should not play immediately

        // Act: Advance time beyond timeout
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response is played
        verify(mockAudioHandler, times(1)).playFollowUpResponse();
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());
    }

    @Test
    public void test_onEndOfSpeech_thenSpeechResult_cancelsTimeout_noFollowUp() {
        setupSessionForListeningState();

        // Act: Simulate end of speech - timeout is now scheduled
        callSessionManager.onEndOfSpeech();

        // Act: Simulate speech result before timeout
        callSessionManager.onSpeechResult("Some recognized text");
        verify(mockSessionListener).onTranscriptionUpdate("Some recognized text");

        // Act: Advance time beyond original timeout duration
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response is NOT played because speech result cancelled it
        verify(mockAudioHandler, never()).playFollowUpResponse();
        // State should remain LISTENING as onSpeechResult doesn't change it directly in this flow
        assertEquals(CallSessionManager.State.LISTENING, callSessionManager.getCurrentState());
    }

    @Test
    public void test_onEndOfSpeech_thenSpeechError_cancelsTimeout_playsFollowUpOnError() {
        setupSessionForListeningState();

        // Act: Simulate end of speech - timeout is now scheduled
        callSessionManager.onEndOfSpeech();

        // Act: Simulate speech error before timeout
        callSessionManager.onSpeechError("Test error", SpeechRecognizer.ERROR_CLIENT);
        // Current onSpeechError logic in LISTENING state directly calls playFollowUpResponse()
        verify(mockAudioHandler, times(1)).playFollowUpResponse(); 
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());

        // Act: Advance time beyond original timeout duration
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response should only have been played once (due to error handling)
        verify(mockAudioHandler, times(1)).playFollowUpResponse(); 
    }

    @Test
    public void test_onEndOfSpeech_thenUserTakesOver_cancelsTimeout_noFollowUp() {
        setupSessionForListeningState();

        // Act: Simulate end of speech - timeout is now scheduled
        callSessionManager.onEndOfSpeech();

        // Act: User takes over before timeout
        callSessionManager.userTakesOver();
        verify(mockSessionListener).onUserTookOver(callSessionManager);

        // Act: Advance time beyond original timeout duration
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response is NOT played
        verify(mockAudioHandler, never()).playFollowUpResponse();
        assertEquals(CallSessionManager.State.USER_TAKEOVER, callSessionManager.getCurrentState());
    }

    @Test
    public void test_onEndOfSpeech_thenStopScreening_cancelsTimeout_noFollowUp() {
        setupSessionForListeningState();

        // Act: Simulate end of speech - timeout is now scheduled
        callSessionManager.onEndOfSpeech();

        // Act: Stop screening before timeout
        callSessionManager.stopScreening(false);

        // Act: Advance time beyond original timeout duration
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response is NOT played
        verify(mockAudioHandler, never()).playFollowUpResponse();
        assertEquals(CallSessionManager.State.ENDED, callSessionManager.getCurrentState());
    }

    @Test
    public void test_multipleOnEndOfSpeech_resetsTimeoutCorrectly() {
        setupSessionForListeningState();

        // Act: Simulate first end of speech
        callSessionManager.onEndOfSpeech();
        verify(mockAudioHandler, never()).playFollowUpResponse();

        // Act: Advance time part way (less than timeout)
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST / 2, java.util.concurrent.TimeUnit.MILLISECONDS);
        verify(mockAudioHandler, never()).playFollowUpResponse(); // Still shouldn't have played

        // Act: Simulate another end of speech (e.g. from continuous recognition)
        callSessionManager.onEndOfSpeech(); 
        verify(mockAudioHandler, never()).playFollowUpResponse(); // Still shouldn't have played

        // Act: Advance time beyond the original timeout from the *second* onEndOfSpeech call
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert: Follow-up response is played ONCE
        verify(mockAudioHandler, times(1)).playFollowUpResponse();
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());
    }
} 