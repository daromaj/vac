package com.example.vac.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vac.R;
import com.example.vac.models.TranscriptionData;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TranscriptionAdapter extends RecyclerView.Adapter<TranscriptionAdapter.ViewHolder> {
    private List<TranscriptionData> transcriptions = new ArrayList<>();
    private int highlightedPosition = -1;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transcription, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TranscriptionData transcription = transcriptions.get(position);
        
        // Set speaker name and text
        holder.speakerNameText.setText(getSpeakerName(transcription.getSpeakerType()));
        holder.transcriptionText.setText(transcription.getText());
        
        // Set timestamp
        holder.timestampText.setText(timeFormat.format(new Date(transcription.getTimestamp())));
        
        // Set background color based on speaker type
        int backgroundColor = getSpeakerColor(transcription.getSpeakerType());
        holder.cardView.setCardBackgroundColor(backgroundColor);
        
        // Highlight current position
        if (position == highlightedPosition) {
            holder.cardView.setStrokeWidth(4);
            holder.cardView.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.highlight));
        } else {
            holder.cardView.setStrokeWidth(0);
        }
    }

    @Override
    public int getItemCount() {
        return transcriptions.size();
    }

    public void setTranscriptions(List<TranscriptionData> transcriptions) {
        this.transcriptions = transcriptions;
        notifyDataSetChanged();
    }

    public void setHighlightedPosition(int position) {
        int oldPosition = highlightedPosition;
        highlightedPosition = position;
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    private String getSpeakerName(TranscriptionData.SpeakerType speakerType) {
        switch (speakerType) {
            case ASSISTANT:
                return "Assistant";
            case USER:
                return "You";
            case CALLER:
                return "Caller";
            default:
                return "Unknown";
        }
    }

    private int getSpeakerColor(TranscriptionData.SpeakerType speakerType) {
        switch (speakerType) {
            case ASSISTANT:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.assistant_background);
            case USER:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.user_background);
            case CALLER:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.caller_background);
            default:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.default_background);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView speakerNameText;
        final TextView transcriptionText;
        final TextView timestampText;

        ViewHolder(View view) {
            super(view);
            cardView = view.findViewById(R.id.cardView);
            speakerNameText = view.findViewById(R.id.speakerNameText);
            transcriptionText = view.findViewById(R.id.transcriptionText);
            timestampText = view.findViewById(R.id.timestampText);
        }
    }
} 