package com.yuyu.bongocat;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PetToolsTest {
    private Context context;
    private PetToolsStore store;
    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("bongocat_tools", 0).edit().clear().commit();
        store = new PetToolsStore(context);
    }

    @Test public void pauseResumeAndRestartPreserveRemainingTime() {
        store.start(25);
        ShadowSystemClock.advanceBy(Duration.ofMinutes(3));
        assertEquals(22 * 60_000, new PetToolsStore(context).remaining());
        store.pause();
        ShadowSystemClock.advanceBy(Duration.ofHours(1));
        assertEquals(22 * 60_000, new PetToolsStore(context).remaining());
        store.resume();
        ShadowSystemClock.advanceBy(Duration.ofMinutes(22));
        assertTrue(store.finishIfDue(store.token()));
        assertFalse(store.finishIfDue(store.token()));
        assertEquals(PetToolsStore.DONE, new PetToolsStore(context).status());
    }

    @Test public void sameBootIgnoresWallClockChangesAndRebootUsesSavedDate() {
        store.start(10);
        long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        assertEquals(600_000, store.remaining(wall + 86_400_000, elapsed, 0));
        assertEquals(480_000, store.remaining(wall + 120_000, 0, 1), 100);
        assertEquals(0, store.remaining(wall + 700_000, 0, 1));
    }

    @Test public void staleAlarmCannotFinishNewTimerOrPausedTimer() {
        store.start(1); String old = store.token();
        store.start(2); ShadowSystemClock.advanceBy(Duration.ofMinutes(1));
        new PetTimerReceiver().onReceive(context, new Intent(PetReminder.ALARM).putExtra("token", old));
        assertEquals(PetToolsStore.RUNNING, store.status());
        store.pause(); ShadowSystemClock.advanceBy(Duration.ofMinutes(3));
        PetReminder.checkDue(context, store.token());
        assertEquals(PetToolsStore.PAUSED, store.status());
        store.cancel(); assertEquals(0, store.remaining());
    }

    @Test public void dueTimerPostsNotificationOnceAndRestoresPinnedNoteAfterAck() {
        store.saveNote("下午三点开会", true); store.start(1); PetReminder.schedule(context);
        assertNotNull(shadowOf(context.getSystemService(AlarmManager.class)).getNextScheduledAlarm());
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1));
        PetReminder.checkDue(context, store.token()); PetReminder.checkDue(context, store.token());
        Notification notification = shadowOf(context.getSystemService(NotificationManager.class)).getNotification(PetReminder.NOTIFICATION);
        assertNotNull(notification); assertEquals("时间到啦，休息一下吧", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager.class)).getAllNotifications().size());
        assertEquals("时间到啦！\n休息一下吧", store.signText());
        PetReminder.acknowledge(context);
        assertEquals("下午三点开会", store.signText()); assertTrue(store.hasSign());
        assertNull(shadowOf(context.getSystemService(NotificationManager.class)).getNotification(PetReminder.NOTIFICATION));
    }

    @Test public void disabledNotificationStillKeepsCompletionSign() {
        shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);
        store.start(1); ShadowSystemClock.advanceBy(Duration.ofMinutes(1)); PetReminder.checkDue(context, store.token());
        assertEquals(PetToolsStore.DONE, store.status()); assertTrue(store.hasSign());
        assertTrue(shadowOf(context.getSystemService(NotificationManager.class)).getAllNotifications().isEmpty());
    }

    @Test public void packageReplacementRestoresAlarmButUnrelatedBroadcastDoesNothing() {
        store.start(10);
        new PetBootReceiver().onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        assertNotNull(shadowOf(context.getSystemService(AlarmManager.class)).getNextScheduledAlarm());
        store.pause(); PetReminder.schedule(context);
        assertNull(shadowOf(context.getSystemService(AlarmManager.class)).getNextScheduledAlarm());
        new PetBootReceiver().onReceive(context, new Intent("unrelated"));
        assertEquals(PetToolsStore.PAUSED, store.status());
    }

    @Test public void notesAndDuplicateTasksKeepIndependentPersistentState() {
        store.saveNote("第一行\n第二行🙂", true);
        assertEquals("第一行\n第二行🙂", new PetToolsStore(context).note());
        store.pinNote(false); assertFalse(store.hasSign()); assertFalse(store.note().isEmpty());
        assertTrue(store.addTask("喝水")); assertTrue(store.addTask("喝水"));
        store.setTaskDone(store.tasks().get(0).id, true);
        assertTrue(new PetToolsStore(context).tasks().get(0).done); assertFalse(store.tasks().get(1).done);
        store.clearCompleted(); assertEquals(1, store.tasks().size());
        assertFalse(store.addTask("  "));
        for (int i = 0; i < 19; i++) assertTrue(store.addTask("待办 " + i));
        assertFalse(store.addTask("超出数量"));
    }

    @Test public void durationValidationAndScreenEdgesAreBounded() {
        for (int value : new int[]{-1, 0, 361, Integer.MAX_VALUE}) {
            try { store.start(value); fail("必须拒绝越界时长"); } catch (IllegalArgumentException expected) { }
        }
        assertEquals("01:00", PetToolsStore.formatTime(59_001));
        for (int x : new int[]{0, 200, 900}) for (int y : new int[]{0, 300, 1900}) {
            int[] p = ToolPopupPlacement.place(1080, 1920, x, y, 180, 336, 500, 12);
            assertTrue(p[0] >= 12 && p[0] + 336 <= 1068); assertTrue(p[1] >= 12 && p[1] + 500 <= 1908);
        }
    }
}
