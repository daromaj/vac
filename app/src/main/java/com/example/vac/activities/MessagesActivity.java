package com.example.vac.activities;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
            mediaPlayer.start();
            Toast.makeText(this, "Playing: " + message.getFilename(), Toast.LENGTH_SHORT).show();
            mediaPlayer.setOnCompletionListener(mp -> releaseMediaPlayer());
        } catch (IOException e) {
            Log.e(TAG, "Error playing message", e);
            Toast.makeText(this, "Error playing message", Toast.LENGTH_SHORT).show();
            releaseMediaPlayer();
        }
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
