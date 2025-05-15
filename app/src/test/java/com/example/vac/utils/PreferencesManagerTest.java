package com.example.vac.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PreferencesManagerTest {

    private static final String PREF_NAME = "VAC_PREFS";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_GREETING_TEXT = "greeting_text";
    private static final String TEST_USER_NAME = "Test User";
    private static final String TEST_GREETING = "Custom Greeting";

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockSharedPreferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    private PreferencesManager preferencesManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock SharedPreferences
        when(mockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mockSharedPreferences);
        
        // Mock Editor
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        
        preferencesManager = new PreferencesManager(mockContext);
    }

    @Test
    public void test_saveAndLoadUserName() {
        // Setup
        when(mockSharedPreferences.getString(eq(KEY_USER_NAME), anyString()))
                .thenReturn(TEST_USER_NAME);

        // Test saving
        preferencesManager.saveUserName(TEST_USER_NAME);
        verify(mockEditor).putString(KEY_USER_NAME, TEST_USER_NAME);
        verify(mockEditor).apply();

        // Test loading
        String loadedName = preferencesManager.getUserName();
        assertEquals(TEST_USER_NAME, loadedName);
    }

    @Test
    public void test_saveAndLoadGreetingText() {
        // Setup
        when(mockSharedPreferences.getString(eq(KEY_GREETING_TEXT), anyString()))
                .thenReturn(TEST_GREETING);

        // Test saving
        preferencesManager.saveGreetingText(TEST_GREETING);
        verify(mockEditor).putString(KEY_GREETING_TEXT, TEST_GREETING);
        verify(mockEditor).apply();

        // Test loading
        String loadedGreeting = preferencesManager.getGreetingText();
        assertEquals(TEST_GREETING, loadedGreeting);
    }

    @Test
    public void test_isSetupCompleted_whenNameExists() {
        when(mockSharedPreferences.getString(eq(KEY_USER_NAME), anyString()))
                .thenReturn(TEST_USER_NAME);

        boolean isSetupCompleted = preferencesManager.isSetupCompleted();
        
        assertTrue(isSetupCompleted);
    }

    @Test
    public void test_isSetupCompleted_whenNameEmpty() {
        when(mockSharedPreferences.getString(eq(KEY_USER_NAME), anyString()))
                .thenReturn("");

        boolean isSetupCompleted = preferencesManager.isSetupCompleted();
        
        assertFalse(isSetupCompleted);
    }
} 