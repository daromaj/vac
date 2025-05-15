package com.example.vac.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.vac.R;
import com.example.vac.activities.SetupActivity;
import com.example.vac.handlers.CallSessionManager;
import com.example.vac.handlers.NotificationHandler;

/**
 * Implementation of CallScreeningService that intercepts incoming calls and manages
 * the assistant conversation flow.
 */
public class CallScreeningServiceImpl extends CallScreeningService implements CallSessionManager.CallSessionListener {
    private static final String TAG = "CallScreeningServiceImpl";
    private static final int FOREGROUND_SERVICE_ID = 1001;
    private static final String CHANNEL_ID = "vac_call_screening_channel";
    
    private CallSessionManager activeCallSession;
    private NotificationHandler notificationHandler;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CallScreeningService created");
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        // Initialize notification handler
        notificationHandler = new NotificationHandler(this);
    }

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.d(TAG, "Incoming call: " + callDetails.getHandle());

        // Respond to the telecom framework that we'll handle this call
        respondToCall(callDetails, buildResponseForIncomingCall());
        
        // Start the call session manager and begin assistant workflow
        startCallSession(callDetails);
    }
    
    /**
     * Build the response for the CallScreeningService to handle the call
     */
    private CallResponse buildResponseForIncomingCall() {
        return new CallResponse.Builder()
                .setDisallowCall(false)        // Allow the call
                .setRejectCall(false)          // Don't reject
                .setSilenceCall(true)          // Silence the ringtone
                .setSkipCallLog(false)         // Don't skip call log
                .setSkipNotification(false)    // Don't skip notification
                .build();
    }
    
    /**
     * Start a new call session to manage this call
     */
    private void startCallSession(Call.Details callDetails) {
        // Make sure any previous session is cleaned up
        if (activeCallSession != null) {
            activeCallSession.stopScreening(false);
            activeCallSession = null;
        }
        
        // Create and start a new call session
        activeCallSession = new CallSessionManager(this, callDetails, this, notificationHandler);
        
        // Start the service in foreground
        startForeground(FOREGROUND_SERVICE_ID, createInitialNotification());
        
        // Begin the screening process
        activeCallSession.startScreening();
    }
    
    /**
     * Create a notification channel for API 26+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Screening",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Used when the assistant is screening a call");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create the initial notification for the foreground service
     */
    private Notification createInitialNotification() {
        Intent notificationIntent = new Intent(this, SetupActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Call Assistant Active")
                .setContentText("Screening incoming call...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "CallScreeningService destroyed");
        
        // Clean up any active call session
        if (activeCallSession != null) {
            activeCallSession.stopScreening(false);
            activeCallSession = null;
        }
        
        super.onDestroy();
    }
    
    // CallSessionListener implementation
    
    @Override
    public void onSessionCompleted(CallSessionManager session) {
        Log.d(TAG, "Call session completed");
        
        // Stop the foreground service
        stopForeground(true);
        stopSelf();
    }
    
    @Override
    public void onSessionError(CallSessionManager session, String errorMessage) {
        Log.e(TAG, "Call session error: " + errorMessage);
        
        // Stop the foreground service
        stopForeground(true);
        stopSelf();
    }
    
    @Override
    public void onUserTookOver(CallSessionManager session) {
        Log.d(TAG, "User took over the call");
        
        // Update notification but keep service running
        notificationHandler.updateNotification("User has taken over. Recording continues.");
        
        // Service will be stopped when call actually ends
    }
    
    @Override
    public void onTranscriptionUpdate(String latestTranscript) {
        // Update the notification with new transcription
        notificationHandler.updateTranscription(latestTranscript);
    }
} 