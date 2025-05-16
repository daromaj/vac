package com.example.vac.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.vac.R;
import com.example.vac.databinding.ActivitySetupBinding;
import com.example.vac.handlers.AudioHandler;
import com.example.vac.utils.PreferencesManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.List;
import java.util.Locale;

// Import for RoleManager
import android.app.role.RoleManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.Context;
import androidx.annotation.RequiresApi;

public class SetupActivity extends AppCompatActivity {
    public static final int REQUEST_PERMISSIONS = 100;
    public static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
    };

    private ActivitySetupBinding binding;
    PreferencesManager preferencesManager;
    private TextInputEditText nameInput;
    private TextInputEditText greetingInput;
    private TextView recordAudioPermissionStatus;
    private TextView phoneStatePermissionStatus;
    private TextView polishLanguagePackStatus;
    private Button openVoiceSettingsButton;
    private Button requestPermissionsButton;
    private Button saveButton;
    // New UI elements for Default Call Screener
    private TextView defaultScreenerStatusText;
    private Button setDefaultScreenerButton;

    // New UI elements for Custom Greeting File
    private Button generateGreetingFileButton;
    private Button playGeneratedGreetingButton;
    private TextView customGreetingStatusText;
    private SwitchMaterial useCustomGreetingFileSwitch;

    AudioHandler audioHandler;

    // ActivityResultLauncher for RoleManager request
    private ActivityResultLauncher<Intent> roleActivityResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferencesManager = new PreferencesManager(this);
        audioHandler = new AudioHandler(this, null);

        // Initialize UI elements
        nameInput = binding.nameInput;
        greetingInput = binding.greetingInput;
        recordAudioPermissionStatus = binding.recordAudioPermissionStatus;
        phoneStatePermissionStatus = binding.phoneStatePermissionStatus;
        polishLanguagePackStatus = binding.polishLanguagePackStatus;
        openVoiceSettingsButton = binding.openVoiceSettingsButton;
        requestPermissionsButton = binding.requestPermissionsButton;
        saveButton = binding.saveButton;
        // Initialize new UI elements
        defaultScreenerStatusText = binding.defaultScreenerStatusText;
        setDefaultScreenerButton = binding.setDefaultScreenerButton;

        // Initialize Custom Greeting File UI elements
        generateGreetingFileButton = binding.generateGreetingFileButton;
        playGeneratedGreetingButton = binding.playGeneratedGreetingButton;
        customGreetingStatusText = binding.customGreetingStatusText;
        useCustomGreetingFileSwitch = binding.useCustomGreetingFileSwitch;

        if (preferencesManager.hasCustomGreetingFile()) {
            String filePath = preferencesManager.getCustomGreetingFilePath();
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            customGreetingStatusText.setText("Status: Using custom file: " + fileName);
        } else {
            customGreetingStatusText.setText(getString(R.string.custom_greeting_status_default));
        }
        // Load and set switch state
        useCustomGreetingFileSwitch.setChecked(preferencesManager.shouldUseCustomGreetingFile());
        // Set listener for the switch to save preference immediately
        useCustomGreetingFileSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferencesManager.setUseCustomGreetingFile(isChecked);
            Toast.makeText(SetupActivity.this, 
                "Custom greeting file for calls: " + (isChecked ? "Enabled" : "Disabled"), 
                Toast.LENGTH_SHORT).show();
        });

        // Load saved preferences
        loadSavedPreferences();

        // Set up button click listeners
        saveButton.setOnClickListener(v -> saveUserSettings());
        requestPermissionsButton.setOnClickListener(v -> requestRequiredPermissions());
        openVoiceSettingsButton.setOnClickListener(v -> openVoiceInputSettings());
        // Set click listener for the new button
        setDefaultScreenerButton.setOnClickListener(v -> openDefaultCallScreenerSettings());
        generateGreetingFileButton.setOnClickListener(v -> generateGreetingFile());
        playGeneratedGreetingButton.setOnClickListener(v -> playGeneratedGreeting());

        // Initialize ActivityResultLauncher for RoleManager
        // This launcher will handle the result of the role request if we decide to request it directly.
        // For now, we just open settings, so this might not be strictly needed if we don't auto-request.
        roleActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Check status again after returning from settings or role request
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        checkDefaultCallScreenerStatus();
                    }
                });

        // Check permissions
        updatePermissionStatuses();

        // Check Polish language pack
        checkPolishLanguagePack();
        // Check Default Call Screener Status (only on Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkDefaultCallScreenerStatus();
            // Ensure container is visible on Q+ if it was previously hidden by a different logic path (unlikely here but good practice)
            View screenerSectionContainer = findViewById(R.id.default_screener_section_container);
            if (screenerSectionContainer != null) {
                screenerSectionContainer.setVisibility(View.VISIBLE);
            }
        } else {
            // Hide the entire screener settings section on older versions
            View screenerSectionContainer = findViewById(R.id.default_screener_section_container);
            if (screenerSectionContainer != null) {
                screenerSectionContainer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update permissions status and language pack status on resume
        updatePermissionStatuses();
        checkPolishLanguagePack();
        // Check Default Call Screener Status on resume (only on Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkDefaultCallScreenerStatus();
        } else {
            // Ensure it stays hidden on resume for pre-Q
            View screenerSectionContainer = findViewById(R.id.default_screener_section_container);
            if (screenerSectionContainer != null) {
                screenerSectionContainer.setVisibility(View.GONE);
            }
        }
        // Update custom greeting status text on resume
        if (preferencesManager.hasCustomGreetingFile()) {
            String filePath = preferencesManager.getCustomGreetingFilePath();
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            customGreetingStatusText.setText("Status: Using custom file: " + fileName);
        } else {
            customGreetingStatusText.setText(getString(R.string.custom_greeting_status_default));
        }

        // Automatically request permissions if not all are granted
        if (!areAllPermissionsGranted()) {
            requestRequiredPermissions();
        }
    }

    private void loadSavedPreferences() {
        String savedName = preferencesManager.getUserName();
        String savedGreeting = preferencesManager.getGreetingText();
        boolean shouldUseCustomFile = preferencesManager.shouldUseCustomGreetingFile();

        if (!savedName.isEmpty()) {
            nameInput.setText(savedName);
        }

        if (!savedGreeting.isEmpty()) {
            greetingInput.setText(savedGreeting);
        } else if (!savedName.isEmpty()) {
            // Pre-fill default greeting if no custom greeting exists and name is available
            String defaultGreeting = getString(R.string.default_greeting);
            greetingInput.setText(String.format(defaultGreeting, savedName));
        }

        useCustomGreetingFileSwitch.setChecked(shouldUseCustomFile);
    }

    private void saveUserSettings() {
        String userName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String greetingText = greetingInput.getText() != null ? greetingInput.getText().toString().trim() : "";
        boolean useGeneratedFile = useCustomGreetingFileSwitch.isChecked();

        if (userName.isEmpty()) {
            nameInput.setError("Please enter your name");
            return;
        }

        preferencesManager.saveUserName(userName);
        preferencesManager.saveGreetingText(greetingText);
        preferencesManager.setUseCustomGreetingFile(useGeneratedFile);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
    }

    private boolean areAllPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void updatePermissionStatuses() {
        boolean recordAudioGranted = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean phoneStateGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        // Update Record Audio status
        recordAudioPermissionStatus.setText("Status: " + 
                (recordAudioGranted ? "Granted" : "Not Granted"));
        recordAudioPermissionStatus.setTextColor(getResources().getColor(
                recordAudioGranted ? android.R.color.holo_green_dark : android.R.color.holo_red_dark, 
                null));

        // Update Phone State status
        phoneStatePermissionStatus.setText("Status: " + 
                (phoneStateGranted ? "Granted" : "Not Granted"));
        phoneStatePermissionStatus.setTextColor(getResources().getColor(
                phoneStateGranted ? android.R.color.holo_green_dark : android.R.color.holo_red_dark, 
                null));
    }

    private void checkPolishLanguagePack() {
        boolean isGeneralRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this);

        if (!isGeneralRecognitionAvailable) {
            polishLanguagePackStatus.setText(R.string.speech_recognition_not_available);
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            openVoiceSettingsButton.setVisibility(View.VISIBLE);
            saveButton.setEnabled(false); // Disable save if no SR
            return;
        }

        // Check specifically for Polish language support by querying activities
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        // EXTRA_LANGUAGE_PREFERENCE tells the recognizer which language to prefer if it supports multiple
        // and the primary language is not available or not specified.
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL"); 
        // EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE can be used if we strictly want results for this lang or none.
        // For checking availability, querying activities is a common approach.

        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(recognizerIntent, 0);

        if (!activities.isEmpty()) {
            polishLanguagePackStatus.setText(R.string.polish_language_pack_available);
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            openVoiceSettingsButton.setVisibility(View.GONE);
            saveButton.setEnabled(true); // Enable save if Polish SR seems available
        } else {
            polishLanguagePackStatus.setText(R.string.polish_language_pack_missing);
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            openVoiceSettingsButton.setVisibility(View.VISIBLE);
            saveButton.setEnabled(false); // Disable save if Polish SR is missing
        }
    }

    private void openVoiceInputSettings() {
        Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fallback to language & input settings if voice input settings aren't available
            intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Cannot open language settings on this device", 
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            updatePermissionStatuses();
        }
    }

    // New methods for Default Call Screener
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkDefaultCallScreenerStatus() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        boolean isHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        if (isHeld) {
            defaultScreenerStatusText.setText(R.string.screener_is_default);
            defaultScreenerStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            setDefaultScreenerButton.setText(R.string.open_default_screener_settings_button); // Or just "Settings"
        } else {
            defaultScreenerStatusText.setText(R.string.screener_is_not_default);
            defaultScreenerStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            setDefaultScreenerButton.setText(R.string.set_default_screener_button);
        }
        // The visibility of individual elements inside the container is handled here based on logic.
        // The container's visibility is handled in onCreate/onResume based on API level.
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void openDefaultCallScreenerSettings() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        boolean isCurrentlyDefault = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        Intent intent;
        if (isCurrentlyDefault) {
            // If VAC is already the default, take the user to the general default apps settings page.
            intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        } else {
            // If VAC is not the default, request the role.
            intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
        }
        
        // Check if the intent can be resolved before launching to prevent crashes
        if (intent.resolveActivity(getPackageManager()) != null) {
            roleActivityResultLauncher.launch(intent);
        } else {
            Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateGreetingFile() {
        String userName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String greetingBase = greetingInput.getText() != null ? greetingInput.getText().toString().trim() : "";
        String recordingNotice = " This call is being recorded."; // Leading space is important
        String greetingForFile;

        if (!greetingBase.isEmpty()) {
            // If user provided base greeting, append recording notice if not already there.
            // Simple check; could be more robust (e.g., case-insensitive, check for variations).
            if (!greetingBase.toLowerCase(Locale.ROOT).contains("call is being recorded")) {
                greetingForFile = greetingBase + recordingNotice;
            } else {
                greetingForFile = greetingBase;
            }
        } else {
            // Construct a default greeting if base is empty
            String namePart = userName.isEmpty() ? "the user" : userName;
            // Using a slightly different default here for the generated file for clarity, or could match call one.
            greetingForFile = String.format(Locale.US, "Hello, you have reached %s.%s", namePart, recordingNotice.trim());
        }

        if (greetingForFile.trim().isEmpty()) {
            Toast.makeText(this, "Greeting text cannot be empty for generation.", Toast.LENGTH_SHORT).show();
            customGreetingStatusText.setText("Status: Greeting text empty.");
            return;
        }

        final String desiredFileName = "custom_greeting_generated.wav"; // Consistent filename
        customGreetingStatusText.setText("Status: Generating " + desiredFileName + "...");
        generateGreetingFileButton.setEnabled(false); // Disable button during generation

        audioHandler.synthesizeGreetingToFile(greetingForFile, desiredFileName,
                new AudioHandler.SynthesisCallback() {
                    @Override
                    public void onSuccess(String filePath) {
                        runOnUiThread(() -> {
                            customGreetingStatusText.setText("Status: Generated " + desiredFileName);
                            preferencesManager.setCustomGreetingFilePath(filePath);
                            Toast.makeText(SetupActivity.this, "Greeting file generated: " + desiredFileName, Toast.LENGTH_SHORT).show();
                            generateGreetingFileButton.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            customGreetingStatusText.setText("Status: Error - " + errorMessage);
                            preferencesManager.setCustomGreetingFilePath(null); // Clear path on error
                            Toast.makeText(SetupActivity.this, "Error generating file: " + errorMessage, Toast.LENGTH_LONG).show();
                            generateGreetingFileButton.setEnabled(true);
                        });
                    }
                });
    }

    private void playGeneratedGreeting() {
        if (preferencesManager.hasCustomGreetingFile()) {
            String filePath = preferencesManager.getCustomGreetingFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                File greetingFile = new File(filePath);
                if (greetingFile.exists()) {
                    Uri fileUri = Uri.fromFile(greetingFile);
                    audioHandler.playAudioFile(fileUri);
                    Toast.makeText(this, "Playing custom greeting...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Custom greeting file not found at path: " + filePath, Toast.LENGTH_LONG).show();
                    customGreetingStatusText.setText("Status: Error - File not found.");
                    preferencesManager.setCustomGreetingFilePath(null); // Clear invalid path
                }
            } else {
                Toast.makeText(this, "Custom greeting file path not configured.", Toast.LENGTH_SHORT).show();
                customGreetingStatusText.setText(getString(R.string.custom_greeting_status_default));
            }
        } else {
            Toast.makeText(this, "No custom greeting file generated yet.", Toast.LENGTH_SHORT).show();
            customGreetingStatusText.setText(getString(R.string.custom_greeting_status_default));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioHandler != null) {
            audioHandler.release();
            audioHandler = null;
        }
    }
} 