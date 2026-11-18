package com.yuyu.bongocat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PetPermissionsTest {
    private Context context;
    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("bongocat_tools", 0).edit().clear().commit();
    }
    @Test public void exactAlarmPermissionSwitchesSchedulingWithoutLosingTimer() {
        PetToolsStore store = new PetToolsStore(context); store.start(25);
        ShadowAlarmManager.setCanScheduleExactAlarms(false); PetReminder.schedule(context);
        ShadowAlarmManager alarms = shadowOf(context.getSystemService(AlarmManager.class));
        assertTrue(alarms.peekNextScheduledAlarm().isAllowWhileIdle());
        assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, alarms.peekNextScheduledAlarm().getWindowLengthMs());
        ShadowAlarmManager.setCanScheduleExactAlarms(true); PetReminder.schedule(context);
        assertEquals(1, alarms.getScheduledAlarms().size());
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarms.peekNextScheduledAlarm().getWindowLengthMs());
        assertEquals(PetToolsStore.RUNNING, store.status());
        store.cancel(); PetReminder.schedule(context); assertTrue(alarms.getScheduledAlarms().isEmpty());
    }
    @Test public void android14NotificationGrantAndDenialKeepBoardReminder() {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertFalse(PetReminder.notificationsAllowed(context));
        PetToolsStore store = new PetToolsStore(context); store.start(1);
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1)); PetReminder.checkDue(context, store.token());
        assertEquals(PetToolsStore.DONE, store.status());
        assertTrue(shadowOf(context.getSystemService(NotificationManager.class)).getAllNotifications().isEmpty());
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        assertTrue(PetReminder.notificationsAllowed(context));
        store.start(1); ShadowSystemClock.advanceBy(Duration.ofMinutes(1)); PetReminder.checkDue(context, store.token());
        assertNotNull(shadowOf(context.getSystemService(NotificationManager.class)).getNotification(PetReminder.NOTIFICATION));
    }
}
