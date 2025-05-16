package com.example.vac.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.vac.R;
import com.example.vac.models.Message;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final Context context;
    private final List<Message> messageList;
    private final MessageInteractionListener listener;

    public interface MessageInteractionListener {
        void onPlayMessage(Message message);
        void onDeleteMessage(Message message);
    }

    public MessageAdapter(Context context, List<Message> messageList, MessageInteractionListener listener) {
        this.context = context;
        this.messageList = messageList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        holder.textViewMessageDate.setText(message.getFormattedDate());
        holder.textViewMessageFilename.setText(message.getFilename());

        holder.buttonPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayMessage(message);
            }
        });
        
        holder.buttonDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteMessage(message);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textViewMessageDate;
        TextView textViewMessageFilename;
        Button buttonPlay;
        Button buttonDelete;

        MessageViewHolder(View itemView) {
            super(itemView);
            textViewMessageDate = itemView.findViewById(R.id.tv_message_date);
            textViewMessageFilename = itemView.findViewById(R.id.tv_message_filename);
            buttonPlay = itemView.findViewById(R.id.btn_play);
            buttonDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
} 