package com.example.vac.ui;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vac.R;
import com.example.vac.handlers.TranscriptionManager;
import com.example.vac.models.TranscriptionData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessagePlaybackActivity extends AppCompatActivity {
    private static final String TAG = "MessagePlaybackActivity";
    private static final int UPDATE_INTERVAL_MS = 100;

    private MaterialToolbar toolbar;
    private RecyclerView transcriptionRecyclerView;
    private TranscriptionAdapter transcriptionAdapter;
    private LinearProgressIndicator progressIndicator;
    private FloatingActionButton playPauseButton;
    private TextView timestampText;
    private SearchView searchView;

    private MediaPlayer mediaPlayer;
    private Handler updateHandler;
    private boolean isPlaying = false;
    private String currentCallId;
    private TranscriptionManager transcriptionManager;
    private List<TranscriptionData> transcriptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_playback);

        // Initialize components
        toolbar = findViewById(R.id.toolbar);
        transcriptionRecyclerView = findViewById(R.id.transcriptionRecyclerView);
        progressIndicator = findViewById(R.id.progressIndicator);
        playPauseButton = findViewById(R.id.playPauseButton);
        timestampText = findViewById(R.id.timestampText);
        searchView = findViewById(R.id.searchView);

        // Setup toolbar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.message_playback_title);

        // Initialize TranscriptionManager
        transcriptionManager = new TranscriptionManager(this);

        // Setup RecyclerView
        transcriptionAdapter = new TranscriptionAdapter();
        transcriptionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transcriptionRecyclerView.setAdapter(transcriptionAdapter);

        // Get call ID from intent
        currentCallId = getIntent().getStringExtra("call_id");
        if (currentCallId != null) {
            loadTranscriptions(currentCallId);
        }

        // Setup click listeners
        playPauseButton.setOnClickListener(v -> togglePlayback());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchTranscriptions(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    loadTranscriptions(currentCallId);
                }
                return true;
            }
        });

        // Initialize update handler
        updateHandler = new Handler(Looper.getMainLooper());
    }

    private void loadTranscriptions(String callId) {
        transcriptions = transcriptionManager.getTranscriptionForCall(callId);
        transcriptionAdapter.setTranscriptions(transcriptions);
    }

    private void searchTranscriptions(String query) {
        transcriptions = transcriptionManager.searchTranscriptions(query);
        transcriptionAdapter.setTranscriptions(transcriptions);
    }

    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (mediaPlayer == null) {
            // TODO: Initialize MediaPlayer with the audio file
            // mediaPlayer = MediaPlayer.create(this, audioFileUri);
        }

        if (mediaPlayer != null) {
            mediaPlayer.start();
            isPlaying = true;
            playPauseButton.setImageResource(R.drawable.ic_pause);
            startProgressUpdates();
        }
    }

    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            playPauseButton.setImageResource(R.drawable.ic_play);
            stopProgressUpdates();
        }
    }

    private void startProgressUpdates() {
        updateHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    
                    // Update progress
                    progressIndicator.setMax(duration);
                    progressIndicator.setProgress(currentPosition);
                    
                    // Update timestamp
                    updateTimestamp(currentPosition);
                    
                    // Update highlighted transcription
                    updateHighlightedTranscription(currentPosition);
                    
                    updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        });
    }

    private void stopProgressUpdates() {
        updateHandler.removeCallbacksAndMessages(null);
    }

    private void updateTimestamp(int position) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(position);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(position) % 60;
        timestampText.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void updateHighlightedTranscription(int currentPosition) {
        for (int i = 0; i < transcriptions.size(); i++) {
            TranscriptionData transcription = transcriptions.get(i);
            if (transcription.getTimestamp() <= currentPosition) {
                transcriptionAdapter.setHighlightedPosition(i);
                transcriptionRecyclerView.smoothScrollToPosition(i);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopProgressUpdates();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_message_playback, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 