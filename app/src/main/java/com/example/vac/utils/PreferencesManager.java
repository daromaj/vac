package com.example.vac.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class to manage application preferences.
 */
public class PreferencesManager {
    private static final String PREF_NAME = "VAC_PREFS";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_GREETING_TEXT = "greeting_text";
    private static final String KEY_CUSTOM_GREETING_FILE_PATH = "custom_greeting_file_path";
    private static final String KEY_USE_CUSTOM_GREETING_FILE = "use_custom_greeting_file";
    private static final String DEFAULT_USER_NAME = "";
    private static final String DEFAULT_GREETING_TEXT = "";
    
    private final SharedPreferences preferences;
    
    public PreferencesManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Save the user's name to preferences.
     * 
     * @param userName The user's name
     */
    public void saveUserName(String userName) {
        preferences.edit().putString(KEY_USER_NAME, userName).apply();
    }
    
    /**
     * Get the user's name from preferences.
     * 
     * @return The user's name
     */
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, DEFAULT_USER_NAME);
    }
    
    /**
     * Save the custom greeting text to preferences.
     * 
     * @param greetingText The custom greeting text
     */
    public void saveGreetingText(String greetingText) {
        preferences.edit().putString(KEY_GREETING_TEXT, greetingText).apply();
    }
    
    /**
     * Get the custom greeting text from preferences.
     * 
     * @return The custom greeting text, or empty string if not set
     */
    public String getGreetingText() {
        return preferences.getString(KEY_GREETING_TEXT, DEFAULT_GREETING_TEXT);
    }
    
    /**
     * Save the file path of the custom generated greeting.
     *
     * @param filePath The absolute path to the generated greeting file, or null to clear.
     */
    public void setCustomGreetingFilePath(String filePath) {
        preferences.edit().putString(KEY_CUSTOM_GREETING_FILE_PATH, filePath).apply();
    }
    
    /**
     * Get the file path of the custom generated greeting.
     *
     * @return The absolute path to the greeting file, or null if not set.
     */
    public String getCustomGreetingFilePath() {
        return preferences.getString(KEY_CUSTOM_GREETING_FILE_PATH, null);
    }
    
    /**
     * Check if a custom greeting file path is stored.
     *
     * @return true if a non-empty path is stored, false otherwise.
     */
    public boolean hasCustomGreetingFile() {
        String path = getCustomGreetingFilePath();
        return path != null && !path.isEmpty();
    }

    /**
     * Set whether to use the custom generated greeting file for calls.
     *
     * @param use true to use the custom file, false otherwise.
     */
    public void setUseCustomGreetingFile(boolean use) {
        preferences.edit().putBoolean(KEY_USE_CUSTOM_GREETING_FILE, use).apply();
    }

    /**
     * Check if the custom generated greeting file should be used for calls.
     *
     * @return true if the custom file should be used, false otherwise (defaults to false).
     */
    public boolean shouldUseCustomGreetingFile() {
        return preferences.getBoolean(KEY_USE_CUSTOM_GREETING_FILE, false);
    }
    
    /**
     * Check if the user has completed the setup process.
     * 
     * @return true if the user has provided at least a name
     */
    public boolean isSetupCompleted() {
        String userName = getUserName();
        return userName != null && !userName.isEmpty();
    }
} 