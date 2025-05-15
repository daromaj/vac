package com.example.vac.handlers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

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
    
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;
    private AudioFocusRequest audioFocusRequest;
    private boolean isPlayingAudio = false;
    
    /**
     * Constructor for AudioHandler
     * 
     * @param context The context
     * @param listener The listener for audio events
     */
    public AudioHandler(Context context, AudioHandlerListener listener) {
        this.context = context;
        this.listener = listener;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        
        initializeTts();
    }
    
    /**
     * Initialize the TextToSpeech engine
     */
    private void initializeTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // For MVP we use default Locale - could be enhanced to use Polish
                int result = tts.setLanguage(Locale.getDefault());
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported for TTS");
                }
                
                // Set up utterance progress listener
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        isPlayingAudio = true;
                        if (listener != null) {
                            listener.onPlaybackStarted();
                        }
                    }
                    
                    @Override
                    public void onDone(String utteranceId) {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (listener != null) {
                            listener.onPlaybackCompleted();
                        }
                    }
                    
                    @Override
                    public void onError(String utteranceId) {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (listener != null) {
                            listener.onPlaybackError("TTS error for utterance: " + utteranceId);
                        }
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        isPlayingAudio = false;
                        releaseAudioFocus();
                        if (listener != null) {
                            listener.onPlaybackError("TTS error for utterance: " + utteranceId + 
                                                    ", code: " + errorCode);
                        }
                    }
                });
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                if (listener != null) {
                    listener.onPlaybackError("TTS initialization failed");
                }
            }
        });
    }
    
    /**
     * Play the greeting to the caller
     * 
     * @param userName The user's name
     * @param customGreeting The custom greeting text (if provided)
     */
    public void playGreeting(String userName, String customGreeting) {
        if (isPlayingAudio) {
            stopPlayback();
        }
        
        String greeting;
        
        if (customGreeting != null && !customGreeting.isEmpty()) {
            // Use custom greeting
            greeting = customGreeting;
        } else {
            // Use default greeting with username
            greeting = context.getString(R.string.default_greeting, userName);
        }
        
        // Request audio focus and play TTS
        if (requestAudioFocus()) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID_GREETING);
            
            tts.speak(greeting, TextToSpeech.QUEUE_FLUSH, params);
        } else {
            if (listener != null) {
                listener.onPlaybackError("Failed to get audio focus for greeting");
            }
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
            Log.e(TAG, "Error setting up MediaPlayer", e);
            if (listener != null) {
                listener.onPlaybackError("Error setting up MediaPlayer: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop any ongoing playback
     */
    public void stopPlayback() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.reset();
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
        stopPlayback();
        
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
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