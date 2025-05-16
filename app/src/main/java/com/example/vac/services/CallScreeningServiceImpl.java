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
    public static final int FOREGROUND_SERVICE_ID = 1;
    public static final String ACTION_HANDLE_TAKE_OVER = "com.example.vac.HANDLE_TAKE_OVER";

    CallSessionManager currentCallSessionManager;
    NotificationHandler notificationHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CallScreeningService creating...");
        notificationHandler = new NotificationHandler(this);
    }

    /**
     * Called when a new call is added and needs to be screened.
     * @param callDetails The details of the new call.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.i(TAG, "onScreenCall for: " + callDetails.getHandle());

        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build();
        respondToCall(callDetails, response);
        Log.i(TAG, "Responded to call - assistant taking control.");

        Intent takeOverActionIntent = new Intent(this, CallScreeningServiceImpl.class);
        takeOverActionIntent.setAction(ACTION_HANDLE_TAKE_OVER);
        PendingIntent takeOverPendingIntent = PendingIntent.getService(this, 0, takeOverActionIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        if (currentCallSessionManager != null) {
            Log.w(TAG, "Previous CallSessionManager active, stopping it before starting new one.");
            currentCallSessionManager.stopScreening(false);
        }
        currentCallSessionManager = new CallSessionManager(this, callDetails, this, notificationHandler);
        
        Notification initialNotification = notificationHandler.showScreeningNotification(
            "VAC is starting...",
            takeOverPendingIntent 
        );

        if (initialNotification != null) {
            startForeground(FOREGROUND_SERVICE_ID, initialNotification); 
            Log.i(TAG, "Service started in foreground.");
            currentCallSessionManager.startScreening();
        } else {
            Log.e(TAG, "Failed to create initial notification. Cannot start foreground service or screening.");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null && ACTION_HANDLE_TAKE_OVER.equals(intent.getAction())) {
            Log.i(TAG, "Handling ACTION_HANDLE_TAKE_OVER.");
            if (currentCallSessionManager != null) {
                currentCallSessionManager.userTakesOver();
            } else {
                Log.w(TAG, "ACTION_HANDLE_TAKE_OVER received but no active call session manager.");
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "CallScreeningService destroying...");
        if (currentCallSessionManager != null) {
            currentCallSessionManager.stopScreening(false);
            currentCallSessionManager = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    // CallSessionManager.CallSessionListener Implementation
    @Override
    public void onSessionCompleted(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onSessionCompleted for session: " + session.hashCode());
        if (session == currentCallSessionManager) {
            notificationHandler.cancelNotification();
            stopForeground(true); 
            stopSelf(); 
            currentCallSessionManager = null; 
        }
    }

    @Override
    public void onSessionError(CallSessionManager session, String errorMessage) {
        Log.e(TAG, "CallSessionListener: onSessionError for session: " + session.hashCode() + " Error: " + errorMessage);
        if (session == currentCallSessionManager) {
            notificationHandler.cancelNotification();
            stopForeground(true);
            stopSelf();
            currentCallSessionManager = null;
        }
    }

    @Override
    public void onUserTookOver(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onUserTookOver for session: " + session.hashCode());
        if (session == currentCallSessionManager && notificationHandler != null) {
            notificationHandler.updateNotification("Call taken over by user.");
        }
    }

    @Override
    public void onTranscriptionUpdate(String latestTranscript) {
        Log.d(TAG, "CallSessionListener: onTranscriptionUpdate: '" + latestTranscript + "'");
        if (notificationHandler != null) {
            notificationHandler.updateTranscription(latestTranscript);
        }
    }
} 