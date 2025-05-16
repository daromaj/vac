package com.example.vac.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.example.vac.handlers.CallSessionManager;
import com.example.vac.handlers.NotificationHandler;

/**
 * Implementation of CallScreeningService that intercepts incoming calls and manages
 * the assistant conversation flow.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class CallScreeningServiceImpl extends CallScreeningService implements CallSessionManager.CallSessionListener {

    private static final String TAG = "CallScreeningServiceImpl";
    private static final String CHANNEL_ID = "VAC_CALL_SCREENING_CHANNEL";
    private static final int FOREGROUND_SERVICE_ID = 1;

    private CallSessionManager currentCallSessionManager;
    private NotificationHandler notificationHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CallScreeningService creating...");
        createNotificationChannel();
        notificationHandler = new NotificationHandler(this);
    }

    /**
     * Called when a new call is added and needs to be screened.
     * @param callDetails The details of the new call.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.i(TAG, "onScreenCall for: " + callDetails.getHandle() + " (Task 3.3 integration)");

        // Respond to the call to take control for the assistant (from Task 3.2)
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build();
        respondToCall(callDetails, response);
        Log.i(TAG, "Responded to call - assistant taking control.");

        // For now, let's create a placeholder PendingIntent for "Take Over"
        // This will be properly implemented in a later task (Task 5.3 / 5.4)
        Intent takeOverIntent = new Intent(this, CallScreeningServiceImpl.class); // Placeholder action
        takeOverIntent.setAction("DUMMY_TAKE_OVER_ACTION"); // Placeholder
        PendingIntent takeOverPendingIntent = PendingIntent.getService(this, 0, takeOverIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        // Create and start a new call session
        if (currentCallSessionManager != null) {
            Log.w(TAG, "Previous CallSessionManager not null, stopping it first.");
            currentCallSessionManager.stopScreening(false);
        }
        currentCallSessionManager = new CallSessionManager(this, callDetails, this, notificationHandler);
        
        // The notification for startForeground should come from NotificationHandler,
        // which is called by CallSessionManager.startScreening()
        // CallSessionManager.startScreening() will call notificationHandler.showScreeningNotification(...)
        // We need that notification object here for startForeground.
        
        // Let's assume startScreening or similar method in CallSessionManager will show the notification
        // and we can retrieve it or CallSessionManager directly calls startForeground if it has service context.
        // For now, CallSessionManager.startScreening() creates and shows a notification.
        // We still need one for startForeground().
        // Let's make showScreeningNotification return the Notification object.
        
        Notification initialNotification = notificationHandler.showScreeningNotification(
            "VAC is starting...", // Initial message
            takeOverPendingIntent  // Pass the take over pending intent
        );

        if (initialNotification != null) {
            startForeground(FOREGROUND_SERVICE_ID, initialNotification);
            Log.i(TAG, "Service started in foreground with initial notification.");
            // Now, actually start the screening process which includes the greeting
            currentCallSessionManager.startScreening();
        } else {
            Log.e(TAG, "Failed to create initial notification. Cannot start foreground service or screening.");
            // Consider how to handle this error - maybe stopSelf()?
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "VAC Call Screening";
            String description = "Notification channel for VAC call screening service";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.i(TAG, "Notification channel created/ensured: " + CHANNEL_ID);
            } else {
                Log.e(TAG, "NotificationManager not found, cannot create channel.");
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "CallScreeningService destroying...");
        if (currentCallSessionManager != null) {
            currentCallSessionManager.stopScreening(false); // Or .release()
            currentCallSessionManager = null;
        }
        // The foreground service stops when stopSelf() is called or all clients unbind and it's not started.
        // stopForeground(true) will remove the notification.
        // This should be called when screening is truly over.
        // For now, onDestroy might be too soon if calls can outlive the initial service instance.
        // But for MVP, if a call ends, the service might be destroyed.
        stopForeground(true); // Remove notification when service is destroyed.
        super.onDestroy();
    }

    // CallSessionManager.CallSessionListener Implementation
    @Override
    public void onSessionCompleted(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onSessionCompleted for session: " + session.hashCode());
        // Here we would typically stop the foreground service if this is the active session
        // and no other sessions are active.
        // For now, if it's the current session, we can stop an clean up.
        if (session == currentCallSessionManager) {
            stopForeground(true); // Remove notification
            stopSelf(); // Stop the service
            currentCallSessionManager = null; // Clear reference
        }
    }

    @Override
    public void onSessionError(CallSessionManager session, String errorMessage) {
        Log.e(TAG, "CallSessionListener: onSessionError for session: " + session.hashCode() + " Error: " + errorMessage);
        if (session == currentCallSessionManager) {
            stopForeground(true);
            stopSelf();
            currentCallSessionManager = null;
        }
    }

    @Override
    public void onUserTookOver(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onUserTookOver for session: " + session.hashCode());
        // The CallSessionManager itself handles its state change.
        // The service might want to stop foreground aspects if UI is now handled by Dialer.
        // However, recording might continue. For now, just log.
        // NotificationHandler might change the notification.
    }

    @Override
    public void onTranscriptionUpdate(String latestTranscript) {
        Log.d(TAG, "CallSessionListener: onTranscriptionUpdate: " + latestTranscript);
        // This will be handled by NotificationHandler via CallSessionManager
    }
} 