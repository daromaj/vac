package com.example.vac.handlers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.util.Log;

import com.example.vac.utils.PreferencesManager;

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
        
        // Get user preferences
        this.preferencesManager = new PreferencesManager(context);
        
        // Initialize components
        initializeComponents();
    }
    
    private void initializeComponents() {
        audioHandler = new AudioHandler(context, this);
        speechRecognitionHandler = new SpeechRecognitionHandler(context, this);
        messageRecorderHandler = new MessageRecorderHandler(context, this);
    }
    
    /**
     * Start the call screening process
     */
    public void startScreening() {
        Log.d(TAG, "Starting call screening process");
        
        // Show notification with "Take Over" action
        Intent takeOverIntent = new Intent(ACTION_TAKE_OVER);
        PendingIntent takeOverPendingIntent = PendingIntent.getBroadcast(
                context, 0, takeOverIntent, PendingIntent.FLAG_IMMUTABLE);
        
        notificationHandler.showScreeningNotification(
                "Starting call assistant...", 
                takeOverPendingIntent);
        
        // Start with greeting
        playGreeting();
    }
    
    /**
     * Stop the call screening process
     * 
     * @param isTakeOver Whether this is because the user took over
     */
    public void stopScreening(boolean isTakeOver) {
        Log.d(TAG, "Stopping call screening process, takeOver=" + isTakeOver);
        
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
        Log.d(TAG, "User is taking over the call");
        
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
     * Play the initial greeting to the caller
     */
    private void playGreeting() {
        currentState = State.GREETING;
        
        // Get user name and custom greeting from preferences
        String userName = preferencesManager.getUserName();
        String customGreeting = preferencesManager.getGreetingText();
        
        // Play the greeting
        audioHandler.playGreeting(userName, customGreeting);
    }
    
    /**
     * Start listening for the caller's response
     */
    private void startListeningForCaller() {
        currentState = State.LISTENING;
        
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
        Log.d(TAG, "Audio playback started");
    }
    
    @Override
    public void onPlaybackCompleted() {
        Log.d(TAG, "Audio playback completed");
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            return;
        }
        
        // Handle next step based on current state
        switch (currentState) {
            case GREETING:
                // After greeting, start listening for caller's response
                startListeningForCaller();
                break;
                
            case RESPONDING:
                // After follow-up response, start recording message
                startRecordingMessage();
                break;
                
            default:
                // Nothing to do for other states
                break;
        }
    }
    
    @Override
    public void onPlaybackError(String errorMessage) {
        Log.e(TAG, "Audio playback error: " + errorMessage);
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            return;
        }
        
        // Notify listener of error
        if (listener != null) {
            listener.onSessionError(this, "Audio playback error: " + errorMessage);
        }
    }
    
    // SpeechRecognitionCallbacks implementation
    
    @Override
    public void onReadyForSpeech() {
        Log.d(TAG, "Ready for speech");
    }
    
    @Override
    public void onSpeechResult(String transcribedText) {
        Log.d(TAG, "Speech result: " + transcribedText);
        
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
        Log.d(TAG, "End of speech detected");
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            return;
        }
        
        // After speech ends, play follow-up response
        // In a more sophisticated implementation, we could add a delay here
        playFollowUpResponse();
    }
    
    @Override
    public void onSpeechError(String errorMessage, int errorCode) {
        Log.e(TAG, "Speech recognition error: " + errorMessage + " (code: " + errorCode + ")");
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            return;
        }
        
        // Handle speech errors (like timeout) by moving to the next phase
        playFollowUpResponse();
    }
    
    // MessageRecorderListener implementation
    
    @Override
    public void onRecordingStarted() {
        Log.d(TAG, "Message recording started");
        
        // Update notification
        notificationHandler.updateNotification("Recording message...");
    }
    
    @Override
    public void onRecordingStopped(String filePath, boolean successfullyCompleted) {
        Log.d(TAG, "Message recording stopped, success=" + successfullyCompleted + ", path=" + filePath);
        
        // If we're in user takeover mode, just log this
        if (userHasTakenOver) {
            Log.d(TAG, "Message recorded while user had taken over");
            return;
        }
        
        // For MVP, we're done after message is recorded
        notificationHandler.updateNotification("Call completed and message saved");
        
        // Notify listener that session is complete
        if (listener != null) {
            listener.onSessionCompleted(this);
        }
    }
    
    @Override
    public void onRecordingError(String errorMessage) {
        Log.e(TAG, "Recording error: " + errorMessage);
        
        // If user has taken over, just log this
        if (userHasTakenOver) {
            Log.e(TAG, "Recording error while user had taken over: " + errorMessage);
            return;
        }
        
        // Notify listener of error
        if (listener != null) {
            listener.onSessionError(this, "Recording error: " + errorMessage);
        }
    }
    
    @Override
    public void onRecordingLimitReached() {
        Log.d(TAG, "Message recording limit reached");
        
        // Update notification
        notificationHandler.updateNotification("Message recording complete (limit reached)");
        
        // If user has taken over, just log this
        if (userHasTakenOver) {
            return;
        }
        
        // Notify listener that session is complete
        if (listener != null) {
            listener.onSessionCompleted(this);
        }
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