package com.example.vac.handlers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telecom.Call;
import android.util.Log;
import android.net.Uri;
import android.os.Handler;

import com.example.vac.R;
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
        
        this.preferencesManager = createPreferencesManager(context);
        this.sttTimeoutHandler = new Handler(Looper.getMainLooper());
        
        // Initialize components, including AudioHandler
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
        try { Log.d(TAG, "Starting call screening process for: " + callDetails.getHandle().getSchemeSpecificPart()); } catch (Throwable t) {}
        currentState = State.GREETING;
        
        // CallScreeningServiceImpl is responsible for the initial notification and foreground service start.
        // This class will only update the notification as states change.
        // PendingIntent takeOverPendingIntent = createTakeOverPendingIntent(); 
        // notificationHandler.showScreeningNotification(
        // "Starting call assistant...", 
        // takeOverPendingIntent, 
        // null // No hangup from here initially
        // );
        
        startGreeting();
    }
    
    /**
     * Stop the call screening process (e.g. call disconnected, screening finished).
     * This method is for non-user-initiated stops.
     * For user take-over, see {@link #userTakesOver()}.
     */
    public void stopScreening() {
        Log.d(TAG, "Stopping call screening process (general stop). Current state: " + currentState);
        if (currentState == State.ENDED) {
            Log.d(TAG, "Already in ENDED state. Ignoring stopScreening call.");
            return;
        }
        releaseInternal(false); // false indicates not due to user takeover
        currentState = State.ENDED; // Set state after release, or within releaseInternal
        if (listener != null) {
            listener.onSessionCompleted(this);
        }
    }
    
    /**
     * User takes over the call
     */
    public void userTakesOver() {
        Log.d(TAG, "User is taking over the call. Current state: " + currentState);
        if (currentState == State.ENDED || userHasTakenOver) {
            Log.d(TAG, "User already took over or session ended. Ignoring userTakesOver call.");
            return;
        }

        // Set state and flag immediately
        currentState = State.USER_TAKEOVER;
        userHasTakenOver = true;

        releaseInternal(true); // true indicates due to user takeover

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
        try { Log.i(TAG, "Transitioning to LISTENING state."); } catch (Throwable t) {}
        currentState = State.LISTENING;
        // Update notification to indicate listening, no actions initially
        notificationHandler.updateNotification(
            context.getString(R.string.notification_title_screening),
            "Listening to caller...", 
            null 
        );
        if (speechRecognitionHandler != null) {
            speechRecognitionHandler.startListening("pl-PL");
        }
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
        Log.d(TAG, "onPlaybackCompleted. Current state: " + currentState);
        if (currentState == State.GREETING) {
            Log.d(TAG, "Greeting playback completed. Starting STT.");
            if (notificationHandler != null) {
                // Ensure context is available for getString
                String listeningMessage = (context != null) ? context.getString(R.string.notification_message_listening) : "Listening to caller...";
                notificationHandler.updateNotificationMessage(listeningMessage);
            }
            speechRecognitionHandler.startListening("pl-PL");
            currentState = State.LISTENING;
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
        stopScreening(); // Stop the session on playback error
    }
    
    // SpeechRecognitionCallbacks implementation
    
    @Override
    public void onReadyForSpeech() {
        try { Log.i(TAG, "Speech Recognizer ready."); } catch (Throwable t) {}
        // Optionally update notification
        notificationHandler.updateNotification(
            context.getString(R.string.notification_title_screening),
            "Listening...", 
            null
        );
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
    public void onSpeechError(String error, int errorCode) {
        Log.e(TAG, "onSpeechError: " + error + ", code: " + errorCode);
        sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
        if (currentState == State.LISTENING || currentState == State.INITIALIZING) {
            audioHandler.playFollowUpResponse();
            currentState = State.RESPONDING;
        } else {
            Log.w(TAG, "Speech error received but not in a state to play follow-up. Current state: " + currentState);
        }
    }
    
    // MessageRecorderListener implementation
    
    @Override
    public void onRecordingStarted() {
        try { Log.i(TAG, "Message recording started."); } catch (Throwable t) {}
        currentState = State.RECORDING_MESSAGE;
        notificationHandler.updateNotification(
            context.getString(R.string.notification_title_screening),
            "Recording message...", 
            null
        );
    }
    
    @Override
    public void onRecordingStopped(String filePath, boolean successfullyCompleted) {
        try { Log.i(TAG, "Message recording stopped. File: " + filePath + ", Success: " + successfullyCompleted); } catch (Throwable t) {}
        if (successfullyCompleted && filePath != null) {
            notificationHandler.updateNotification(
                context.getString(R.string.notification_title_screening),
                "Message recorded: " + new File(filePath).getName(), 
                null
            );
            // TODO: Handle the recorded message (e.g., save path to DB, notify user)
        } else {
            notificationHandler.updateNotification(
                context.getString(R.string.notification_title_screening),
                "Recording failed or stopped.", 
                null
            );
        }
        // After recording, the session typically ends.
        stopScreening(); 
    }
    
    @Override
    public void onRecordingError(String errorMessage) {
        Log.e(TAG, "MessageRecorderListener: onRecordingError: " + errorMessage);
        // Potentially stop other operations and inform the user or end the call.
        // For now, notify the main listener about the error.
        releaseInternal(false); // Or a specific error state cleanup
        currentState = State.ENDED; // Or a new ERROR state
        if (listener != null) {
            listener.onSessionError(this, "Recording error: " + errorMessage);
        }
    }
    
    @Override
    public void onRecordingLimitReached() {
        try { Log.w(TAG, "Recording time limit reached."); } catch (Throwable t) {}
        notificationHandler.updateNotification(
            context.getString(R.string.notification_title_screening),
            "Recording limit reached. Saving message.", 
            null
        );
        if (messageRecorderHandler != null) {
            messageRecorderHandler.stopRecording(); // Ensure recording is stopped
            messageRecorderHandler.release();
            messageRecorderHandler = null;
        }
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

    /**
     * Instructs the system to hang up the call. This should typically be used when the assistant
     * determines the call should not proceed (e.g., spam) or when the user initiates a hang-up
     * via the notification.
     * This method will also trigger the cleanup of the current session.
     */
    public void hangUpCall() {
        Log.i(TAG, "hangUpCall invoked. Current state: " + currentState);
        if (currentState == State.ENDED || userHasTakenOver) {
            Log.d(TAG, "Call already ended or user took over. Ignoring hangUpCall.");
            return;
        }

        // Inform the listener (CallScreeningServiceImpl) that a hang-up is requested.
        // The listener is then responsible for interacting with the Telecom framework
        // to actually reject or disconnect the call.
        if (listener != null) {
            // We don't have a specific callback for hangup, so we use onSessionCompleted
            // or onSessionError. For now, let's consider it a form of session completion
            // initiated by us.
            // CallScreeningServiceImpl will need to see userHasTakenOver = false and then issue a disallow/reject.
            // Alternatively, add a specific listener.onHangUpRequested(this) if more direct control is needed.
             Log.d(TAG, "Requesting hangup; will call stopScreening which notifies onSessionCompleted.");
        }

        // The actual call to Telecom to disallow/reject should happen in CallScreeningServiceImpl
        // after it receives onSessionCompleted (and checks userHasTakenOver is false).
        // For now, CallSessionManager just cleans itself up.
        stopScreening(); // This will call releaseInternal and notify onSessionCompleted.
    }

    // Make this public so CallScreeningServiceImpl can call it if a session needs premature release.
    public void releaseInternal(boolean dueToUserTakeover) {
        try { Log.d(TAG, "Releasing internal resources. Due to user takeover: " + dueToUserTakeover + ". Current state: " + currentState); } catch (Throwable t) {}

        // Stop any pending STT timeout callbacks
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
            // sttTimeoutHandler = null; // Don't null if it might be reused, but CallSessionManager is single-use.
            // sttTimeoutRunnable = null;
        }

        // Stop and release AudioHandler
        if (audioHandler != null) {
            audioHandler.stopPlayback();
            if (!dueToUserTakeover || currentState == State.USER_TAKEOVER) { // Release fully unless specific conditions
                audioHandler.release();
                audioHandler = null;
            }
        }

        // Stop and release SpeechRecognitionHandler
        if (speechRecognitionHandler != null) {
            speechRecognitionHandler.stopListening();
             if (!dueToUserTakeover || currentState == State.USER_TAKEOVER) { // Release fully
                speechRecognitionHandler.release();
                speechRecognitionHandler = null;
            }
        }

        // Stop and release MessageRecorderHandler
        if (messageRecorderHandler != null) {
            messageRecorderHandler.stopRecording(); // Ensure recording is stopped
            if (!dueToUserTakeover || currentState == State.USER_TAKEOVER) { // Release fully
                 messageRecorderHandler.release();
                 messageRecorderHandler = null;
            }
        }
        
        Log.d(TAG, "releaseInternal completed.");
        // Note: context and listener are not nulled here as they are final and passed in.
        // The manager instance itself should be dereferenced by its owner when no longer needed.
    }
} 