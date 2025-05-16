package com.example.vac.handlers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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
    private static final String ACTION_TAKE_OVER = "com.example.vac.TAKE_OVER";
    
    private final Context context;
    private final NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    
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
     * @return The created Notification
     */
    public Notification showScreeningNotification(String initialMessage, PendingIntent takeOverPendingIntent) {
        // Create the notification builder
        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Call Assistant Active")
                .setContentText(initialMessage)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true);
        
        // Add the take over action if the intent is provided
        if (takeOverPendingIntent != null) {
            notificationBuilder.addAction(
                    android.R.drawable.ic_menu_call,
                    "Take Over Call",
                    takeOverPendingIntent
            );
        }
        
        Notification notification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);
        
        return notification;
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
     * Update the notification with a new message.
     * 
     * @param message The new message to display
     */
    public void updateNotification(String message) {
        if (notificationBuilder == null) {
            return;
        }
        
        notificationBuilder.setContentText(message);
        // Clear any action buttons
        notificationBuilder.mActions.clear();
        
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
    
    /**
     * Cancel the notification.
     */
    public void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
        notificationBuilder = null;
    }
} 