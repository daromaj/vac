package com.example.vac.handlers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import com.example.vac.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.NEWEST_SDK)
public class NotificationHandlerTest {

    private Context context;
    private NotificationHandler notificationHandler;
    private NotificationManager realNotificationManager;

    private static final String CHANNEL_ID = "VAC_CALL_SCREENING_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;

    @Mock
    private PendingIntent mockPendingIntent;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = ApplicationProvider.getApplicationContext();
        realNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        notificationHandler = new NotificationHandler(context);
    }

    @Test
    public void testCreateNotificationChannel_createsChannelOnOreoAndAbove() {
        NotificationChannel channel = realNotificationManager.getNotificationChannel(CHANNEL_ID);
        assertNotNull("Channel should be created on SDK " + Build.VERSION.SDK_INT, channel);
        assertEquals(CHANNEL_ID, channel.getId());
        assertEquals("VAC Call Screening", channel.getName().toString());
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance());
    }

    private ShadowNotificationManager getShadowManager() {
        return Shadows.shadowOf((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    }

    @Test
    public void showScreeningNotification_displaysNotification_withAction() {
        ShadowNotificationManager shadowManager = getShadowManager();
        String initialMessage = "Screening call...";
        Notification notification = notificationHandler.showScreeningNotification(initialMessage, mockPendingIntent, null);

        assertNotNull("Notification should not be null", notification);
        assertTrue("Notification should have a valid content title", 
                 Shadows.shadowOf(notification).getContentTitle() != null);
        assertTrue("Content text should match or contain initial message", 
                 Shadows.shadowOf(notification).getContentText().toString().contains(initialMessage));
        
        assertEquals(1, shadowManager.size());
        Notification postedNotification = shadowManager.getNotification(NOTIFICATION_ID);
        assertNotNull("Posted notification should not be null", postedNotification);
        
        // Check for action without assuming exact text
        assertEquals(1, postedNotification.actions.length);
    }

    @Test
    public void showScreeningNotification_displaysNotification_withoutAction() {
        ShadowNotificationManager shadowManager = getShadowManager();
        String initialMessage = "Starting up...";
        Notification notification = notificationHandler.showScreeningNotification(initialMessage, null, null);

        assertNotNull(notification);
        assertEquals(initialMessage, Shadows.shadowOf(notification).getContentText().toString());
        Notification postedNotification = shadowManager.getNotification(NOTIFICATION_ID);
        assertNotNull(postedNotification);
        assertTrue("Actions should be null or empty when no PendingIntent is provided", 
                   postedNotification.actions == null || postedNotification.actions.length == 0);
    }

    @Test
    public void updateTranscription_updatesNotificationText() {
        ShadowNotificationManager shadowManager = getShadowManager();
        String initialMessage = "Screening...";
        notificationHandler.showScreeningNotification(initialMessage, mockPendingIntent, null);

        String transcribedText = "Hello, this is a test.";
        notificationHandler.updateTranscription(transcribedText);

        Notification postedNotification = shadowManager.getNotification(NOTIFICATION_ID);
        assertNotNull(postedNotification);
        assertEquals(transcribedText, Shadows.shadowOf(postedNotification).getContentText().toString());
        // Ensure action is still present
        assertEquals(1, postedNotification.actions.length);
    }

    @Test
    public void updateNotification_updatesTextAndClearsActions() {
        ShadowNotificationManager shadowManager = getShadowManager();
        String initialMessage = "Listening...";
        notificationHandler.showScreeningNotification(initialMessage, mockPendingIntent, null);

        String newMessage = "Call taken over.";
        notificationHandler.updateNotification(context.getString(R.string.notification_title_screening), newMessage, null);

        Notification postedNotification = shadowManager.getNotification(NOTIFICATION_ID);
        assertNotNull(postedNotification);
        assertEquals(newMessage, Shadows.shadowOf(postedNotification).getContentText().toString());
        assertTrue("Actions should be empty after updateNotification", 
                   postedNotification.actions == null || postedNotification.actions.length == 0);
    }

    @Test
    public void cancelNotification_removesNotification() {
        ShadowNotificationManager shadowManager = getShadowManager();
        notificationHandler.showScreeningNotification("Test", null, null);
        assertTrue("Notification should be present before cancel", shadowManager.size() > 0);

        notificationHandler.cancelNotification();
        assertEquals("Notification should be removed after cancel", 0, shadowManager.size());
        // assertNull("Notification builder should be nulled after cancel", notificationHandler.getNotificationBuilder()); // Requires a getter for testing
    }

    // Helper method in NotificationHandler to get notificationBuilder for testing (add if needed)
    // public NotificationCompat.Builder getNotificationBuilder() { return notificationBuilder; }
} 