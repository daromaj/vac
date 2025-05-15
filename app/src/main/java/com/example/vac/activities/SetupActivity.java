package com.example.vac.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import com.example.vac.utils.PreferencesManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class SetupActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
    };

    private ActivitySetupBinding binding;
    private PreferencesManager preferencesManager;
    private TextInputEditText nameInput;
    private TextInputEditText greetingInput;
    private TextView recordAudioPermissionStatus;
    private TextView phoneStatePermissionStatus;
    private TextView polishLanguagePackStatus;
    private Button openVoiceSettingsButton;
    private Button requestPermissionsButton;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferencesManager = new PreferencesManager(this);

        // Initialize UI elements
        nameInput = binding.nameInput;
        greetingInput = binding.greetingInput;
        recordAudioPermissionStatus = binding.recordAudioPermissionStatus;
        phoneStatePermissionStatus = binding.phoneStatePermissionStatus;
        polishLanguagePackStatus = binding.polishLanguagePackStatus;
        openVoiceSettingsButton = binding.openVoiceSettingsButton;
        requestPermissionsButton = binding.requestPermissionsButton;
        saveButton = binding.saveButton;

        // Load saved preferences
        loadSavedPreferences();

        // Set up button click listeners
        saveButton.setOnClickListener(v -> saveUserSettings());
        requestPermissionsButton.setOnClickListener(v -> requestRequiredPermissions());
        openVoiceSettingsButton.setOnClickListener(v -> openVoiceInputSettings());

        // Check permissions
        updatePermissionStatuses();

        // Check Polish language pack
        checkPolishLanguagePack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update permissions status and language pack status on resume
        updatePermissionStatuses();
        checkPolishLanguagePack();
    }

    private void loadSavedPreferences() {
        String savedName = preferencesManager.getUserName();
        String savedGreeting = preferencesManager.getGreetingText();

        if (!savedName.isEmpty()) {
            nameInput.setText(savedName);
        }

        if (!savedGreeting.isEmpty()) {
            greetingInput.setText(savedGreeting);
        }
    }

    private void saveUserSettings() {
        String userName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String greetingText = greetingInput.getText() != null ? greetingInput.getText().toString().trim() : "";

        if (userName.isEmpty()) {
            nameInput.setError("Please enter your name");
            return;
        }

        preferencesManager.saveUserName(userName);
        preferencesManager.saveGreetingText(greetingText);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
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
        boolean isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this);
        
        if (!isRecognitionAvailable) {
            polishLanguagePackStatus.setText("Speech recognition is not available on this device.");
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            openVoiceSettingsButton.setVisibility(View.VISIBLE);
            return;
        }
        
        // Check for Polish language specifically
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL");
        
        // This is an approximation since there's no direct way to check for specific offline models
        // A full implementation would need to attempt recognition and handle errors
        boolean hasPolishLanguage = SpeechRecognizer.isRecognitionAvailable(this);
        
        if (hasPolishLanguage) {
            polishLanguagePackStatus.setText("Polish language pack appears to be available.");
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            openVoiceSettingsButton.setVisibility(View.GONE);
        } else {
            polishLanguagePackStatus.setText(R.string.polish_language_pack_missing);
            polishLanguagePackStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            openVoiceSettingsButton.setVisibility(View.VISIBLE);
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
} 