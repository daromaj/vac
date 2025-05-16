package com.example.vac.handlers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.vac.R;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles audio playback (TTS and pre-recorded files), managing TextToSpeech
 * and MediaPlayer instances and audio focus.
 */
public class AudioHandler {
    private static final String TAG = "AudioHandler";
    private static final String UTTERANCE_ID_GREETING = "greeting";
    private static final String UTTERANCE_ID_FOLLOW_UP = "follow_up";
    private static final String UTTERANCE_ID_GENERIC = "generic";
    private static final String UTTERANCE_PREFIX_SYNTHESIS = "synthesis_";
    
    private final Context context;
    private final AudioHandlerListener listener;
    private final AudioManager audioManager;
    
    // Helper class to store synthesis request details
    private static class SynthesisRequest {
        SynthesisCallback callback;
        String filePath;

        SynthesisRequest(SynthesisCallback callback, String filePath) {
            this.callback = callback;
            this.filePath = filePath;
        }
    }
    // Map to hold requests for synthesizeToFile operations
    private final HashMap<String, SynthesisRequest> synthesisRequests = new HashMap<>();
    
    TextToSpeech tts; // Package-private for test access
    private MediaPlayer mediaPlayer;
    private AudioFocusRequest audioFocusRequest;
    private boolean isPlayingAudio = false;
    
    /**
     * Primary constructor used by the application.
     * Creates and initializes its own TextToSpeech engine.
     */
    public AudioHandler(Context context, AudioHandlerListener listener) {
        this(context, listener, null);
    }

    /**
     * Constructor that allows injecting a TextToSpeech engine (primarily for testing).
     * If ttsEngine is null, a new one will be created and initialized.
     * If ttsEngine is provided, it will be configured directly.
     */
    public AudioHandler(Context context, AudioHandlerListener listener, @Nullable TextToSpeech ttsEngine) {
        this.context = context;
        this.listener = listener;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (ttsEngine != null) {
            this.tts = ttsEngine;
            configureExistingTtsInstance();
        } else {
            initializeNewTts();
        }
    }
    
    protected TextToSpeech createTextToSpeech(Context context, TextToSpeech.OnInitListener listener) {
        try {
            return new TextToSpeech(context, listener);
        } catch (Exception e) {
            try { Log.e(TAG, "Failed to create TextToSpeech instance: " + e.getMessage()); } catch (Throwable t) {}
            return null; 
        }
    }
    
