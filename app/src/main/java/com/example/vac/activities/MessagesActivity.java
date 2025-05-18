package com.example.vac.activities;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vac.R;
import com.example.vac.adapters.MessageAdapter;
import com.example.vac.models.Message;
import com.example.vac.models.TranscriptionData;
import com.example.vac.managers.TranscriptionManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MessagesActivity extends AppCompatActivity implements MessageAdapter.MessageInteractionListener {

    private static final String TAG = "MessagesActivity";

    private RecyclerView recyclerViewMessages;
    private MessageAdapter messageAdapter;
    private TextView textViewEmptyMessages;
    private MediaPlayer mediaPlayer;
    private List<Message> messageList = new ArrayList<>();
    private TranscriptionManager transcriptionManagerInstance;  // Instance for transcription management

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        recyclerViewMessages = findViewById(R.id.recycler_view_messages);
        textViewEmptyMessages = findViewById(R.id.tv_empty_messages);

        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        transcriptionManagerInstance = new TranscriptionManager(getFilesDir());  // Initialize here
        messageAdapter = new MessageAdapter(this, messageList, this);  // Adjusted to match constructor
        recyclerViewMessages.setAdapter(messageAdapter);

        loadMessages();
    }

    private void loadMessages() {
        messageList.clear();
        File dir = getFilesDir();
        File[] files = dir.listFiles((d, name) -> name.startsWith("message_") && name.endsWith(".3gp"));

        if (files != null) {
            for (File file : files) {
                try {
                    List<TranscriptionData> transcriptions = transcriptionManagerInstance.getTranscriptionsForCall(file.getName());
                    messageList.add(new Message(file, transcriptions));  // Assuming Message constructor is updated
                } catch (IOException e) {
                    Log.e(TAG, "Error loading transcriptions: " + e.getMessage());
                    Toast.makeText(this, "Error loading transcriptions", Toast.LENGTH_SHORT).show();
                }
            }
            Collections.sort(messageList, (m1, m2) -> {
                try {
                    long ts1 = Long.parseLong(m1.getFilename().split("_")[1].split("\\.")[0]);
                    long ts2 = Long.parseLong(m2.getFilename().split("_")[1].split("\\.")[0]);
                    return Long.compare(ts2, ts1); // Newest first
                } catch (Exception e) {
                    return 0;
                }
            });
        }

        if (messageList.isEmpty()) {
            textViewEmptyMessages.setVisibility(View.VISIBLE);
            recyclerViewMessages.setVisibility(View.GONE);
        } else {
            textViewEmptyMessages.setVisibility(View.GONE);
            recyclerViewMessages.setVisibility(View.VISIBLE);
        }
        messageAdapter.notifyDataSetChanged();
    }

@Override
public void onPlayMessage(Message message) {
    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    mediaPlayer = new MediaPlayer();
    try {
        mediaPlayer.setDataSource(message.getFile().getAbsolutePath());
        mediaPlayer.prepare();
        
        // Add UI controls for playback
        showPlaybackControls();  // New method to display controls
        
        mediaPlayer.start();
        Toast.makeText(this, "Playing: " + message.getFilename(), Toast.LENGTH_SHORT).show();
        mediaPlayer.setOnCompletionListener(mp -> {
            releaseMediaPlayer();
            hidePlaybackControls();  // Hide controls when playback ends
        });
    } catch (IOException e) {
        Log.e(TAG, "Error playing message", e);
        Toast.makeText(this, "Error playing message", Toast.LENGTH_SHORT).show();
        releaseMediaPlayer();
    }
}

// New method to handle UI controls
private void showPlaybackControls() {
    // Check if the layout exists before proceeding
    View controlsView = findViewById(R.id.playback_controls_layout);
    if (controlsView == null) {
        Log.e(TAG, "playback_controls_layout not found in layout. Skipping controls setup.");
        Toast.makeText(this, "Playback controls layout not found.", Toast.LENGTH_SHORT).show();
        return;  // Exit if layout is missing
    }
    controlsView.setVisibility(View.VISIBLE);
    
    // Set up buttons (e.g., play, pause, seek bar) with null checks
    View btnPlayPause = findViewById(R.id.btn_play_pause);
    if (btnPlayPause != null) {
        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    ((Button) v).setText("Play");
                } else {
                    mediaPlayer.start();
                    ((Button) v).setText("Pause");
                }
            }
        });
    } else {
        Log.e(TAG, "btn_play_pause not found in layout.");
    }
    
    SeekBar seekBar = (SeekBar) findViewById(R.id.seek_bar);
    if (seekBar != null) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No action needed
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No action needed
            }
        });
    } else {
        Log.e(TAG, "seek_bar not found in layout.");
    }
    
    TextView timerTextView = (TextView) findViewById(R.id.timer_text_view);
    if (timerTextView != null && mediaPlayer != null) {
        timerTextView.setText(formatTime(mediaPlayer.getCurrentPosition()) + " / " + formatTime(mediaPlayer.getDuration()));
        
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying() && timerTextView != null) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    if (seekBar != null) {
                        seekBar.setProgress(currentPosition);
                    }
                    timerTextView.setText(formatTime(currentPosition) + " / " + formatTime(mediaPlayer.getDuration()));
                    new Handler().postDelayed(this, 1000);
                }
            }
        }, 1000);
    } else {
        Log.e(TAG, "timer_text_view not found in layout.");
    }
}

private void hidePlaybackControls() {
    View controlsView = findViewById(R.id.playback_controls_layout);
    if (controlsView != null) {
        controlsView.setVisibility(View.GONE);
    }
}

private String formatTime(int milliseconds) {
    int seconds = milliseconds / 1000;
    int minutes = seconds / 60;
    seconds = seconds % 60;
    return String.format("%d:%02d", minutes, seconds);
}

    @Override
    public void onDeleteMessage(Message message) {
        File fileToDelete = message.getFile();
        if (fileToDelete.exists() && fileToDelete.delete()) {
            Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
            loadMessages();
        } else {
            Toast.makeText(this, "Error deleting message", Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaPlayer();
    }
}
