package com.example.vac.handlers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.util.Log;

import com.example.vac.utils.PreferencesManager;

import java.util.Locale;

/**
 * Manages the state and flow of a single screened call.
 * Orchestrates TTS, STT, and recording components.
 */
public class CallSessionManager implements 
        AudioHandler.AudioHandlerListener,
        SpeechRecognitionHandler.SpeechRecognitionCallbacks,
        MessageRecorderHandler.MessageRecorderListener {
    
    private static final String TAG = "CallSessionManager";
    private static final String ACTION_TAKE_OVER = "com.example.vac.TAKE_OVER";
    
    // States for the call screening process
    public enum State {
        INITIALIZING,
        GREETING,
        LISTENING,
        RESPONDING,
        RECORDING_MESSAGE,
        USER_TAKEOVER,
        ENDED
    }
    
    private final Context context;
    private final Call.Details callDetails;
    private final CallSessionListener listener;
    private final NotificationHandler notificationHandler;
    private final PreferencesManager preferencesManager;
    
    private AudioHandler audioHandler;
    private SpeechRecognitionHandler speechRecognitionHandler;
    private MessageRecorderHandler messageRecorderHandler;
    
    private State currentState = State.INITIALIZING;
    private boolean userHasTakenOver = false;
    
    public CallSessionManager(Context context, Call.Details callDetails, 
                             CallSessionListener listener, 
                             NotificationHandler notificationHandler) {
        this.context = context;
        this.callDetails = callDetails;
        this.listener = listener;
        this.notificationHandler = notificationHandler;
        
        // Initialize PreferencesManager using overridable method
        this.preferencesManager = createPreferencesManager(context);
        
        // Initialize components using overridable methods
        initializeComponents();
    }
    
    // Overridable factory methods for testing
    protected PreferencesManager createPreferencesManager(Context context) {
        return new PreferencesManager(context);
    }

    protected AudioHandler createAudioHandler(Context context, AudioHandler.AudioHandlerListener listener) {
        return new AudioHandler(context, listener);
    }

    protected SpeechRecognitionHandler createSpeechRecognitionHandler(Context context, SpeechRecognitionHandler.SpeechRecognitionCallbacks callbacks) {
        return new SpeechRecognitionHandler(context, callbacks);
    }

    protected MessageRecorderHandler createMessageRecorderHandler(Context context, MessageRecorderHandler.MessageRecorderListener listener) {
        return new MessageRecorderHandler(context, listener);
    }

    // Added for testability
    protected PendingIntent createTakeOverPendingIntent() {
        Intent takeOverIntent = new Intent(ACTION_TAKE_OVER);
        return PendingIntent.getBroadcast(
                context, 0, takeOverIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void initializeComponents() {
        // Use the factory methods to create handlers
        audioHandler = createAudioHandler(context, this);
        speechRecognitionHandler = createSpeechRecognitionHandler(context, this);
        messageRecorderHandler = createMessageRecorderHandler(context, this);
    }
    
    /**
     * Start the call screening process
     */
    public void startScreening() {
        try { Log.d(TAG, "Starting call screening process"); } catch (Throwable t) {}
        
        PendingIntent takeOverPendingIntent = createTakeOverPendingIntent();
        
        notificationHandler.showScreeningNotification(
                "Starting call assistant...", 
                takeOverPendingIntent);
        
        // Start with greeting
        startGreeting();
    }
    
    /**
     * Stop the call screening process
     * 
     * @param isTakeOver Whether this is because the user took over
     */
    public void stopScreening(boolean isTakeOver) {
        try { Log.d(TAG, "Stopping call screening process, takeOver=" + isTakeOver); } catch (Throwable t) {}
        
        // Set state to ENDED
        currentState = State.ENDED;
        
        // Release all resources
        if (audioHandler != null) {
            audioHandler.stopPlayback();
            audioHandler.release();
            audioHandler = null;
        }
        
        if (speechRecognitionHandler != null) {
            speechRecognitionHandler.stopListening();
            speechRecognitionHandler.release();
            speechRecognitionHandler = null;
        }
        
        if (messageRecorderHandler != null) {
            messageRecorderHandler.stopRecording();
            messageRecorderHandler.release();
            messageRecorderHandler = null;
        }
    }
    
    /**
     * User takes over the call
     */
    public void userTakesOver() {
        try { Log.d(TAG, "User is taking over the call"); } catch (Throwable t) {}
        
        if (currentState == State.ENDED) {
            return;
        }
        
        // Set state and flag
        currentState = State.USER_TAKEOVER;
        userHasTakenOver = true;
        
        // Stop audio playback
        if (audioHandler != null) {
            audioHandler.stopPlayback();
        }
        
        // Stop speech recognition
        if (speechRecognitionHandler != null) {
            speechRecognitionHandler.stopListening();
        }
        
        // Note: we do NOT stop message recording yet - it continues until call ends
        
        // Notify listener
        if (listener != null) {
            listener.onUserTookOver(this);
        }
    }
    
    /**
     * Start greeting the caller
     */
    public void startGreeting() {
        String userName = preferencesManager.getUserName();
        String fullGreetingText;
        
        // Construct the greeting message
        // "Hi, you\'ve reached [User\'s Name]\'s phone. This is their virtual assistant. This call is being recorded. How can I help you?"
        if (userName == null || userName.trim().isEmpty()) {
            // Fallback if user name is not set
            fullGreetingText = "Hi, you\'ve reached this phone. This is the virtual assistant. This call is being recorded. How can I help you?";
        } else {
            fullGreetingText = String.format(Locale.US, "Hi, you\'ve reached %s\'s phone. This is their virtual assistant. This call is being recorded. How can I help you?", userName);
        }
        try { Log.d(TAG, "Constructed greeting for AudioHandler: " + fullGreetingText); } catch (Throwable t) {}

        // Delegate to AudioHandler to play the greeting
        if (audioHandler != null) {
            currentState = State.GREETING; // Set state before playing
            audioHandler.playGreeting(fullGreetingText);
        } else {
            try { Log.e(TAG, "AudioHandler is null, cannot play greeting."); } catch (Throwable t) {}
            if (listener != null) {
                listener.onSessionError(this, "AudioHandler not available for greeting.");
            }
        }
    }
    
    /**
     * Start listening for the caller's response
     */
    private void startListeningForCaller() {
        currentState = State.LISTENING;
        try { Log.d(TAG, "Transitioned to LISTENING state."); } catch (Throwable t) {}
        
        // Update notification
        notificationHandler.updateNotification("Listening to caller...");
        
        // Start speech recognition
        speechRecognitionHandler.startListening("pl-PL");
    }
    
    /**
     * Play the follow-up response after caller speaks
     */
    private void playFollowUpResponse() {
        currentState = State.RESPONDING;
        
        // Play follow-up response
        audioHandler.playFollowUpResponse();
    }
    
    /**
     * Start recording the caller's message
     */
    private void startRecordingMessage() {
        currentState = State.RECORDING_MESSAGE;
        
        // Generate a filename for the recording
        String fileName = "message_" + System.currentTimeMillis() + ".3gp";
        
        // Start recording
        messageRecorderHandler.startRecording(fileName);
    }
    
    // AudioHandlerListener implementation
    
    @Override
    public void onPlaybackStarted() {
        try { Log.d(TAG, "Audio playback started (via AudioHandler)"); } catch (Throwable t) {}
        // Potentially update state or notification if needed
    }
    
    @Override
    public void onPlaybackCompleted() {
        try { Log.d(TAG, "Audio playback completed (via AudioHandler)"); } catch (Throwable t) {}
        if (currentState == State.GREETING) {
            try { Log.i(TAG, "Greeting playback completed. Proceeding to listen for caller."); } catch (Throwable t) {}
            startListeningForCaller(); 
        } else if (currentState == State.RESPONDING) {
            try { Log.i(TAG, "Follow-up response playback completed. Proceeding to record message."); } catch (Throwable t) {}
            startRecordingMessage();
        } else {
            try { Log.w(TAG, "Playback completed in unexpected state: " + currentState); } catch (Throwable t) {}
        }
    }
    
    @Override
    public void onPlaybackError(String errorMessage) {
        try { Log.e(TAG, "Audio playback error (via AudioHandler): " + errorMessage); } catch (Throwable t) {}
        if (listener != null) {
            listener.onSessionError(this, "Audio playback error: " + errorMessage);
        }
        stopScreening(false); // Stop the session on playback error
    }
    
    // SpeechRecognitionCallbacks implementation
    
    @Override
    public void onReadyForSpeech() {
        try { Log.d(TAG, "Speech recognizer ready for speech."); } catch (Throwable t) {}
        // Update notification to indicate listening
        notificationHandler.updateNotification("Listening...");
    }
    
    @Override
    public void onSpeechResult(String transcribedText) {
        try { Log.i(TAG, "Speech recognized: " + transcribedText); } catch (Throwable t) {}
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            return;
        }
        
        // Update notification with transcription
        if (listener != null) {
            listener.onTranscriptionUpdate(transcribedText);
        }
    }
    
    @Override
    public void onEndOfSpeech() {
        try { Log.d(TAG, "End of speech detected by recognizer."); } catch (Throwable t) {}
        // If in LISTENING state, and no result yet, might mean silence or non-speech.
        // Depending on strategy, could re-prompt, wait, or move to voicemail.
        // For MVP, if we get here after listening, we assume we should play follow-up.
        if (currentState == State.LISTENING) {
            try { Log.i(TAG, "End of speech in LISTENING state. Playing follow-up and preparing for message."); } catch (Throwable t) {}
            playFollowUpResponse();
        }
    }
    
    @Override
    public void onSpeechError(String errorMessage, int errorCode) {
        try { Log.e(TAG, "Speech recognition error: " + errorMessage + ", code: " + errorCode); } catch (Throwable t) {}
        // Handle speech errors, e.g., network issues, no match, etc.
        // For MVP, play follow-up and move to message recording on any error during listening.
        if (currentState == State.LISTENING) {
            try { Log.w(TAG, "Speech error in LISTENING state. Playing follow-up and preparing for message."); } catch (Throwable t) {}
            playFollowUpResponse(); 
        } else if (listener != null) {
            // If error occurs outside of active listening (e.g., init error), notify session listener.
            listener.onSessionError(this, "Speech recognition error: " + errorMessage);
            stopScreening(false);
        }
    }
    
    // MessageRecorderListener implementation
    
    @Override
    public void onRecordingStarted() {
        try { Log.i(TAG, "Message recording started."); } catch (Throwable t) {}
        notificationHandler.updateNotification("Recording message...");
    }
    
    @Override
    public void onRecordingStopped(String filePath, boolean successfullyCompleted) {
        if (successfullyCompleted) {
            try { Log.i(TAG, "Message recording stopped. File saved at: " + filePath); } catch (Throwable t) {}
            notificationHandler.updateNotification("Message recorded: " + filePath.substring(filePath.lastIndexOf('/') + 1));
        } else {
            try { Log.e(TAG, "Message recording stopped but failed to save or limit reached early."); } catch (Throwable t) {}
            notificationHandler.updateNotification("Recording failed or stopped.");
        }
        // For MVP, session ends after recording stops (either successfully or due to error/limit)
        if (listener != null) {
            listener.onSessionCompleted(this);
        }
        stopScreening(false); // Ensure session cleanup
    }
    
    @Override
    public void onRecordingError(String errorMessage) {
        try { Log.e(TAG, "Message recording error: " + errorMessage); } catch (Throwable t) {}
        notificationHandler.updateNotification("Recording error: " + errorMessage);
        if (listener != null) {
            listener.onSessionError(this, "Recording error: " + errorMessage);
        }
        stopScreening(false); // Session ends on recording error
    }
    
    @Override
    public void onRecordingLimitReached() {
        try { Log.i(TAG, "Message recording limit reached."); } catch (Throwable t) {}
        notificationHandler.updateNotification("Recording limit reached. Saving message.");
        // MessageRecorderHandler should stop recording automatically and onRecordingStopped will be called.
    }
    
    /**
     * Interface for CallSessionManager to communicate with CallScreeningService
     */
    public interface CallSessionListener {
        void onSessionCompleted(CallSessionManager session);
        void onSessionError(CallSessionManager session, String errorMessage);
        void onUserTookOver(CallSessionManager session);
        void onTranscriptionUpdate(String latestTranscript);
    }
} 