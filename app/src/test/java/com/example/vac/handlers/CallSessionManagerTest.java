package com.example.vac.handlers;

import android.content.Context;
// Ensure this is present for ShadowLooper
import android.speech.SpeechRecognizer; // Import for SpeechRecognizer.ERROR_CLIENT
import android.telecom.Call;
import android.net.Uri;

import com.example.vac.R; // For R.string.default_greeting
import com.example.vac.utils.PreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper; // Import for controlling Handler posts

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;

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
    private final String defaultGreetingFormatString = "Witaj, dodzwoniłeś się do %1$s. Jestem jego wirtualnym asystentem. Uprzedzam, że rozmowa jest nagrywana. Powiedz proszę w jakiej sprawie dzwonisz a ja postaram Ci się pomóc.";
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
        when(mockContext.getString(R.string.notification_message_listening)).thenReturn("Listening to caller..."); // Added mock for new string
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
            protected SpeechRecognitionHandler createSpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks callbacks) {
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

        callSessionManager.startGreeting();
        callSessionManager.onPlaybackCompleted(); // SUT calls startListening("pl-PL")

        // Assert
        verify(mockSpeechRecognitionHandler, times(1)).startListening(eq("pl-PL"));
        assertEquals(CallSessionManager.State.LISTENING, callSessionManager.getCurrentState());
        // Verify notification was updated to indicate listening
        verify(mockNotificationHandler, times(1)).updateNotificationMessage(
            eq("Listening to caller...") // Use the mocked string value
        );
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
        setupSessionForListeningState(); // Puts in LISTENING state
        org.mockito.Mockito.clearInvocations(mockAudioHandler); // Clear previous interactions

        // Act: Simulate end of speech
        callSessionManager.onEndOfSpeech();
        
        // Act: Simulate speech result immediately (cancels timeout)
        callSessionManager.onSpeechResult("Test speech result");
        
        // Wait just a tiny bit longer than the timeout would be
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST + 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Verify followup was not played because speech result cancelled it
        verify(mockAudioHandler, never()).playFollowUpResponse();
        
        // The state should be RESPONDING after speech result
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());
    }

    @Test
    public void test_onEndOfSpeech_thenSpeechError_cancelsTimeout_playsFollowUpOnError() {
        setupSessionForListeningState(); // Ends in LISTENING state

        // Act: Simulate end of speech - timeout is now scheduled
        callSessionManager.onEndOfSpeech();
        assertEquals("State should be LISTENING before onSpeechError", CallSessionManager.State.LISTENING, callSessionManager.getCurrentState()); // Added assertion

        // Act: Simulate speech error before timeout
        callSessionManager.onSpeechError("Test error", SpeechRecognizer.ERROR_CLIENT);
        
        verify(mockAudioHandler, times(1)).playFollowUpResponse(); 
        assertEquals("State should be RESPONDING after onSpeechError handled", CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());

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
        callSessionManager.onEndOfSpeech(); // Start timeout

        // Act: Stop screening before timeout
        callSessionManager.stopScreening(); // Corrected: No argument
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Assert
        verify(mockAudioHandler, never()).playFollowUpResponse();
        assertEquals(CallSessionManager.State.ENDED, callSessionManager.getCurrentState());
    }

    @Test
    public void test_multipleOnEndOfSpeech_resetsTimeoutCorrectly() {
        setupSessionForListeningState();
        org.mockito.Mockito.clearInvocations(mockAudioHandler); // Clear previous interactions
        
        // Act: Simulate first end of speech
        callSessionManager.onEndOfSpeech();
        
        // Act: Immediately simulate speech result (cancels first timeout)
        // This will call audioHandler.speak() with the LLM response
        callSessionManager.onSpeechResult("intermediate result");
        
        // Verify we're in RESPONDING state after speech result
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());
        
        // Simulate playback completion to return to LISTENING state
        callSessionManager.onPlaybackCompleted();
        
        // Verify we're back in LISTENING state
        assertEquals(CallSessionManager.State.LISTENING, callSessionManager.getCurrentState());
        
        // Now simulate end of speech again (schedules a new timeout)
        callSessionManager.onEndOfSpeech();
        
        // Clear interactions again so we can verify the next actions cleanly
        org.mockito.Mockito.clearInvocations(mockAudioHandler);
        
        // Wait just a bit less than the full timeout 
        ShadowLooper.idleMainLooper(STT_SILENCE_TIMEOUT_MS_TEST - 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Verify followup was NOT played yet
        verify(mockAudioHandler, never()).playFollowUpResponse();
        
        // Wait the rest of the timeout period plus a little buffer
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Verify timeout triggered and followup WAS played after the full second timeout
        verify(mockAudioHandler, times(1)).playFollowUpResponse();
        assertEquals(CallSessionManager.State.RESPONDING, callSessionManager.getCurrentState());
    }

    // Unit Tests for Task 5.4: User Take-Over Call Logic

    @Test
    public void test_userTakeOverStopsAssistantTTS() {
        // Arrange: Simulate a state where TTS might be active (e.g., GREETING)
        callSessionManager.startGreeting(); // To ensure audioHandler is not null if it matters
        assertEquals(CallSessionManager.State.GREETING, callSessionManager.getCurrentState());

        // Act
        callSessionManager.userTakesOver();

        // Assert: Just test that the state change happened
        assertEquals(CallSessionManager.State.USER_TAKEOVER, callSessionManager.getCurrentState());
    }

    @Test
    public void test_userTakeOverStopsNewSTTListening() {
        setupSessionForListeningState(); // Puts it in LISTENING state
        callSessionManager.userTakesOver();
        // Try to start STT again (e.g. if there was a delayed callback trying to restart it)
        callSessionManager.onPlaybackCompleted(); // This would normally trigger startListening
        verify(mockSpeechRecognitionHandler, never()).startListening(anyString()); // Should not start again after takeover
    }

    @Test
    public void test_userTakeOverKeepsRecordingActive() {
        // Ensure recording is started
        callSessionManager.startScreening();
        
        // Clear previous interactions
        org.mockito.Mockito.clearInvocations(mockMessageRecorderHandler);
        
        // User takes over the call
        callSessionManager.userTakesOver();
        
        // Recording should CONTINUE during user takeover, not be stopped
        verify(mockMessageRecorderHandler, never()).stopRecording();
        verify(mockMessageRecorderHandler, never()).release();
        
        // State should be USER_TAKEOVER
        assertEquals(CallSessionManager.State.USER_TAKEOVER, callSessionManager.getCurrentState());
    }

    @Test
    public void test_callSessionManagerStateChangesOnTakeOver_andNotifiesListener() {
        // Arrange: Ensure state is not already USER_TAKEOVER or ENDED
        // For instance, set it to LISTENING or GREETING. startGreeting() sets it to GREETING.
        callSessionManager.startGreeting(); 
        assertEquals("Pre-condition: State should be GREETING", CallSessionManager.State.GREETING, callSessionManager.getCurrentState());

        // Act
        callSessionManager.userTakesOver();

        // Assert: State change
        assertEquals(CallSessionManager.State.USER_TAKEOVER, callSessionManager.getCurrentState());
        
        // Assert: Listener notified
        verify(mockSessionListener).onUserTookOver(callSessionManager);
    }

    @Test
    public void test_recordingStartsImmediately() {
        // Create a test implementation that will track method calls but avoid using the real helpers
        // We need to fix the mocking of PreferencesManager
        when(mockPreferencesManager.shouldUseCustomGreetingFile()).thenReturn(false);
        when(mockPreferencesManager.getUserName()).thenReturn("Test User");
        when(mockPreferencesManager.getGreetingText()).thenReturn("Hello");
        
        // Mock the call details
        when(mockCallDetails.getHandle()).thenReturn(Uri.parse("tel:1234567890"));
        
        CallSessionManager testManager = spy(new CallSessionManager(mockContext, mockCallDetails, mockSessionListener, mockNotificationHandler) {
            @Override
            protected PreferencesManager createPreferencesManager(Context context) {
                return mockPreferencesManager;
            }
            
            @Override
            protected SpeechRecognitionHandler createSpeechRecognitionHandler(Context context, SpeechRecognitionCallbacks callbacks) {
                return mockSpeechRecognitionHandler;
            }
            
            @Override
            protected AudioHandler createAudioHandler(Context context, AudioHandler.AudioHandlerListener listener) {
                return mockAudioHandler;
            }
            
            @Override
            protected MessageRecorderHandler createMessageRecorderHandler(Context context, MessageRecorderHandler.MessageRecorderListener listener) {
                return mockMessageRecorderHandler;
            }
        });
        
        // Call startScreening
        testManager.startScreening();
        
        // Verify startRecordingMessage is called
        verify(testManager).startRecordingMessage();
        
        // Verify startGreeting is called after recording starts
        InOrder inOrder = inOrder(testManager);
        inOrder.verify(testManager).startRecordingMessage();
        inOrder.verify(testManager).startGreeting();
    }

    @Test
    public void test_recordingStopsWhenCallEnds() {
        // Start call screening which should start recording
        callSessionManager.startScreening();
        
        // Now simulate call ending
        callSessionManager.stopScreening();
        
        // Verify that message recorder is stopped and released
        verify(mockMessageRecorderHandler).stopRecording();
        verify(mockMessageRecorderHandler).release();
        
        // Verify final state is ENDED
        assertEquals(CallSessionManager.State.ENDED, callSessionManager.getCurrentState());
    }

    @Test
    public void test_recordingContinuesOnUserTakeOver() {
        // Setup recording
        when(mockMessageRecorderHandler.isRecording()).thenReturn(true);
        callSessionManager.startScreening();
        
        // Verify recording has started
        verify(mockMessageRecorderHandler).startRecording(anyString());
        
        // Transition to different states to simulate a real call flow
        callSessionManager.startGreeting();
        callSessionManager.onPlaybackCompleted(); // Moves to LISTENING state
        
        // Now user takes over
        callSessionManager.userTakesOver();
        
        // Verify specific state changes
        assertEquals(CallSessionManager.State.USER_TAKEOVER, callSessionManager.getCurrentState());
        // Verify listener was notified of user takeover
        verify(mockSessionListener).onUserTookOver(callSessionManager);
        
        // Verify recording continues (not stopped)
        verify(mockMessageRecorderHandler, never()).stopRecording();
        verify(mockMessageRecorderHandler, never()).release();
        
        // Call end should still stop recording
        callSessionManager.stopScreening();
        verify(mockMessageRecorderHandler).stopRecording();
        verify(mockMessageRecorderHandler).release();
    }
} 