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

import com.example.vac.R;
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
    public static final String ACTION_HANG_UP_CALL = "com.example.vac.HANG_UP_CALL";

    CallSessionManager currentCallSessionManager;
    NotificationHandler notificationHandler;
    Call.Details activeCallDetails;
    private boolean userHasTakenOverCall = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CallScreeningService creating...");
        notificationHandler = new NotificationHandler(this);
    }

    private PendingIntent createTakeOverPendingIntent() {
        Intent takeOverIntent = new Intent(this, CallScreeningServiceImpl.class);
        takeOverIntent.setAction(ACTION_HANDLE_TAKE_OVER);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 0, takeOverIntent, flags);
    }

    private PendingIntent createHangUpPendingIntent() {
        Intent hangUpIntent = new Intent(this, CallScreeningServiceImpl.class);
        hangUpIntent.setAction(ACTION_HANG_UP_CALL);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 1, hangUpIntent, flags);
    }

    /**
     * Called when a new call is added and needs to be screened.
     * @param callDetails The details of the new call.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.i(TAG, "onScreenCall for: " + callDetails.getHandle());
        this.activeCallDetails = callDetails;
        this.userHasTakenOverCall = false;

        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
            Notification notification = notificationHandler.showScreeningNotification(
                    getString(R.string.notification_message_default),
                    createTakeOverPendingIntent(),
                    createHangUpPendingIntent()
            );
            if (notification != null) {
                startForeground(FOREGROUND_SERVICE_ID, notification);
            } else {
                Log.e(TAG, "Failed to create notification, cannot start foreground service.");
            }

            if (currentCallSessionManager != null) {
                Log.w(TAG, "Previous CallSessionManager was not null. Releasing it before creating a new one.");
                currentCallSessionManager.releaseInternal(false);
            }
            currentCallSessionManager = new CallSessionManager(this, callDetails, this, notificationHandler);
            currentCallSessionManager.startScreening();
        } else {
            Log.w(TAG, "Not an incoming call, ignoring: " + callDetails.getHandle());
            stopScreeningAndSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_HANDLE_TAKE_OVER.equals(action)) {
                Log.i(TAG, "Take over action received.");
                if (currentCallSessionManager != null) {
                    currentCallSessionManager.userTakesOver();
                } else {
                    Log.w(TAG, "Take over action received but no active call session manager.");
                    stopScreeningAndSelf();
                }
            } else if (ACTION_HANG_UP_CALL.equals(action)) {
                Log.i(TAG, "Hang up action received.");
                if (currentCallSessionManager != null) {
                    currentCallSessionManager.hangUpCall();
                } else {
                    Log.w(TAG, "Hang up action received but no active call session manager.");
                    stopScreeningAndSelf();
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void stopScreeningAndSelf() {
        Log.d(TAG, "stopScreeningAndSelf called.");
        if (notificationHandler != null) {
            notificationHandler.cancelNotification();
        }
        if (currentCallSessionManager != null && currentCallSessionManager.getCurrentState() != CallSessionManager.State.ENDED) {
            currentCallSessionManager.stopScreening();
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "CallScreeningService destroying...");
        if (currentCallSessionManager != null) {
            currentCallSessionManager.stopScreening();
            currentCallSessionManager = null;
        }
        activeCallDetails = null;
        if (notificationHandler != null) {
            notificationHandler.cancelNotification();
        }
        stopForeground(true);
        super.onDestroy();
    }

    // CallSessionManager.CallSessionListener Implementation
    @Override
    public void onUserTookOver(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onUserTookOver for session: " + session.hashCode());
        if (session == currentCallSessionManager && activeCallDetails != null) {
            userHasTakenOverCall = true;

            CallResponse takeOverResponse = new CallResponse.Builder().build();
            respondToCall(activeCallDetails, takeOverResponse);
            Log.i(TAG, "Responded to call - user taking over.");

            if (notificationHandler != null) {
                notificationHandler.updateNotification(
                    getString(R.string.notification_title_user_took_over),
                    getString(R.string.notification_message_user_took_over),
                    null
                );
            }
        } else {
            Log.w(TAG, "onUserTookOver called for an unknown or mismatched session, or null activeCallDetails.");
        }
    }

    @Override
    public void onSessionCompleted(CallSessionManager session) {
        Log.i(TAG, "CallSessionListener: onSessionCompleted for session: " + session.hashCode());
        if (session == currentCallSessionManager) {
            if (notificationHandler != null && !userHasTakenOverCall) {
                notificationHandler.updateNotification(
                    getString(R.string.notification_title_screening),
                    getString(R.string.notification_message_call_ended),
                    null
                );
            }
            currentCallSessionManager = null;
            activeCallDetails = null;
            userHasTakenOverCall = false;

            if (!userHasTakenOverCall) {
                Log.i(TAG, "Session completed (not by user takeover). Stopping foreground and self.");
                stopForeground(true);
                stopSelf();
            } else {
                 Log.i(TAG, "Session completed (after user takeover). Service will be stopped by onCallRemoved or system.");
            }
        } else {
            Log.w(TAG, "onSessionCompleted called for an unknown or mismatched session.");
        }
    }

    @Override
    public void onSessionError(CallSessionManager session, String errorMessage) {
        Log.e(TAG, "CallSessionListener: onSessionError for session: " + session.hashCode() + " Error: " + errorMessage);
        if (session == currentCallSessionManager) {
            if (notificationHandler != null && !userHasTakenOverCall) {
                notificationHandler.updateNotification(
                    getString(R.string.notification_title_screening),
                    getString(R.string.notification_message_call_ended) + " (Error: " + errorMessage + ")",
                    null
                );
            }
            currentCallSessionManager = null;
            activeCallDetails = null;
            userHasTakenOverCall = false;

            Log.i(TAG, "Session error. Stopping foreground and self.");
            stopForeground(true);
            stopSelf();
        } else {
            Log.w(TAG, "onSessionError called for an unknown or mismatched session.");
        }
    }

    @Override
    public void onTranscriptionUpdate(String latestTranscript) {
        if (notificationHandler != null && !userHasTakenOverCall) {
            notificationHandler.updateNotificationMessage(latestTranscript);
        }
        Log.d(TAG, "Live transcript update: " + latestTranscript);
    }

    // Called by the system when a call is removed from Telecom.
    // This can happen if the call is rejected, disconnected, or missed.
    // REMOVING THIS METHOD AS IT'S NOT A VALID OVERRIDE AND CAUSES COMPILATION ISSUES
    /*
    @Override
    public void onCallRemoved(Call call) { // Removed @androidx.annotation.NonNull
        Call.Details callDetails = call.getDetails();
        Log.i(TAG, "onCallRemoved for: " + callDetails.getHandle() + ", User had taken over: " + userHasTakenOverCall);

        if (currentCallSessionManager != null && currentCallSessionManager.getCallDetails().equals(callDetails)) {
            Log.i(TAG, "Call removed matches current session. Performing cleanup.");
            // If user took over, session completion/cleanup might have been handled by onUserTookOver.
            // If not, and call is removed for other reasons (e.g., caller hung up, disconnected),
            // ensure session is stopped.
            if (!userHasTakenOverCall) {
                currentCallSessionManager.stopScreening(); // General stop
            } else {
                // If user took over, the session is already considered "completed" from CSM's perspective
                // via userTakesOver -> releaseInternal(true) -> listener.onSessionCompleted.
                // However, CallScreeningService still needs to clean up its own state.
                Log.d(TAG, "User had taken over. Ensuring CallSessionManager is released if not already.");
                // releaseInternal(true) would have been called. Ensure idempotency or avoid double-release.
                // currentCallSessionManager.releaseInternal(true); // This might be redundant or cause issues
            }
            // Regardless of takeover status, the service itself is done with this call.
            stopScreeningAndSelf();
        } else {
            Log.w(TAG, "onCallRemoved for a call that doesn't match the current session or no session active.");
            // Potentially stopSelf if no active session is expected.
             if (currentCallSessionManager == null) {
                 stopSelf();
             }
        }
        activeCallDetails = null; // Clear active call details
        userHasTakenOverCall = false; // Reset takeover status
    }
    */
} 