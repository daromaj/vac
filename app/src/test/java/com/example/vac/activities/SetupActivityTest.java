package com.example.vac.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.vac.R;
import com.example.vac.handlers.AudioHandler;
import com.example.vac.utils.PreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowSpeechRecognizer;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.pm.PackageManager;

import org.junit.Assert;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isNull;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import android.net.Uri;
import org.robolectric.shadows.ShadowToast;
import com.google.android.material.switchmaterial.SwitchMaterial;

@RunWith(AndroidJUnit4.class)
@Config(sdk = Config.NEWEST_SDK, shadows = {SetupActivityTest.CustomShadowSpeechRecognizer.class})
public class SetupActivityTest {

    private PreferencesManager preferencesManager;
    private String defaultGreetingFormat;
    private ShadowApplication shadowApplication;
    private AudioHandler mockAudioHandler;
    private static final String POLISH_RECORDING_NOTICE = " Ta rozmowa jest nagrywana.";
    private static final String POLISH_RECORDING_KEYPHRASE = "rozmowa jest nagrywana";

    // --- Custom Shadow for SpeechRecognizer ---
    @org.robolectric.annotation.Implements(SpeechRecognizer.class)
    public static class CustomShadowSpeechRecognizer {
        private static boolean shouldRecognitionBeAvailable = true;

        public static void setShouldRecognitionBeAvailable(boolean available) {
            shouldRecognitionBeAvailable = available;
        }

        @org.robolectric.annotation.Implementation
        public static boolean isRecognitionAvailable(Context context) {
            return shouldRecognitionBeAvailable;
        }

        // You might need to add other @Implementation methods if your tests trigger them
        // For example, a constructor or other static methods of SpeechRecognizer.
        // For now, we only need isRecognitionAvailable.

