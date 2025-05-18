package com.example.vac.activities;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

import android.widget.SeekBar;
import android.os.Handler;

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

    private static final String POLISH_RECORDING_NOTICE = " Ta rozmowa jest nagrywana.";
    private static final String POLISH_RECORDING_KEYPHRASE = "rozmowa jest nagrywana";

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

        // Add TextWatcher for live update of default greeting
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Do nothing */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { /* Do nothing */ }

            @Override
            public void afterTextChanged(Editable s) {
                updateDefaultGreetingPreview();
            }
        });

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

        if (savedName != null && !savedName.isEmpty()) { // Check for null before isEmpty
            nameInput.setText(savedName);
        }

        // Always update greeting preview based on current name and saved custom greeting
        updateDefaultGreetingPreview();

        useCustomGreetingFileSwitch.setChecked(shouldUseCustomFile);
    }

    private void updateDefaultGreetingPreview() {
        String currentName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String savedCustomGreeting = preferencesManager.getGreetingText(); // Get the *saved* custom greeting

        if (savedCustomGreeting == null || savedCustomGreeting.isEmpty()) {
            // No custom greeting is saved, so the input field should reflect the default greeting with the current name.
            String defaultGreetingFormat = getString(R.string.default_greeting);
            greetingInput.setText(String.format(defaultGreetingFormat, currentName));
        } else {
            // A custom greeting IS saved. Display that one, and don't change it live.
            greetingInput.setText(savedCustomGreeting);
        }
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
        String greetingForFile;

        if (greetingBase != null && !greetingBase.isEmpty()) {
            // User provided base greeting, append Polish recording notice if not already there.
            if (!greetingBase.toLowerCase(Locale.ROOT).contains(POLISH_RECORDING_KEYPHRASE)) {
                greetingForFile = greetingBase + POLISH_RECORDING_NOTICE;
            } else {
                greetingForFile = greetingBase;
            }
        } else {
            // Construct a default greeting if base is empty using the R.string.default_greeting
            // which already contains the Polish recording notice.
            String namePart = (userName != null && !userName.isEmpty()) ? userName : ""; // Use empty string if name is null/empty for formatting
            greetingForFile = String.format(getString(R.string.default_greeting), namePart);
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
                if (greetingFile.exists() && greetingFile.canRead()) {
                    try {
                        Uri fileUri = Uri.fromFile(greetingFile);
                        audioHandler.playAudioFile(fileUri);  // Start playback
                        showGreetingPlaybackControls();  // Show controls after starting playback
                        Toast.makeText(this, "Playing custom greeting...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("SetupActivity", "Error playing greeting file: " + e.getMessage(), e);
                        Toast.makeText(this, "Error playing greeting: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        customGreetingStatusText.setText("Status: Error playing file - " + e.getMessage());
                    }
                } else {
                    Toast.makeText(this, "Custom greeting file not found or not readable: " + filePath, Toast.LENGTH_LONG).show();
                    customGreetingStatusText.setText("Status: Error - File not found or not readable.");
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

    // New method to show playback controls for greeting
    private void showGreetingPlaybackControls() {
        // Similar to MessagesActivity, but adapted for SetupActivity
        // Assuming you have a layout for controls in activity_setup.xml, e.g., with ID playback_controls_greeting
        View controlsView = findViewById(R.id.playback_controls_layout);  // Use the same ID if shared, or define a new one
        if (controlsView != null) {
            controlsView.setVisibility(View.VISIBLE);

            Button btnPlayPause = (Button) findViewById(R.id.btn_play_pause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    if (audioHandler.isPlaying()) {  // Assuming AudioHandler has isPlaying method
                        audioHandler.pause();
                        btnPlayPause.setText("Play");
                    } else {
                        audioHandler.play();
                        btnPlayPause.setText("Pause");
                    }
                });
            }

            SeekBar seekBar = (SeekBar) findViewById(R.id.seek_bar);
            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        if (fromUser && audioHandler != null) {
                            audioHandler.seekTo(progress);  // Assuming AudioHandler has seekTo
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                // Set up seekbar max and progress if possible
                if (audioHandler != null) {
                    seekBar.setMax(audioHandler.getDuration());  // Assuming getDuration method
                }
            }

            TextView timerTextView = (TextView) findViewById(R.id.timer_text_view);
            if (timerTextView != null && audioHandler != null) {
                timerTextView.setText(formatTime(audioHandler.getCurrentPosition()) + " / " + formatTime(audioHandler.getDuration()));
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (audioHandler != null && audioHandler.isPlaying() && timerTextView != null) {
                            int currentPosition = audioHandler.getCurrentPosition();
                            if (seekBar != null) {
                                seekBar.setProgress(currentPosition);
                            }
                            timerTextView.setText(formatTime(currentPosition) + " / " + formatTime(audioHandler.getDuration()));
                            new Handler().postDelayed(this, 1000);
                        }
                    }
                }, 1000);
            }
        } else {
            Log.e(TAG, "Playback controls layout not found in SetupActivity.");
            Toast.makeText(this, "Playback controls not available.", Toast.LENGTH_SHORT).show();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_view_messages) {
            // Navigate to MessagesActivity
            Intent intent = new Intent(this, MessagesActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
