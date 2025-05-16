package com.example.vac.services;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Implementation of CallScreeningService that intercepts incoming calls and manages
 * the assistant conversation flow.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class CallScreeningServiceImpl extends CallScreeningService {

    private static final String TAG = "CallScreeningServiceImpl";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "CallScreeningService created (Task 3.1.1 Stub)");
    }

    /**
     * Called when a new call is added and needs to be screened.
     * @param callDetails The details of the new call.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        Log.i(TAG, "onScreenCall for: " + callDetails.getHandle() + " (Task 3.1.1 Stub)");
        CallResponse response = new CallResponse.Builder().build();
        respondToCall(callDetails, response);
        Log.i(TAG, "Responded to call - allowing (Task 3.1.1 Stub)");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "CallScreeningService destroyed (Task 3.1.1 Stub)");
    }
} 