        @org.robolectric.annotation.Resetter
        public static void reset() {
            shouldRecognitionBeAvailable = true; // Default to true
        }
    }
    // --- End Custom Shadow ---

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferencesManager = new PreferencesManager(context);
        preferencesManager.saveUserName("");
        preferencesManager.saveGreetingText("");
        preferencesManager.setCustomGreetingFilePath(null);
        preferencesManager.setUseCustomGreetingFile(false);
        defaultGreetingFormat = context.getString(R.string.default_greeting);
        shadowApplication = shadowOf(RuntimeEnvironment.getApplication());
        mockAudioHandler = mock(AudioHandler.class);
    }

    @Test
    public void test_saveAndLoadUserName() {
        final String testName = "Darek";
        
        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                EditText nameInput = activity.findViewById(R.id.name_input);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertNotNull("Name input field should not be null", nameInput);
                assertNotNull("Save button should not be null", saveButton);

                nameInput.setText(testName);
                saveButton.performClick();

                // Verify saved in SharedPreferences
                assertEquals(testName, preferencesManager.getUserName());
            });

            // Recreate activity to check if value is loaded
            scenario.recreate();

            scenario.onActivity(activity -> {
                EditText nameInput = activity.findViewById(R.id.name_input);
                assertEquals("Name should be loaded from SharedPreferences on recreate",
                    testName, nameInput.getText().toString());
            });
        }
    }

    @Test
    public void test_saveAndLoadGreetingText() {
        final String testName = "DarekForGreeting"; 
        final String testGreeting = "Yo, it\'s your assistant!";

        // Ensure a clean slate for prefs relevant to this test
        preferencesManager.saveUserName("");
        preferencesManager.saveGreetingText("");
        
        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            // Phase 1: Test saving
            scenario.onActivity(activity -> {
                EditText nameInput = activity.findViewById(R.id.name_input);
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertNotNull("Name input field should not be null", nameInput);
                assertNotNull("Greeting input field should not be null", greetingInput);
                assertNotNull("Save button should not be null", saveButton);

                // Set name (required for saveUserSettings to proceed)
                nameInput.setText(testName);
                
                // Set the custom greeting in the input field
                greetingInput.setText(testGreeting);
                
                saveButton.performClick();

                // Verify that SharedPreferences was updated correctly by saveUserSettings()
                assertEquals("Saved name should match", testName, preferencesManager.getUserName());
                assertEquals("Saved greeting should match exactly after click",
                    testGreeting, preferencesManager.getGreetingText());
            });

            // Phase 2: Test loading after recreate
            // Preferences should now contain testName and testGreeting from the save above.
            scenario.recreate();

            scenario.onActivity(activity -> {
                EditText nameInput = activity.findViewById(R.id.name_input);
                EditText greetingInput = activity.findViewById(R.id.greeting_input);

                assertEquals("Name should be loaded from SharedPreferences on recreate", 
                    testName, nameInput.getText().toString());
                assertEquals("Greeting should be loaded from SharedPreferences on recreate",
                    testGreeting, greetingInput.getText().toString());
            });
        }
    }

    @Test
    public void test_defaultGreetingIsUsed() {
         // Ensure no custom greeting is set
         preferencesManager.saveGreetingText("");
         // Set a user name to ensure the default greeting format is correctly applied
         String userName = "TestUser";
         preferencesManager.saveUserName(userName);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                EditText nameInput = activity.findViewById(R.id.name_input);
                
                assertNotNull("Greeting input field should not be null", greetingInput);
                assertNotNull("Name input field should not be null", nameInput);

                // Verify the name input is pre-filled
                assertEquals("User name should be pre-filled", userName, nameInput.getText().toString());
                
                // Verify the greeting input shows the default greeting hint or is empty,
                // as the actual default greeting is applied during call, not pre-filled in input.
                // The plan states: "A default greeting is pre-filled if user hasn't set one."
                // This might refer to the actual greeting logic rather than the input field text.
                // For the purpose of this test, we'll check if the customGreeting preference is empty
                // and the name is loaded. The actual default greeting logic is tested implicitly elsewhere (Task 3.3)
                // or would require deeper mocking of the TTS/AudioHandler.

                // Let's check if the custom greeting in SharedPreferences is indeed empty or null
                // and the loaded user name is correct, which implies default greeting logic would engage.
                assertEquals("Custom greeting in preferences should be empty for default to be used", "", preferencesManager.getGreetingText());
                assertEquals("User name should be loaded correctly", userName, preferencesManager.getUserName());

                // If the EditText for greeting is supposed to be pre-filled with the *formatted* default greeting,
                // then this part of the test would look like:
                // String expectedDefaultGreeting = String.format(defaultGreetingFormat, userName);
                // assertEquals("Greeting input should show the formatted default greeting", expectedDefaultGreeting, greetingInput.getText().toString());
                // However, SetupActivity currently only loads saved custom greeting into this field.
                // The test will verify that the *saved custom greeting* is empty.
                String expectedDefaultGreeting = String.format(activity.getString(R.string.default_greeting), userName);
                assertEquals("Greeting input should show formatted default greeting when no custom greeting exists",
                    expectedDefaultGreeting, greetingInput.getText().toString());
            });
        }
    }

    @Test
    public void test_checkPermissionsGranted() {
        // Grant all required permissions before the activity starts
        shadowApplication.grantPermissions(Manifest.permission.RECORD_AUDIO);
        shadowApplication.grantPermissions(Manifest.permission.READ_PHONE_STATE);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                TextView recordAudioStatus = activity.findViewById(R.id.record_audio_permission_status);
                TextView phoneStateStatus = activity.findViewById(R.id.phone_state_permission_status);

                assertEquals("Record Audio status should be Granted", 
                             "Status: Granted", recordAudioStatus.getText().toString());
                assertEquals("Phone State status should be Granted", 
                             "Status: Granted", phoneStateStatus.getText().toString());

                // Verify colors (optional, but good for completeness if easy)
                // Note: getColor with theme might be needed for Robolectric
                // assertEquals(activity.getResources().getColor(android.R.color.holo_green_dark, null), recordAudioStatus.getCurrentTextColor());
                // assertEquals(activity.getResources().getColor(android.R.color.holo_green_dark, null), phoneStateStatus.getCurrentTextColor());
            
                // Since permissions are granted, no automatic request should be made.
                // Verifying no new permission request activity was started (tricky for requestPermissions)
                // Instead, we check if the activity is proceeding normally.
                assertNotNull(activity.findViewById(R.id.save_button)); // Check activity is not stuck
            });
        }
    }

    @Test
    public void test_checkPermissionsDenied_andThenGrantedViaButton() {
        // Ensure permissions are initially denied
        shadowApplication.denyPermissions(Manifest.permission.RECORD_AUDIO);
        shadowApplication.denyPermissions(Manifest.permission.READ_PHONE_STATE);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            // Initial state: Denied (automatic request would have been triggered)
            scenario.onActivity(activity -> {
                TextView recordAudioStatus = activity.findViewById(R.id.record_audio_permission_status);
                TextView phoneStateStatus = activity.findViewById(R.id.phone_state_permission_status);
                Button requestButton = activity.findViewById(R.id.request_permissions_button);

                assertEquals("Record Audio status should be Not Granted initially", 
                             "Status: Not Granted", recordAudioStatus.getText().toString());
                assertEquals("Phone State status should be Not Granted initially", 
                             "Status: Not Granted", phoneStateStatus.getText().toString());
                
                // Simulate user clicking the request button
                // After the automatic request onResume, let's also test the button works.
                // We need to simulate the permission grant *after* the request is made.
                // For this test, we will grant permissions and then simulate onRequestPermissionsResult.

                // Grant permissions as if user accepted through dialog triggered by button
                shadowApplication.grantPermissions(Manifest.permission.RECORD_AUDIO);
                shadowApplication.grantPermissions(Manifest.permission.READ_PHONE_STATE);

                // Manually call onRequestPermissionsResult to simulate the callback from the system
                // after permissions have been granted through the (simulated) dialog.
                int[] grantResults = {PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED};
                activity.onRequestPermissionsResult(SetupActivity.REQUEST_PERMISSIONS, 
                                                    SetupActivity.REQUIRED_PERMISSIONS, 
                                                    grantResults);

                // Check UI update after permissions are granted via the callback
                assertEquals("Record Audio status should be Granted after result", 
                             "Status: Granted", recordAudioStatus.getText().toString());
                assertEquals("Phone State status should be Granted after result", 
                             "Status: Granted", phoneStateStatus.getText().toString());
            });
        }
    }

    @Test
    public void test_automaticPermissionRequest_whenInitiallyDenied() {
        shadowApplication.denyPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                ShadowActivity currentActivityShadow = shadowOf(activity);
                ShadowActivity.PermissionsRequest lastRequest = currentActivityShadow.getLastRequestedPermission();

                assertNotNull("A permission request should have been made", lastRequest);
                assertArrayEquals("Requested permissions should match REQUIRED_PERMISSIONS",
                                 SetupActivity.REQUIRED_PERMISSIONS, lastRequest.requestedPermissions);
                assertEquals("Request code should match", SetupActivity.REQUEST_PERMISSIONS, lastRequest.requestCode);
            });
        }
    }

    // --- Tests for Task 1.4: Polish Language Pack Check ---

    @Test
    @Config(shadows = {CustomShadowSpeechRecognizer.class}) // Use our custom shadow
    public void test_languagePackAvailable() {
        CustomShadowSpeechRecognizer.setShouldRecognitionBeAvailable(true); // General SR is available
        
        // Mock PackageManager to return a non-empty list for Polish SR Intent
        ShadowPackageManager shadowPackageManager = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        Intent polishRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        polishRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        polishRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL");
        // Add a dummy ResolveInfo to simulate that an activity can handle this intent
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.recognizer";
        resolveInfo.activityInfo.name = "PolishRecognizerActivity";
        shadowPackageManager.addResolveInfoForIntent(polishRecognizerIntent, resolveInfo);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                TextView polishStatus = activity.findViewById(R.id.polish_language_pack_status);
                Button openSettingsButton = activity.findViewById(R.id.open_voice_settings_button);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertEquals(activity.getString(R.string.polish_language_pack_available), polishStatus.getText().toString());
                assertEquals(View.GONE, openSettingsButton.getVisibility());
                assertTrue("Save button should be enabled", saveButton.isEnabled());
            });
        }
    }

    @Test
    @Config(shadows = {CustomShadowSpeechRecognizer.class}) // Use our custom shadow
    public void test_languagePackMissing_butGeneralSRAvailable() {
        CustomShadowSpeechRecognizer.setShouldRecognitionBeAvailable(true); // General SR is available
        
        // Mock PackageManager to return an empty list for Polish SR Intent
        ShadowPackageManager shadowPackageManager = shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        Intent polishRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        polishRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        shadowPackageManager.removeResolveInfosForIntent(polishRecognizerIntent, null); // Clear any existing

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                TextView polishStatus = activity.findViewById(R.id.polish_language_pack_status);
                Button openSettingsButton = activity.findViewById(R.id.open_voice_settings_button);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertEquals(activity.getString(R.string.polish_language_pack_missing), polishStatus.getText().toString());
                assertEquals(View.VISIBLE, openSettingsButton.getVisibility());
                assertFalse("Save button should be disabled", saveButton.isEnabled());
            });
        }
    }

    @Test
    @Config(shadows = {CustomShadowSpeechRecognizer.class}) // Use our custom shadow
    public void test_generalSRAbsent() {
        CustomShadowSpeechRecognizer.setShouldRecognitionBeAvailable(false); // Use custom shadow's static method

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                TextView polishStatus = activity.findViewById(R.id.polish_language_pack_status);
                Button openSettingsButton = activity.findViewById(R.id.open_voice_settings_button);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertEquals(activity.getString(R.string.speech_recognition_not_available), polishStatus.getText().toString());
                assertEquals(View.VISIBLE, openSettingsButton.getVisibility());
                assertFalse("Save button should be disabled", saveButton.isEnabled());
            });
        }
    }

    @Test
    @Config(shadows = {CustomShadowSpeechRecognizer.class}) // Use our custom shadow
    public void test_intentToVoiceSettings_whenGeneralSRAbsent() {
        // This test also relies on isRecognitionAvailable returning false
        CustomShadowSpeechRecognizer.setShouldRecognitionBeAvailable(false); // General SR is NOT available

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                // Mock PackageManager to resolve the ACTION_VOICE_INPUT_SETTINGS intent
                ShadowPackageManager shadowPackageManager = shadowOf(activity.getPackageManager());
                Intent voiceInputIntent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.activityInfo = new ActivityInfo();
                resolveInfo.activityInfo.packageName = "com.example.settings"; // Dummy package
                resolveInfo.activityInfo.name = "VoiceInputSettingsActivity"; // Dummy activity
                shadowPackageManager.addResolveInfoForIntent(voiceInputIntent, resolveInfo);

                Button openSettingsButton = activity.findViewById(R.id.open_voice_settings_button);
                openSettingsButton.performClick();

                ShadowActivity shadowActivity = shadowOf(activity);
                Intent startedIntent = shadowActivity.getNextStartedActivity();
                assertNotNull("Intent should have been started", startedIntent);
                assertEquals("Intent action should be ACTION_VOICE_INPUT_SETTINGS", 
                             Settings.ACTION_VOICE_INPUT_SETTINGS, startedIntent.getAction());
            });
        }
    }

    // --- Tests for Task 4.1.2: Generate Greeting File Logic ---

    @Test
    public void test_generateGreetingFile_success() {
        final String testName = "Darek";
        final String testBaseGreeting = "Hello from Darek"; // Custom greeting, does not contain Polish notice
        // Expected synthesized text should now have the Polish notice appended
        final String expectedSynthesizedText = testBaseGreeting + POLISH_RECORDING_NOTICE;
        final String expectedFileName = "custom_greeting_generated.wav";
        final String fakeFilePath = "/data/user/0/com.example.vac/files/" + expectedFileName;

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                activity.audioHandler = mockAudioHandler;

                EditText nameInput = activity.findViewById(R.id.name_input);
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                Button generateButton = activity.findViewById(R.id.generate_greeting_file_button);
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);

                nameInput.setText(testName);
                greetingInput.setText(testBaseGreeting);

                generateButton.performClick();

                ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> fileCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<AudioHandler.SynthesisCallback> callbackCaptor = ArgumentCaptor.forClass(AudioHandler.SynthesisCallback.class);

                verify(mockAudioHandler).synthesizeGreetingToFile(textCaptor.capture(), fileCaptor.capture(), callbackCaptor.capture());
                assertEquals(expectedSynthesizedText, textCaptor.getValue());
                assertEquals(expectedFileName, fileCaptor.getValue());
                assertNotNull(callbackCaptor.getValue());

                callbackCaptor.getValue().onSuccess(fakeFilePath);

                assertEquals("Status: Generated " + expectedFileName, statusText.getText().toString());
                assertEquals(fakeFilePath, preferencesManager.getCustomGreetingFilePath());
                assertTrue(generateButton.isEnabled());
            });
        }
    }

    @Test
    public void test_generateGreetingFile_ttsFailure() {
        final String testName = "DarekFail";
        final String testBaseGreeting = "Test greeting for failure"; // Custom greeting, does not contain Polish notice
        // Expected synthesized text should now have the Polish notice appended
        final String expectedSynthesizedText = testBaseGreeting + POLISH_RECORDING_NOTICE;
        final String expectedFileName = "custom_greeting_generated.wav";

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                activity.audioHandler = mockAudioHandler;

                EditText nameInput = activity.findViewById(R.id.name_input);
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                Button generateButton = activity.findViewById(R.id.generate_greeting_file_button);
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);

                nameInput.setText(testName);
                greetingInput.setText(testBaseGreeting);
                generateButton.performClick();

                ArgumentCaptor<AudioHandler.SynthesisCallback> callbackCaptor = ArgumentCaptor.forClass(AudioHandler.SynthesisCallback.class);
                verify(mockAudioHandler).synthesizeGreetingToFile(eq(expectedSynthesizedText), eq(expectedFileName), callbackCaptor.capture());

                String errorMessage = "TTS engine blew up";
                callbackCaptor.getValue().onError(errorMessage);

                assertEquals("Status: Error - " + errorMessage, statusText.getText().toString());
                assertNull(preferencesManager.getCustomGreetingFilePath());
                assertTrue(generateButton.isEnabled());
            });
        }
    }

    @Test
    public void test_defaultGreetingGenerated_whenBaseGreetingIsEmpty() {
         try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                activity.audioHandler = mockAudioHandler;

                EditText nameInput = activity.findViewById(R.id.name_input);
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                Button generateButton = activity.findViewById(R.id.generate_greeting_file_button);
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);

                String testUserName = "TestUser";
                nameInput.setText(testUserName);
                greetingInput.setText("   "); // Empty after trim, so default greeting should be used

                generateButton.performClick();

                ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> fileCaptor = ArgumentCaptor.forClass(String.class);
                verify(mockAudioHandler).synthesizeGreetingToFile(textCaptor.capture(), fileCaptor.capture(), any(AudioHandler.SynthesisCallback.class));

                // Expected synthesized text is the Polish default greeting, formatted
                String expectedSynthesizedText = String.format(activity.getString(R.string.default_greeting), testUserName);
                String expectedFileName = "custom_greeting_generated.wav";

                assertEquals(expectedSynthesizedText, textCaptor.getValue());
                assertEquals(expectedFileName, fileCaptor.getValue());
                
                // Verify UI status before callback
                assertEquals("Status: Generating " + expectedFileName + "...", statusText.getText().toString());
                assertFalse(generateButton.isEnabled());

                // Verify that the "empty greeting" Toast was NOT shown
                // This relies on ShadowToast.getLatestToast() not being the error toast.
                // A more robust way might be to check ShadowToast.shownToastCount() or clear toasts before action.
                String latestToast = ShadowToast.getTextOfLatestToast();
                if (latestToast != null) {
                    assertFalse("Error toast for empty greeting should not be shown", 
                        latestToast.equals(activity.getString(R.string.greeting_text_cannot_be_empty)));
                }
            });
        }
    }
    // --- End Tests for Task 4.1.2 ---

    // --- Tests for Task 4.1.3: Play Generated Greeting Logic ---
    @Test
    public void test_playGeneratedGreeting_fileExists() throws IOException {
        final String dummyFileName = "test_greeting.wav";
        File filesDir = RuntimeEnvironment.getApplication().getFilesDir();
        File dummyFile = new File(filesDir, dummyFileName);
        // Create a dummy file
        assertTrue("Failed to create dummy file for testing", dummyFile.createNewFile() || dummyFile.exists());
        final String fakeFilePath = dummyFile.getAbsolutePath();

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                // Inject mocks
                activity.audioHandler = mockAudioHandler;
                // Configure PreferencesManager for the activity's instance
                PreferencesManager activityPrefs = new PreferencesManager(activity);
                activityPrefs.setCustomGreetingFilePath(fakeFilePath);
                activityPrefs.setUseCustomGreetingFile(true); // Assume it's set if file exists

                Button playButton = activity.findViewById(R.id.play_generated_greeting_button);
                playButton.performClick();

                ArgumentCaptor<Uri> uriCaptor = ArgumentCaptor.forClass(Uri.class);
                verify(mockAudioHandler).playAudioFile(uriCaptor.capture());
                assertEquals(Uri.fromFile(dummyFile), uriCaptor.getValue());

                // Check that status text was not changed to an error
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);
                // This check depends on initial state or successful playback message.
                // For now, ensure it's not an error state.
                // The actual message might be "Playing..." or remain as is. Let's assume it doesn't show error.
                assertFalse("Status text should not indicate an error", statusText.getText().toString().toLowerCase().contains("error"));
                assertFalse("Status text should not say 'no file'", statusText.getText().toString().toLowerCase().contains("no file"));


            });
        } finally {
            if (dummyFile.exists()) {
                dummyFile.delete();
            }
        }
    }

    @Test
    public void test_playGeneratedGreeting_noFileSetInPreferences() {
        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                activity.audioHandler = mockAudioHandler;
                PreferencesManager activityPrefs = new PreferencesManager(activity);
                activityPrefs.setCustomGreetingFilePath(null); // Ensure no file path is set

                Button playButton = activity.findViewById(R.id.play_generated_greeting_button);
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);

                playButton.performClick();

                verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
                assertEquals(activity.getString(R.string.custom_greeting_status_default), statusText.getText().toString());
                assertEquals("No custom greeting file generated yet.", ShadowToast.getTextOfLatestToast());
            });
        }
    }

    @Test
    public void test_playGeneratedGreeting_fileNotFoundOnDisk() {
        final String nonExistentFileName = "non_existent_greeting.wav";
        final String fakeFilePath = new File(RuntimeEnvironment.getApplication().getFilesDir(), nonExistentFileName).getAbsolutePath();

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                activity.audioHandler = mockAudioHandler;
                PreferencesManager spyPrefs = org.mockito.Mockito.spy(new PreferencesManager(activity));
                activity.preferencesManager = spyPrefs; 

                spyPrefs.setCustomGreetingFilePath(fakeFilePath);
                spyPrefs.setUseCustomGreetingFile(true); 

                File nonExistentFile = new File(fakeFilePath);
                if (nonExistentFile.exists()) {
                    nonExistentFile.delete();
                }
                assertFalse("File should not exist for this test", nonExistentFile.exists());

                Button playButton = activity.findViewById(R.id.play_generated_greeting_button);
                TextView statusText = activity.findViewById(R.id.custom_greeting_status_text);
                SwitchMaterial useCustomSwitch = activity.findViewById(R.id.use_custom_greeting_file_switch);

                playButton.performClick();

                verify(mockAudioHandler, never()).playAudioFile(any(Uri.class));
                assertEquals("Status: Error - File not found.", statusText.getText().toString());
                String expectedToastMsg = "Custom greeting file not found at path: " + fakeFilePath;
                assertEquals(expectedToastMsg, ShadowToast.getTextOfLatestToast());

                verify(spyPrefs).setCustomGreetingFilePath(isNull());
                assertFalse(useCustomSwitch.isChecked());
            });
        }
    }
    // --- End Tests for Task 4.1.3 ---
} 