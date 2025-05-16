package com.example.vac.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.vac.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowActivity;

import com.example.vac.utils.PreferencesManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.pm.PackageManager;

import org.junit.Assert;

@RunWith(AndroidJUnit4.class)
@Config(sdk = Config.NEWEST_SDK) // Test on newest SDK only for faster tests
public class SetupActivityTest {

    private PreferencesManager preferencesManager;
    private String defaultGreetingFormat;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferencesManager = new PreferencesManager(context);
        // Clear any previous preferences to ensure a clean state for each test
        preferencesManager.saveUserName("");
        preferencesManager.saveGreetingText("");
        // Load the default greeting format string from resources
        defaultGreetingFormat = context.getString(R.string.default_greeting);
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
        ShadowApplication shadowApplication = shadowOf(RuntimeEnvironment.getApplication());
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
        ShadowApplication shadowApplication = shadowOf(RuntimeEnvironment.getApplication());
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
        ShadowApplication shadowApplication = shadowOf(RuntimeEnvironment.getApplication());
        shadowApplication.denyPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE);

        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                ShadowActivity currentActivityShadow = shadowOf(activity);
                ShadowActivity.PermissionsRequest lastRequest = currentActivityShadow.getLastRequestedPermission();

                assertNotNull("A permission request should have been made", lastRequest);
                assertArrayEquals("Requested permissions should match REQUIRED_PERMISSIONS",
                                 SetupActivity.REQUIRED_PERMISSIONS, lastRequest.requestedPermissions);
                assertEquals("Request code should match", 
                             SetupActivity.REQUEST_PERMISSIONS, lastRequest.requestCode);

                TextView recordAudioStatus = activity.findViewById(R.id.record_audio_permission_status);
                assertEquals("Record Audio status should still be Not Granted before callback", 
                             "Status: Not Granted", recordAudioStatus.getText().toString());
            });
        }
    }
} 