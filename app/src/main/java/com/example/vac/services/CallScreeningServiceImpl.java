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
import androidx.core.app.NotificationCompat;

import com.example.vac.R; // For R.drawable.ic_launcher_foreground (assuming it exists)
import com.example.vac.activities.SetupActivity; // To launch from notification

/**
 * Implementation of CallScreeningService that intercepts incoming calls and manages
 * the assistant conversation flow.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class CallScreeningServiceImpl extends CallScreeningService {

    private static final String TAG = "CallScreeningServiceImpl";
    private static final String CHANNEL_ID = "VAC_CALL_SCREENING_CHANNEL";
    private static final int FOREGROUND_SERVICE_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CallScreeningService created (Task 3.1.1 Stub)");
        createNotificationChannel();
    }

    /**
     * Called when a new call is added and needs to be screened.
     * @param callDetails The details of the new call.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.i(TAG, "onScreenCall for: " + callDetails.getHandle() + " (Task 3.2)");

        // Respond to the call to take control for the assistant
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true) // Prevent the call from going to the default dialer
                .setRejectCall(true)   // Silently reject the call (from user's perspective)
                .setSkipCallLog(true)  // Don't add to call log as a missed call
                .setSkipNotification(true) // Don't show a system notification for a missed call
                .build();
        respondToCall(callDetails, response);
        Log.i(TAG, "Responded to call - assistant taking control (Task 3.2)");

        // Start foreground service with a notification
        Notification notification = buildNotification("Screening call...");
        startForeground(FOREGROUND_SERVICE_ID, notification);
        Log.i(TAG, "Service started in foreground.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "VAC Call Screening";
            String description = "Notification channel for VAC call screening service";
            int importance = NotificationManager.IMPORTANCE_LOW; // Low to be less intrusive, or HIGH if immediate attention is needed
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.i(TAG, "Notification channel created.");
            } else {
                Log.e(TAG, "NotificationManager not found, cannot create channel.");
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, SetupActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VAC Call Screening")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "CallScreeningService destroyed (Task 3.1.1 Stub)");
        // Consider when to call stopForeground(true) - e.g. when call ends or is no longer screened.
        // For this task, we start it but don't explicitly stop it here yet.
    }
} 