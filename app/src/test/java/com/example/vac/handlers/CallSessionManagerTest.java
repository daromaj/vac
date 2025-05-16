package com.example.vac.handlers;

import android.content.Context;
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
@Config(manifest=Config.NONE)
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
} 