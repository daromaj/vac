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
     * Check if the user has completed the setup process.
     * 
     * @return true if the user has provided at least a name
     */
    public boolean isSetupCompleted() {
        String userName = getUserName();
        return userName != null && !userName.isEmpty();
    }
} 