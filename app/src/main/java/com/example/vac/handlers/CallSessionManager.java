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
        
        // Start recording the call immediately
        startRecordingMessage();
        
        // Then start the greeting
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
        if (userHasTakenOver || speechRecognitionHandler == null) {
            Log.d(TAG, "Cannot start listening: user takeover or null speech handler. UserTakeover: " + userHasTakenOver);
            return;
        }
        if (currentState == State.LISTENING && speechRecognitionHandler.isListening()) {
            Log.d(TAG, "Already listening, not starting again.");
            return;
        }

        Log.d(TAG, "Transitioning to LISTENING state and starting STT.");
        currentState = State.LISTENING;
        if (notificationHandler != null && context != null) {
            notificationHandler.updateNotificationMessage(context.getString(R.string.notification_message_listening));
        }
        speechRecognitionHandler.startListening("pl-PL"); // Assuming Polish for now

        // Restart STT silence timeout
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            Log.d(TAG, "Starting STT silence timeout (" + STT_SILENCE_TIMEOUT_MS + "ms).");
            sttTimeoutHandler.postDelayed(sttTimeoutRunnable, STT_SILENCE_TIMEOUT_MS);
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
     * Start recording the call
     * Records the entire call from start to finish
     */
    /* package */ void startRecordingMessage() {
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
        if (userHasTakenOver) {
            Log.d(TAG, "User has taken over, not processing playback completion further.");
            return;
        }

        switch (currentState) {
            case GREETING:
                Log.d(TAG, "Greeting playback completed. Starting STT.");
                startListeningForCaller(); // Consolidate notification update into startListeningForCaller
                break;
            case RESPONDING: // Added case for RESPONDING
                Log.d(TAG, "Assistant response playback completed. Transitioning back to LISTENING.");
                startListeningForCaller();
                break;
            case RECORDING_MESSAGE:
                // This case handles completion of "Please leave a message..." prompt
                Log.d(TAG, "Playback of 'leave message' prompt completed. Already in RECORDING_MESSAGE state.");
                break;
            default:
                Log.w(TAG, "Playback completed in unexpected state: " + currentState);
                break;
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

        if (userHasTakenOver) {
            Log.d(TAG, "User has taken over, ignoring speech result.");
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

        // If we were not in LISTENING state, it's unexpected, but let's try to recover if speech recognition was somehow active.
        if (currentState != State.LISTENING) {
            Log.w(TAG, "Speech result received but not in LISTENING state (current: " + currentState + "). Will attempt to process as if LISTENING.");
            // Not ideal, but might prevent stalling if SpeechRecognizer calls this unexpectedly.
        }
        
        currentState = State.RESPONDING;
        Log.d(TAG, "Transitioned to RESPONDING state.");
        if (notificationHandler != null && context != null) { // Ensure context and handler are available
            notificationHandler.updateNotificationMessage(context.getString(R.string.notification_responding));
        }


        String llmResponse = generateLlmPlaceholderResponse(transcribedText);
        if (audioHandler != null) {
            // Assuming UTTERANCE_ID_ASSISTANT_RESPONSE will be added to AudioHandler
            // For now, let's use a new distinct ID string or map it to an existing one like follow_up if appropriate.
            // We'll add AudioHandler.UTTERANCE_ID_ASSISTANT_RESPONSE later.
            audioHandler.speak(llmResponse, "UTTERANCE_ID_ASSISTANT_RESPONSE", Locale.getDefault().toLanguageTag());
        } else {
            Log.e(TAG, "AudioHandler is null, cannot speak LLM response.");
            // Consider error handling or fallback: maybe go back to listening or play follow-up?
            // For now, it will stall here if audioHandler is null.
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
        Log.e(TAG, "onSpeechError: " + error + ", code: " + errorCode + " Current state: " + currentState);
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable); // Stop silence timeout on error
        }

        if (userHasTakenOver) {
            Log.d(TAG, "User has taken over, not processing speech error.");
            return;
        }

        // Only play follow-up if we were actively listening or just starting.
        if (currentState == State.LISTENING || currentState == State.INITIALIZING) { // INITIALIZING was a previous state before GREETING
            Log.i(TAG, "Playing follow-up response due to STT error in LISTENING/INITIALIZING state.");
            // playFollowUpResponse changes state to RESPONDING internally
            // Then onPlaybackCompleted for the follow-up will transition to LISTENING.
            playFollowUpResponse(); 
        } else {
            Log.w(TAG, "Speech error received but not in a state to play follow-up. Current state: " + currentState + ". No action taken beyond logging.");
            // If in RESPONDING, and STT somehow gave an error, this might be problematic.
            // For now, we don't transition state here unless we were LISTENING.
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
        // This method is kept for backward compatibility but is no longer used
        // Recording now continues until the call ends (no time limit)
        Log.d(TAG, "onRecordingLimitReached called but ignored - recording continues until call ends.");
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
        try { Log.d(TAG, "Releasing internal components. Due to user takeover: " + dueToUserTakeover + ". Current state: " + currentState); } catch (Throwable t) {}

        // Stop any ongoing TTS
        if (audioHandler != null) {
            Log.d(TAG, "Stopping TTS if active.");
            audioHandler.stopSpeaking();
        }

        // Stop STT timeout handler
        if (sttTimeoutHandler != null && sttTimeoutRunnable != null) {
            Log.d(TAG, "Removing STT timeout callbacks.");
            sttTimeoutHandler.removeCallbacks(sttTimeoutRunnable);
        }

        // Stop and release AudioHandler
        if (audioHandler != null) {
            Log.d(TAG, "Releasing AudioHandler.");
            audioHandler.release();
            audioHandler = null;
        }

        // Stop and release SpeechRecognitionHandler
        if (speechRecognitionHandler != null) {
            speechRecognitionHandler.release();
            speechRecognitionHandler = null;
        }

        // Stop and release MessageRecorderHandler
        if (messageRecorderHandler != null) {
            messageRecorderHandler.stopRecording(); // Ensure recording is stopped
            messageRecorderHandler.release();
            messageRecorderHandler = null;
        }
        
        Log.d(TAG, "releaseInternal completed.");
        // Note: context and listener are not nulled here as they are final and passed in.
        // The manager instance itself should be dereferenced by its owner when no longer needed.
    }

    // Method to generate placeholder LLM response
    private String generateLlmPlaceholderResponse(String lastTranscribedText) {
        Log.d(TAG, "LLM Placeholder: Generating response for input: '" + lastTranscribedText + "'");
        // For MVP, return a hardcoded response.
        // In the future, this would involve actual LLM interaction.
        // Make sure to add R.string.llm_placeholder_response to strings.xml
        // And R.string.notification_responding for the notification
        return context.getString(R.string.llm_placeholder_response); 
    }

    protected String getGreetingText() {
        String userName = preferencesManager.getUserName();
        String baseGreeting = preferencesManager.getGreetingText();
        String fullGreeting = baseGreeting;
        if (userName != null && !userName.isEmpty()) {
            fullGreeting = context.getString(R.string.greeting_format_with_name, baseGreeting, userName);
        }
        
        // Append recording notice only if not already present (case-insensitive)
        // And only if actual recording is planned (which it is by default after greeting)
        String lowerCaseGreeting = fullGreeting.toLowerCase(Locale.ROOT);
        String lowerCaseNotice = POLISH_RECORDING_NOTICE.toLowerCase(Locale.ROOT); // Assuming notice is in Polish
        String keyphraseNotice = POLISH_RECORDING_KEYPHRASE.toLowerCase(Locale.ROOT);


        if (!lowerCaseGreeting.contains(lowerCaseNotice) && !lowerCaseGreeting.contains(keyphraseNotice)) {
            fullGreeting += POLISH_RECORDING_NOTICE;
        }
        
        Log.d(TAG, "Final greeting text: " + fullGreeting);
        return fullGreeting;
    }
} 