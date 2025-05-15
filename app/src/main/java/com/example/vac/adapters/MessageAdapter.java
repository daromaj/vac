package com.example.vac.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
        //TODO: Implement when item_message.xml IDs are defined (Task 6.3)
        // holder.textViewMessageName.setText(message.getFilename()); // Or a more friendly name

        // holder.buttonPlay.setOnClickListener(v -> {
        //     if (listener != null) {
        //         listener.onPlayMessage(message);
        //     }
        // });
        // holder.buttonDelete.setOnClickListener(v -> {
        //     if (listener != null) {
        //         listener.onDeleteMessage(message);
        //     }
        // });
        if (true) { // Condition to ensure method is not empty if all above is commented
             throw new UnsupportedOperationException("onBindViewHolder not fully implemented - Task 6.3 pending. item_message.xml needs IDs.");
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textViewMessageName;
        ImageButton buttonPlay;
        ImageButton buttonDelete;

        MessageViewHolder(View itemView) {
            super(itemView);
            //TODO: Implement when item_message.xml IDs are defined (Task 6.3)
            // textViewMessageName = itemView.findViewById(R.id.text_view_message_name);
            // buttonPlay = itemView.findViewById(R.id.button_play_message);
            // buttonDelete = itemView.findViewById(R.id.button_delete_message);
            if (true) { // Condition to ensure constructor is not empty if all above is commented
                // This is a placeholder to ensure the constructor compiles.
                // The actual view finding logic depends on Task 6.3 (item_message.xml).
            }
        }
    }
} 