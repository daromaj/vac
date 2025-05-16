package com.example.vac.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.vac.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.example.vac.utils.PreferencesManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
@Config(sdk = {Config.OLDEST_SDK, Config.NEWEST_SDK}) // Test on a range of SDKs
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
        final String testGreeting = "Yo, it's your assistant!";
        
        try (ActivityScenario<SetupActivity> scenario = ActivityScenario.launch(SetupActivity.class)) {
            scenario.onActivity(activity -> {
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
                Button saveButton = activity.findViewById(R.id.save_button);

                assertNotNull("Greeting input field should not be null", greetingInput);
                assertNotNull("Save button should not be null", saveButton);

                // Clear any default greeting that might be pre-filled
                greetingInput.setText("");
                
                greetingInput.setText(testGreeting);
                saveButton.performClick();

                // Verify saved in SharedPreferences
                assertEquals("Saved greeting should match exactly",
                    testGreeting, preferencesManager.getGreetingText());
            });

            // Recreate activity to check if value is loaded
            scenario.recreate();

            scenario.onActivity(activity -> {
                EditText greetingInput = activity.findViewById(R.id.greeting_input);
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
} 