    /**
     * Creates and initializes a new TextToSpeech engine.
     */
    private void initializeNewTts() {
        this.tts = createTextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS && this.tts != null) {
                configureExistingTtsInstance();
            } else {
                try { Log.e(TAG, "TTS init failed, status: " + status + " or TTS null."); } catch (Throwable t) {}
                if (this.listener != null) {
                    this.listener.onPlaybackError("TTS initialization failed or engine unavailable.");
                }
            }
        });
        if (this.tts == null && this.listener != null) {
             try { Log.d(TAG, "TTS engine was null after creation attempt, notifying listener."); } catch (Throwable t) {}
             this.listener.onPlaybackError("TTS engine creation failed outright.");
        }
    }

    /**
     * Configures an already existing (and successfully initialized) TextToSpeech instance.
     * Sets language and utterance progress listener.
     */
    private void configureExistingTtsInstance() {
        if (this.tts == null) {
            try { Log.e(TAG, "configureExistingTtsInstance: TTS engine is null."); } catch (Throwable t) {}
            return;
        }
        try {
            int result = this.tts.setLanguage(new Locale("pl", "PL"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                try { Log.e(TAG, "Polish lang not supported for TTS."); } catch (Throwable t) {}
            } else {
                try { Log.i(TAG, "TTS language set to Polish."); } catch (Throwable t) {}
            }
        } catch (Exception e) { 
            try { Log.e(TAG, "Exception setting TTS language: " + e.getMessage()); } catch (Throwable t) {}
            this.tts = null; 
            if (this.listener != null) this.listener.onPlaybackError("TTS language configuration failed.");
            return; 
        }
        try {
            this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    if (synthesisRequests.containsKey(utteranceId)) {
                        try { Log.d(TAG, "TTS synthesis started for utteranceId: " + utteranceId); } catch (Throwable t) {}
                    } else {
                        isPlayingAudio = true;
                        if (AudioHandler.this.listener != null) {
                            AudioHandler.this.listener.onPlaybackStarted();
                        }
                    }
                }
                
                @Override
                public void onDone(String utteranceId) {
                    SynthesisRequest request = synthesisRequests.remove(utteranceId);
                    if (request != null) {
                        try { Log.d(TAG, "TTS synthesis onDone for utteranceId: " + utteranceId + ", file: " + request.filePath); } catch (Throwable t) {}
                        request.callback.onSuccess(request.filePath);
                    } else {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (AudioHandler.this.listener != null) {
                            AudioHandler.this.listener.onPlaybackCompleted();
                        }
                    }
                }
                
                @Override
                public void onError(String utteranceId) {
                    SynthesisRequest request = synthesisRequests.remove(utteranceId);
                    if (request != null) {
                        try { Log.e(TAG, "TTS synthesis onError for utteranceId: " + utteranceId); } catch (Throwable t) {}
                        request.callback.onError("TTS synthesis error for utterance: " + utteranceId);
                    } else {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (AudioHandler.this.listener != null) {
                            AudioHandler.this.listener.onPlaybackError("TTS error for utterance: " + utteranceId);
                        }
                    }
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    SynthesisRequest request = synthesisRequests.remove(utteranceId);
                    if (request != null) {
                        try { Log.e(TAG, "TTS synthesis onError for utteranceId: " + utteranceId + ", code: " + errorCode); } catch (Throwable t) {}
                        request.callback.onError("TTS synthesis error for utterance: " + utteranceId + ", code: " + errorCode);
                    } else {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (AudioHandler.this.listener != null) {
                            AudioHandler.this.listener.onPlaybackError("TTS error for utterance: " + utteranceId +
                                                            ", code: " + errorCode);
                        }
                    }
                }
            });
        } catch (Exception e) { 
            try { Log.e(TAG, "Exception setting TTS Listener: " + e.getMessage()); } catch (Throwable t) {}
            this.tts = null; 
            if (this.listener != null) this.listener.onPlaybackError("TTS listener configuration failed.");
        }
    }
    
    /**
     * Play the greeting to the caller using the provided full greeting text.
     *
     * @param fullGreetingText The complete greeting string to be spoken.
     */
    public void playGreeting(String fullGreetingText) {
        if (this.tts == null) {
            try { Log.e(TAG, "playGreeting: TTS engine not available."); } catch (Throwable t) {}
            if (listener != null) listener.onPlaybackError("TTS engine not available.");
            return;
        }
        if (fullGreetingText == null || fullGreetingText.isEmpty()) {
            try { Log.e(TAG, "playGreeting: Greeting text is empty."); } catch (Throwable t) {}
            if (listener != null) listener.onPlaybackError("Greeting text is empty.");
            return;
        }
        if (isPlayingAudio) {
             try { Log.w(TAG, "playGreeting called while already playing audio."); } catch (Throwable t) {}
            stopPlayback();
        }
        try { Log.i(TAG, "Attempting to play greeting: " + fullGreetingText); } catch (Throwable t) {}
        if (requestAudioFocus()) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_GREETING);
            
            // Ensure TTS engine is ready (it should be if initializeTts was successful)
            if (tts != null) {
                try {
                    tts.speak(fullGreetingText, TextToSpeech.QUEUE_FLUSH, params);
                } catch (Exception e) {
                    try { Log.e(TAG, "Exception during tts.speak(): " + e.getMessage()); } catch (Throwable t) {}
                    if (listener != null) {
                        listener.onPlaybackError("TTS speak operation failed.");
                    }
                    releaseAudioFocus(); // Release focus if speaking failed
                }
            } else {
                try { Log.e(TAG, "TTS engine not available in playGreeting."); } catch (Throwable t) {}
                if (listener != null) {
                    listener.onPlaybackError("TTS engine not available.");
                }
                releaseAudioFocus(); // Release focus if we can't speak
            }
        } else {
             try { Log.e(TAG, "Failed to get audio focus for greeting."); } catch (Throwable t) {}
            if (listener != null) listener.onPlaybackError("Failed to get audio focus for greeting");
        }
    }
    
    /**
     * Play the follow-up response to the caller
     */
    public void playFollowUpResponse() {
        if (isPlayingAudio) {
            stopPlayback();
        }
        
        // In a more sophisticated app, this would be customizable, but for MVP it's hardcoded
        String followUpText = "Thank you. I'll make sure your message gets delivered. " +
                             "Please leave a message after the beep.";
        
        // Request audio focus and play TTS
        if (requestAudioFocus()) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_FOLLOW_UP);
            
            tts.speak(followUpText, TextToSpeech.QUEUE_FLUSH, params);
        } else {
            if (listener != null) {
                listener.onPlaybackError("Failed to get audio focus for follow-up");
            }
        }
    }
    
    /**
     * Play an audio file from the provided URI
     * 
     * @param audioUri The URI of the audio file to play
     */
    public void playAudioFile(Uri audioUri) {
        if (audioUri == null) {
            try { Log.e(TAG, "playAudioFile: audioUri is null."); } catch (Throwable t) {}
            if (listener != null) listener.onPlaybackError("Audio URI is null.");
            return;
        }
        if (isPlayingAudio) {
            try { Log.w(TAG, "playAudioFile called while already playing audio."); } catch (Throwable t) {}
            stopPlayback(); // Stop any current playback
        }

        try { Log.i(TAG, "Attempting to play audio file: " + audioUri.toString()); } catch (Throwable t) {}
        if (requestAudioFocus()) {
            mediaPlayer = new MediaPlayer();

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // Suitable for spoken greetings
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setOnPreparedListener(mp -> {
                try { Log.d(TAG, "MediaPlayer prepared, starting playback."); } catch (Throwable t) {}
                isPlayingAudio = true;
                mp.start();
                if (listener != null) {
                    listener.onPlaybackStarted();
                }
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                isPlayingAudio = false;
                releaseAudioFocus();
                if (listener != null) {
                    listener.onPlaybackCompleted();
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                isPlayingAudio = false;
                releaseAudioFocus();
                if (listener != null) {
                    listener.onPlaybackError("MediaPlayer error: what=" + what + ", extra=" + extra);
                }
                return true;
            });
            
            mediaPlayer.prepareAsync();
        } else {
             try { Log.e(TAG, "Failed to get audio focus for audio file."); } catch (Throwable t) {}
            if (listener != null) {
                listener.onPlaybackError("Failed to get audio focus for audio file");
            }
        }
    }
    
    /**
     * Stop any ongoing playback
     */
    public void stopPlayback() {
        try {
            if (tts != null && tts.isSpeaking()) {
                tts.stop();
            }
        } catch (Exception e) {
            try { Log.e(TAG, "Exception during tts.stop() or tts.isSpeaking(): " + e.getMessage()); } catch (Throwable t) {}
            // Don't propagate, primary goal is to stop audio and release focus
        }
        
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.reset();
            }
        } catch (Exception e) {
            try { Log.e(TAG, "Exception during mediaPlayer.stop()/reset(): " + e.getMessage()); } catch (Throwable t) {}
        }
        
        isPlayingAudio = false;
        releaseAudioFocus();
    }
    
    /**
     * Request audio focus for playback
     * 
     * @return true if audio focus was granted, false otherwise
     */
    private boolean requestAudioFocus() {
        int result;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            stopPlayback();
                        }
                    })
                    .build();
            
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            stopPlayback();
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
        
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    
    /**
     * Release audio focus when done with playback
     */
    private void releaseAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        stopPlayback(); // stopPlayback is now more robust
        
        try {
            if (tts != null) {
                tts.shutdown();
            }
        } catch (Exception e) {
            try { Log.e(TAG, "Exception during tts.shutdown(): " + e.getMessage()); } catch (Throwable t) {}
        }
        tts = null; // Ensure tts is null after release
        
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
        } catch (Exception e) {
            try { Log.e(TAG, "Exception during mediaPlayer.release(): " + e.getMessage()); } catch (Throwable t) {}
        }
        mediaPlayer = null; // Ensure mediaPlayer is null after release
    }
    
    /**
     * Interface for AudioHandler to communicate with CallSessionManager
     */
    public interface AudioHandlerListener {
        void onPlaybackStarted();
        void onPlaybackCompleted();
        void onPlaybackError(String errorMessage);
    }

    /**
     * Interface for AudioHandler to communicate synthesis results.
     */
    public interface SynthesisCallback {
        void onSuccess(String filePath);
        void onError(String errorMessage);
    }

    /**
     * Synthesizes the given text to an audio file in the app's private directory.
     *
     * @param textToSpeak The text to synthesize.
     * @param desiredFileName The desired name for the output file (e.g., "custom_greeting.wav").
     * @param callback      Callback to notify of success (with file path) or failure.
     */
    public void synthesizeGreetingToFile(String textToSpeak, String desiredFileName, SynthesisCallback callback) {
        if (this.tts == null) {
            try { Log.e(TAG, "synthesizeGreetingToFile: TTS engine not available."); } catch (Throwable t) {}
            if (callback != null) callback.onError("TTS engine not available for synthesis.");
            return;
        }
        if (textToSpeak == null || textToSpeak.isEmpty()) {
            try { Log.e(TAG, "synthesizeGreetingToFile: Text to speak is empty."); } catch (Throwable t) {}
            if (callback != null) callback.onError("Text to speak is empty.");
            return;
        }
        if (desiredFileName == null || desiredFileName.isEmpty()) {
            try { Log.e(TAG, "synthesizeGreetingToFile: Desired filename is empty."); } catch (Throwable t) {}
            if (callback != null) callback.onError("Desired filename is empty.");
            return;
        }

        File outputDir = context.getFilesDir();
        if (outputDir == null) {
            try { Log.e(TAG, "synthesizeGreetingToFile: App files directory is null."); } catch (Throwable t) {}
            if (callback != null) callback.onError("App files directory is unavailable.");
            return;
        }
        
        File outputFile = new File(outputDir, desiredFileName);
        String utteranceId = UTTERANCE_PREFIX_SYNTHESIS + UUID.randomUUID().toString();

        // Store the callback and file path before starting synthesis
        synthesisRequests.put(utteranceId, new SynthesisRequest(callback, outputFile.getAbsolutePath()));

        try {
            Log.i(TAG, "Attempting to synthesize to file: " + outputFile.getAbsolutePath() + " with utteranceId: " + utteranceId); }
        catch (Throwable t) {}

        int result = tts.synthesizeToFile(textToSpeak, null, outputFile, utteranceId);
        
        if (result == TextToSpeech.ERROR) {
            try { Log.e(TAG, "TTS synthesizeToFile failed immediately for utteranceId: " + utteranceId); } catch (Throwable t) {}
            synthesisRequests.remove(utteranceId); // Clean up map
            if (callback != null) callback.onError("TTS synthesizeToFile failed immediately.");
        } else {
            // Success or pending, UtteranceProgressListener will handle onDone/onError for this utteranceId
            try { Log.d(TAG, "TTS synthesizeToFile call successful (pending completion) for utteranceId: " + utteranceId); } catch (Throwable t) {}
        }
    }
} 