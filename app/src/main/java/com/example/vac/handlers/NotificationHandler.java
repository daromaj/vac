package com.example.vac.handlers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.vac.R;

/**
 * Handles the creation and updating of notifications for the call screening service.
 */
public class NotificationHandler {
    private static final String TAG = "NotificationHandler";
    public static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "VAC_CALL_SCREENING_CHANNEL";
    private static final String CHANNEL_NAME = "VAC Call Screening";
    
    private final Context context;
    private final NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private Notification currentNotification;
    
    public NotificationHandler(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, 
                        CHANNEL_NAME, 
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for active call screening by VAC");
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Show a notification for the ongoing call screening session.
     * 
     * @param initialMessage The initial message to display
     * @param takeOverPendingIntent The PendingIntent to execute when user takes over
     * @param hangUpPendingIntent The PendingIntent to execute when user hangs up
     * @return The created Notification
     */
    public Notification showScreeningNotification(String initialMessage, PendingIntent takeOverPendingIntent, PendingIntent hangUpPendingIntent) {
        // Create the notification builder
        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_title_screening))
                .setContentText(initialMessage)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        
        // Add the take over action if the intent is provided
        if (takeOverPendingIntent != null) {
            notificationBuilder.addAction(
                    new NotificationCompat.Action(
                        0,
                        context.getString(R.string.notification_action_take_over),
                        takeOverPendingIntent
                    )
            );
        }
        
        // Add the hang up action if the intent is provided
        if (hangUpPendingIntent != null) {
            notificationBuilder.addAction(
                    new NotificationCompat.Action(
                        0,
                        context.getString(R.string.notification_action_hang_up),
                        hangUpPendingIntent
                    )
            );
        }
        
        currentNotification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, currentNotification);
        
        return currentNotification;
    }
    
    /**
     * Update the notification with transcribed text.
     * 
     * @param transcribedText The new transcribed text to display
     */
    public void updateTranscription(String transcribedText) {
        if (notificationBuilder == null) {
            return;
        }
        
        notificationBuilder.setContentText(transcribedText);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
    
    /**
     * Update the notification with a new title, message, and optional actions.
     *
     * @param title The new title for the notification.
     * @param message The new message to display.
     * @param actions A list of NotificationCompat.Action to add. Clears existing actions if null or empty.
     */
    public void updateNotification(String title, String message, java.util.List<NotificationCompat.Action> actions) {
        if (notificationBuilder == null) {
            // Optionally, recreate the builder if we want to allow updating a cancelled notification
            // For now, assume it only updates an existing, visible notification's builder
            return;
        }

        if (title != null) {
            notificationBuilder.setContentTitle(title);
        }
        if (message != null) {
            notificationBuilder.setContentText(message);
        }

        // Clear existing actions before adding new ones
        notificationBuilder.mActions.clear();
        if (actions != null) {
            for (NotificationCompat.Action action : actions) {
                notificationBuilder.addAction(action);
            }
        }
        
        // Ensure ongoing flag is appropriate. If actions are removed, it might not need to be ongoing.
        // For simplicity, keeping it as is, but this could be refined.
        // notificationBuilder.setOngoing(actions != null && !actions.isEmpty());

        currentNotification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, currentNotification);
    }
    
    /**
     * Get the currently built notification.
     * @return The current Notification object, or null if not built.
     */
    public Notification getCurrentNotification() {
        return currentNotification;
    }
    
    /**
     * Cancel the notification.
     */
    public void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
        notificationBuilder = null;
    }

    public NotificationCompat.Builder getNotificationBuilder() {
        return notificationBuilder;
    }

    /**
     * Updates only the content message of the current notification, preserving title and actions.
     * If no notification is currently shown (builder is null), this does nothing.
     *
     * @param newMessage The new message to display.
     */
    public void updateNotificationMessage(String newMessage) {
        if (notificationBuilder == null) {
            Log.w(TAG, "updateNotificationMessage called but notificationBuilder is null. Cannot update.");
            return;
        }
        notificationBuilder.setContentText(newMessage);
        currentNotification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, currentNotification);
        Log.d(TAG, "Notification message updated: " + newMessage);
    }
} 