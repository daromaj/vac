package com.example.vac.handlers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telecom.Call;
import android.util.Log;
import android.net.Uri;
import android.os.Handler;

import com.example.vac.utils.PreferencesManager;

import java.io.File;
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
    private static final String POLISH_RECORDING_NOTICE = " Ta rozmowa jest nagrywana.";
    private static final String POLISH_RECORDING_KEYPHRASE = "rozmowa jest nagrywana";
    private static final long STT_SILENCE_TIMEOUT_MS = 3000; // 3 seconds
    
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
    
    private Handler sttTimeoutHandler;
    private Runnable sttTimeoutRunnable;
    private String lastTranscribedText = null;
    
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
        
        this.sttTimeoutHandler = new Handler(Looper.getMainLooper());
        
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

        sttTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState == State.LISTENING && !userHasTakenOver) {
                    Log.i(TAG, "STT silence timeout reached. Current last transcribed text: '" + lastTranscribedText + "'. Proceeding to follow-up.");
                    playFollowUpResponse();
                } else {
                    Log.d(TAG, "STT silence timeout runnable executed but state is no longer LISTENING or user has taken over. State: " + currentState + ", UserTakenOver: " + userHasTakenOver);
                }
            }
        };
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
        
        // Remove any pending STT timeout callbacks
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
        }

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
        
        if (currentState == State.ENDED || userHasTakenOver) {
            Log.d(TAG, "User already took over or session ended. Ignoring userTakesOver call.");
            return;
        }
        
        // Remove any pending STT timeout callbacks
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
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
        boolean useCustomFile = preferencesManager.shouldUseCustomGreetingFile();
        String customFilePath = null;
        File greetingFile = null;

        if (useCustomFile) {
            customFilePath = preferencesManager.getCustomGreetingFilePath();
            if (customFilePath != null && !customFilePath.isEmpty()) {
                greetingFile = new File(customFilePath);
                if (!greetingFile.exists() || !greetingFile.canRead()) {
                    try { Log.w(TAG, "Custom greeting file not found or not readable: " + customFilePath + ". Falling back to TTS."); } catch (Throwable t) {}
                    greetingFile = null; // Invalidate if not usable
                }
            } else {
                 try { Log.w(TAG, "'Use custom file' is true, but no path is stored. Falling back to TTS."); } catch (Throwable t) {}
            }
        }

        if (greetingFile != null) {
            try { Log.i(TAG, "Playing custom greeting file: " + greetingFile.getAbsolutePath()); } catch (Throwable t) {}
            if (audioHandler != null) {
                audioHandler.playAudioFile(Uri.fromFile(greetingFile));
            }
        } else {
            try { Log.i(TAG, "Falling back to TTS for greeting."); } catch (Throwable t) {}
            String userName = preferencesManager.getUserName();
            String baseGreetingText = preferencesManager.getGreetingText();
            String fullGreetingText;

            if (baseGreetingText != null && !baseGreetingText.trim().isEmpty()) {
                // User has provided a custom greeting text
                if (!baseGreetingText.toLowerCase(Locale.ROOT).contains(POLISH_RECORDING_KEYPHRASE)) {
                    fullGreetingText = baseGreetingText + POLISH_RECORDING_NOTICE;
                } else {
                    fullGreetingText = baseGreetingText;
                }
            } else {
                // No custom greeting text, use the default Polish greeting from strings.xml
                // This default greeting already contains the recording notice.
                String namePart = (userName != null && !userName.isEmpty()) ? userName : ""; // Use empty string for formatting if name is null/empty
                fullGreetingText = String.format(context.getString(com.example.vac.R.string.default_greeting), namePart);
            }
            
            if (audioHandler != null) {
                audioHandler.playGreeting(fullGreetingText);
            }
        }
        currentState = State.GREETING;
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
        try { Log.i(TAG, "Speech recognized: " + transcribedText + " Current state: " + currentState); } catch (Throwable t) {}
        
        // If user has taken over, do nothing
        if (userHasTakenOver) {
            Log.d(TAG, "User has taken over, ignoring speech result.");
            return;
        }

        if (currentState != State.LISTENING) {
            Log.w(TAG, "Speech result received but not in LISTENING state. Ignoring. State: " + currentState);
            return;
        }

        // IMPORTANT: Cancel any pending silence timeout because we got a result.
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
            Log.d(TAG, "Cancelled STT silence timeout due to new speech result.");
        }
        
        this.lastTranscribedText = transcribedText; // Store the latest text

        // Update notification with transcription
        if (listener != null) {
            listener.onTranscriptionUpdate(transcribedText);
        }
    }
    
    @Override
    public void onEndOfSpeech() {
        try { Log.d(TAG, "End of speech detected by recognizer. Current state: " + currentState); } catch (Throwable t) {}
        // If in LISTENING state, and no result yet, might mean silence or non-speech.
        // Depending on strategy, could re-prompt, wait, or move to voicemail.
        // For MVP, if we get here after listening, we assume we should play follow-up.
        if (currentState == State.LISTENING && !userHasTakenOver) {
            // Don't play follow-up immediately. Start the timer.
            // The runnable will play follow-up if no new results come in.
            Log.i(TAG, "End of speech in LISTENING state. Starting silence timer (" + STT_SILENCE_TIMEOUT_MS + "ms) to play follow-up.");
            if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
                 // Clear previous one just in case (e.g. multiple onEndOfSpeech calls rapidly, though unlikely)
                sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
                sttTimeoutHandler.postDelayed(sttTimeoutRunnable, STT_SILENCE_TIMEOUT_MS);
            } else {
                Log.e(TAG, "sttTimeoutHandler or sttTimeoutRunnable is null, cannot start timeout.");
                // Fallback or error handling if handler/runnable isn't initialized?
                // For now, this case should ideally not happen with proper init.
                // If it does, we might play follow-up immediately as a fallback.
                playFollowUpResponse();
            }
        } else {
            Log.d(TAG, "End of speech detected, but not in LISTENING state or user has taken over. State: " + currentState + ", UserTakenOver: " + userHasTakenOver);
        }
    }
    
    @Override
    public void onSpeechError(String errorMessage, int errorCode) {
        try { Log.e(TAG, "Speech recognition error: " + errorMessage + ", code: " + errorCode + ". Current state: " + currentState); } catch (Throwable t) {}
        
        if (userHasTakenOver) {
            Log.d(TAG, "User has taken over, ignoring speech error.");
            return;
        }

        // Cancel any pending silence timeout on error.
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
            Log.d(TAG, "Cancelled STT silence timeout due to speech error.");
        }

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
    
    // Getter for testing purposes
    public State getCurrentState() {
        return currentState;
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

    public void releaseInternal() {
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
        }
        // ... other potential handler-specific cleanup before SUT fields are nulled ...
    }
} 