package com.example.vac.activities;

import android.media.MediaPlayer;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;

import com.example.vac.R;
import com.example.vac.adapters.MessageAdapter;
import com.example.vac.models.Message;
import com.example.vac.models.TranscriptionData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = {ShadowMediaPlayer.class})
public class MessagesActivityTest {

    @Mock
    private MediaPlayer mockMediaPlayer;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void test_loadMessageList_displaysEmptyViewWhenNoMessages() {
        File testDir = RuntimeEnvironment.getApplication().getFilesDir();
        File[] existingFiles = testDir.listFiles((d, name) -> name.startsWith("message_") && name.endsWith(".3gp"));
        if (existingFiles != null) {
            for (File file : existingFiles) {
                file.delete();
            }
        }

        try (ActivityScenario<MessagesActivity> scenario = ActivityScenario.launch(MessagesActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_view_messages);
                TextView emptyView = activity.findViewById(R.id.tv_empty_messages);

                assertEquals(View.GONE, recyclerView.getVisibility());
                assertEquals(View.VISIBLE, emptyView.getVisibility());
                assertEquals(activity.getString(R.string.no_messages), emptyView.getText().toString());
            });
        }
    }

    @Test
    public void test_loadMessageList_displaysMessagesWhenAvailable() throws IOException {
        File testDir = RuntimeEnvironment.getApplication().getFilesDir();
        List<File> testFiles = createTestMessageFiles(testDir, 3);

        try (ActivityScenario<MessagesActivity> scenario = ActivityScenario.launch(MessagesActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.recycler_view_messages);
                TextView emptyView = activity.findViewById(R.id.tv_empty_messages);

                assertEquals(View.VISIBLE, recyclerView.getVisibility());
                assertEquals(View.GONE, emptyView.getVisibility());
                MessageAdapter adapter = (MessageAdapter) recyclerView.getAdapter();
                assertNotNull(adapter);
                assertEquals(3, adapter.getItemCount());
            });
        }

        for (File file : testFiles) {
            file.delete();
        }
    }

    @Test
    public void test_deleteMessageRemovesFile() throws IOException {
        File testDir = RuntimeEnvironment.getApplication().getFilesDir();
        List<File> testFiles = createTestMessageFiles(testDir, 1);
        File testFile = testFiles.get(0);
        assertTrue(testFile.exists());

        try (ActivityScenario<MessagesActivity> scenario = ActivityScenario.launch(MessagesActivity.class)) {
            scenario.onActivity(activity -> {
                Message testMessage = new Message(testFile);
                activity.onDeleteMessage(testMessage);
                assertEquals(activity.getString(R.string.message_deleted), ShadowToast.getTextOfLatestToast());
                assertTrue(!testFile.exists());
            });
        }
    }

    private List<File> createTestMessageFiles(File directory, int count) throws IOException {
        List<File> files = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            File file = new File(directory, "message_" + (timestamp + i) + ".3gp");
            file.createNewFile();
            files.add(file);
        }
        return files;
    }
}
