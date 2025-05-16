package com.example.vac.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.app.NotificationManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.example.vac.handlers.CallSessionManager;
import com.example.vac.handlers.NotificationHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotificationManager;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.NEWEST_SDK)
public class CallScreeningServiceImplTest {

    @Mock private Call.Details mockCallDetails;
    @Mock private CallSessionManager mockCallSessionManager;
    @Mock private Notification mockNotification;
    
    private ServiceController<CallScreeningServiceImpl> controller;
    private CallScreeningServiceImpl serviceInstance;
    private NotificationManager realNotificationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        realNotificationManager = (NotificationManager) ApplicationProvider.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Test
    public void onScreenCall_startsForegroundService_andStartsScreening() {
        controller = Robolectric.buildService(CallScreeningServiceImpl.class);
        serviceInstance = spy(controller.create().get());
        doNothing().when(serviceInstance).startForeground(anyInt(), any(Notification.class));
        doNothing().when(serviceInstance).respondToCall(any(Call.Details.class), any(CallScreeningService.CallResponse.class));
        serviceInstance.onScreenCall(mockCallDetails);
        verify(serviceInstance).respondToCall(eq(mockCallDetails), any(CallScreeningService.CallResponse.class));
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(serviceInstance).startForeground(eq(CallScreeningServiceImpl.FOREGROUND_SERVICE_ID), notificationCaptor.capture());
        assertNotNull("Notification passed to startForeground should not be null", notificationCaptor.getValue());
        assertNotNull("currentCallSessionManager should be initialized by onScreenCall", serviceInstance.currentCallSessionManager);
    }

    @Test
    public void onStartCommand_withTakeOverAction_callsUserTakesOverOnSessionManager() {
        CallScreeningServiceImpl serviceForTest = new CallScreeningServiceImpl();
        CallSessionManager realSessionManagerForThisTest = mock(CallSessionManager.class); 
        serviceForTest.currentCallSessionManager = realSessionManagerForThisTest;
        Intent intent = new Intent(CallScreeningServiceImpl.ACTION_HANDLE_TAKE_OVER);
        serviceForTest.onStartCommand(intent, 0, 1);
        verify(realSessionManagerForThisTest).userTakesOver();
    }

    @Test
    public void onStartCommand_withOtherAction_doesNothingSpecific() {
        CallScreeningServiceImpl serviceForTest = new CallScreeningServiceImpl();
        CallSessionManager realSessionManagerForThisTest = mock(CallSessionManager.class);
        serviceForTest.currentCallSessionManager = realSessionManagerForThisTest; 
        Intent intent = new Intent("SOME_OTHER_ACTION");
        serviceForTest.onStartCommand(intent, 0, 1);
        verify(realSessionManagerForThisTest, never()).userTakesOver();
    }

    @Test
    public void onTranscriptionUpdate_updatesNotificationViaHandler() {
        controller = Robolectric.buildService(CallScreeningServiceImpl.class);
        serviceInstance = controller.create().get();
        ShadowNotificationManager shadowNM = Shadows.shadowOf(realNotificationManager);
        
        serviceInstance.notificationHandler.showScreeningNotification("Initial", null);
        realNotificationManager.cancelAll();

        String transcript = "Test transcript";
        serviceInstance.onTranscriptionUpdate(transcript);
        
        Notification updatedNotification = shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID);
        assertNotNull("Notification should be present after update", updatedNotification);
        assertEquals(transcript, Shadows.shadowOf(updatedNotification).getContentText().toString());
    }

    @Test
    public void onUserTookOver_updatesNotificationViaHandler() {
        controller = Robolectric.buildService(CallScreeningServiceImpl.class);
        serviceInstance = controller.create().get();
        serviceInstance.currentCallSessionManager = mockCallSessionManager;
        ShadowNotificationManager shadowNM = Shadows.shadowOf(realNotificationManager);

        serviceInstance.notificationHandler.showScreeningNotification("Initial with action", mock(PendingIntent.class)); 

        serviceInstance.onUserTookOver(mockCallSessionManager); 

        Notification updatedNotification = shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID);
        assertNotNull("Notification should be present after user takeover update", updatedNotification);
        assertEquals("Call taken over by user.", Shadows.shadowOf(updatedNotification).getContentText().toString());
        assertTrue("Actions should be cleared on user takeover notification", 
            updatedNotification.actions == null || updatedNotification.actions.length == 0);
        assertEquals("Only one notification should be present", 1, shadowNM.getAllNotifications().size());
    }

    @Test
    public void onSessionCompleted_cancelsNotification_stopsForegroundAndSelf() {
        controller = Robolectric.buildService(CallScreeningServiceImpl.class);
        serviceInstance = spy(controller.create().get());
        serviceInstance.currentCallSessionManager = mockCallSessionManager;
        doNothing().when(serviceInstance).stopForeground(anyBoolean());
        doNothing().when(serviceInstance).stopSelf();
        ShadowNotificationManager shadowNM = Shadows.shadowOf(realNotificationManager);

        serviceInstance.notificationHandler.showScreeningNotification("Active session", null);
        assertNotNull("Notification should be present before completion", shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID));

        serviceInstance.onSessionCompleted(mockCallSessionManager);
        
        assertNull("Notification should be cancelled after completion", shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID));
        verify(serviceInstance).stopForeground(true);
        verify(serviceInstance).stopSelf();
    }

    @Test
    public void onSessionError_cancelsNotification_stopsForegroundAndSelf() {
        controller = Robolectric.buildService(CallScreeningServiceImpl.class);
        serviceInstance = spy(controller.create().get());
        serviceInstance.currentCallSessionManager = mockCallSessionManager;
        doNothing().when(serviceInstance).stopForeground(anyBoolean());
        doNothing().when(serviceInstance).stopSelf();
        ShadowNotificationManager shadowNM = Shadows.shadowOf(realNotificationManager);

        serviceInstance.notificationHandler.showScreeningNotification("Active session error", null);
        assertNotNull("Notification should be present before error", shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID));

        serviceInstance.onSessionError(mockCallSessionManager, "Error");

        assertNull("Notification should be cancelled on error", shadowNM.getNotification(NotificationHandler.NOTIFICATION_ID));
        verify(serviceInstance).stopForeground(true);
        verify(serviceInstance).stopSelf();
    }
} 