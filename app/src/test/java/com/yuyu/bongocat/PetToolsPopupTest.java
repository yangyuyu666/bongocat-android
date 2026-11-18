package com.yuyu.bongocat;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34}, application = Application.class, qualifiers = "w411dp-h891dp")
public class PetToolsPopupTest {
    private Application app;

    @Before public void reset() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("bongocat_tools", 0).edit().clear().commit();
    }

    private void assertPopupIntent(Intent intent, String page) throws Exception {
        assertNotNull(intent);
        assertEquals(new ComponentName(app, PetToolsPopupActivity.class), intent.getComponent());
        assertEquals(page, intent.getStringExtra("page"));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
        ActivityInfo info = app.getPackageManager().getActivityInfo(intent.getComponent(), 0);
        assertTrue(info.taskAffinity == null || info.taskAffinity.isEmpty());
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode);
        assertTrue((info.flags & ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0);
        assertFalse(info.exported);
    }

    private Intent anchoredIntent(String page) {
        return PetToolsPopupActivity.intent(app, page)
                .putExtra("pet_x", 80).putExtra("pet_y", 300).putExtra("pet_width", 140);
    }

    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = find(((ViewGroup)view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private EditText edit(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            EditText found = edit(((ViewGroup)view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Test public void floatingMenuAndBoardUseIsolatedPopupWithPetCoordinates() throws Exception {
        String receiverPermission = app.getPackageName() + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
        assertEquals(app.getPackageName(), app.getPackageManager().getPermissionInfo(receiverPermission, 0).packageName);
        // Robolectric 需要补上安装时系统自动授予的应用签名权限。
        shadowOf(app).grantPermissions(receiverPermission);
        ServiceController<FloatingPetService> controller = Robolectric.buildService(FloatingPetService.class).create();
        try {
            FloatingPetService service = controller.get();
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.x = 80; params.y = 300; params.width = 140;
            ReflectionHelpers.setField(service, "params", params);
            for (String page : new String[]{"menu", "read", "timer"}) {
                ReflectionHelpers.callInstanceMethod(service, "openTools", ReflectionHelpers.ClassParameter.from(String.class, page));
                Intent launched = shadowOf(app).getNextStartedActivity();
                assertPopupIntent(launched, page);
                assertEquals(80, launched.getIntExtra("pet_x", -1));
                assertEquals(300, launched.getIntExtra("pet_y", -1));
                assertEquals(140, launched.getIntExtra("pet_width", -1));
                assertNull(shadowOf(app).getNextStartedActivity());
            }
        } finally { controller.destroy(); }
        ActivityInfo normal = app.getPackageManager().getActivityInfo(new ComponentName(app, PetToolsActivity.class), 0);
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, normal.launchMode);
        assertEquals(app.getPackageName(), normal.taskAffinity);
    }

    @Test public void popupKeepsFormsNearPetAndClosesWithoutStartingMain() {
        try (ActivityController<PetToolsPopupActivity> controller = Robolectric.buildActivity(PetToolsPopupActivity.class, anchoredIntent("menu")).setup().visible()) {
            PetToolsPopupActivity activity = controller.get();
            TypedArray style = activity.obtainStyledAttributes(new int[]{android.R.attr.windowIsFloating, android.R.attr.windowDisablePreview});
            assertTrue(style.getBoolean(0, false)); assertTrue(style.getBoolean(1, false)); style.recycle();
            for (String title : new String[]{"番茄钟  ›", "便利贴  ›", "待办清单  ›"}) {
                find(activity.getWindow().getDecorView(), title).performClick();
                WindowManager.LayoutParams position = activity.getWindow().getAttributes();
                assertEquals(Gravity.TOP | Gravity.LEFT, position.gravity);
                assertTrue(position.x >= 0 && position.y >= 0);
                activity.onBackPressed();
                assertNotNull(find(activity.getWindow().getDecorView(), "猫咪工具箱"));
            }
            find(activity.getWindow().getDecorView(), "收起菜单").performClick();
            assertTrue(activity.isFinishing());
            assertNull(shadowOf(app).getNextStartedActivity());
        }
    }

    @Test public void popupNoteDraftAndAnchorSurviveRecreation() {
        try (ActivityController<PetToolsPopupActivity> controller = Robolectric.buildActivity(PetToolsPopupActivity.class, anchoredIntent("note")).setup().visible()) {
            edit(controller.get().getWindow().getDecorView()).setText("下午记得取快递");
            controller.recreate();
            PetToolsPopupActivity activity = controller.get();
            assertEquals("下午记得取快递", edit(activity.getWindow().getDecorView()).getText().toString());
            assertEquals(Gravity.TOP | Gravity.LEFT, activity.getWindow().getAttributes().gravity);
            find(activity.getWindow().getDecorView(), "只保存内容").performClick();
            assertEquals("下午记得取快递", new PetToolsStore(app).note());
            assertNull(shadowOf(app).getNextStartedActivity());
        }
    }

    @Test public void reminderAlsoOpensOnlyItsPopup() throws Exception {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        PetToolsStore store = new PetToolsStore(app);
        store.start(1); ShadowSystemClock.advanceBy(Duration.ofMinutes(1));
        PetReminder.checkDue(app, store.token());
        Notification notification = shadowOf(app.getSystemService(NotificationManager.class)).getNotification(PetReminder.NOTIFICATION);
        assertNotNull(notification);
        notification.contentIntent.send();
        assertPopupIntent(shadowOf(app).getNextStartedActivity(), "timer");
        assertNull(shadowOf(app).getNextStartedActivity());
    }
}
