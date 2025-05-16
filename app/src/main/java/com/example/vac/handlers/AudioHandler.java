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

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

/**
 * Handles audio playback (TTS and pre-recorded files), managing TextToSpeech
 * and MediaPlayer instances and audio focus.
 */
public class AudioHandler {
    private static final String TAG = "AudioHandler";
    private static final String UTTERANCE_ID_GREETING = "greeting";
    private static final String UTTERANCE_ID_FOLLOW_UP = "follow_up";
    private static final String UTTERANCE_ID_GENERIC = "generic";
    
    private final Context context;
    private final AudioHandlerListener listener;
    private final AudioManager audioManager;
    
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
                    isPlayingAudio = true;
                    if (AudioHandler.this.listener != null) {
                        AudioHandler.this.listener.onPlaybackStarted();
                    }
                }
                
                @Override
                public void onDone(String utteranceId) {
                    isPlayingAudio = false;
                    releaseAudioFocus();
                    if (AudioHandler.this.listener != null) {
                        AudioHandler.this.listener.onPlaybackCompleted();
                    }
                }
                
                @Override
                public void onError(String utteranceId) {
                    isPlayingAudio = false;
                    releaseAudioFocus();
                    if (AudioHandler.this.listener != null) {
                        AudioHandler.this.listener.onPlaybackError("TTS error for utterance: " + utteranceId);
                    }
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    isPlayingAudio = false;
                    releaseAudioFocus();
                    if (AudioHandler.this.listener != null) {
                        AudioHandler.this.listener.onPlaybackError("TTS error for utterance: " + utteranceId +
                                                        ", code: " + errorCode);
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
        if (isPlayingAudio) {
            stopPlayback();
        }
        
        try {
            // Set up MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build());
            
            mediaPlayer.setDataSource(context, audioUri);
            mediaPlayer.setOnPreparedListener(mp -> {
                if (requestAudioFocus()) {
                    isPlayingAudio = true;
                    mp.start();
                    if (listener != null) {
                        listener.onPlaybackStarted();
                    }
                } else {
                    if (listener != null) {
                        listener.onPlaybackError("Failed to get audio focus for audio file");
                    }
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
        } catch (IOException e) {
            try { Log.e(TAG, "Error setting up MediaPlayer", e); } catch (Throwable t) {}
            if (listener != null) {
                listener.onPlaybackError("Error setting up MediaPlayer: " + e.getMessage());
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
} 