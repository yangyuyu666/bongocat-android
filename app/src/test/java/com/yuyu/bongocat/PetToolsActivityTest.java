package com.yuyu.bongocat;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PetToolsActivityTest {
    @Before public void reset() { RuntimeEnvironment.getApplication().getSharedPreferences("bongocat_tools", 0).edit().clear().commit(); }
    private ActivityController<PetToolsActivity> open(String page) {
        return Robolectric.buildActivity(PetToolsActivity.class, new Intent().putExtra("page", page)).setup().visible();
    }
    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = find(((ViewGroup)view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private EditText edit(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            EditText found = edit(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private View root(PetToolsActivity activity) { return activity.getWindow().getDecorView(); }
    @Test public void timerFormValidatesStartsPausesAndCancels() {
        try (ActivityController<PetToolsActivity> controller = open("timer")) {
            PetToolsActivity activity = controller.get(); PetToolsStore store = new PetToolsStore(activity);
            edit(root(activity)).setText("0"); find(root(activity), "开始专注").performClick();
            assertEquals(PetToolsStore.IDLE, store.status()); assertNotNull(edit(root(activity)).getError());
            edit(root(activity)).setText("15"); find(root(activity), "开始专注").performClick();
            assertEquals(900_000, store.duration()); assertEquals(PetToolsStore.RUNNING, store.status());
            find(root(activity), "暂停").performClick(); assertEquals(PetToolsStore.PAUSED, store.status());
            find(root(activity), "继续计时").performClick(); assertEquals(PetToolsStore.RUNNING, store.status());
            find(root(activity), "结束本次计时").performClick(); assertEquals(PetToolsStore.IDLE, store.status());
        }
    }
    @Test public void noteDraftSurvivesRecreationAndSaveIsPersistent() {
        try (ActivityController<PetToolsActivity> controller = open("note")) {
            edit(root(controller.get())).setText("记得取快递\n晚上给花浇水");
            controller.recreate();
            assertEquals("记得取快递\n晚上给花浇水", edit(root(controller.get())).getText().toString());
            find(root(controller.get()), "只保存内容").performClick();
            assertEquals("记得取快递\n晚上给花浇水", new PetToolsStore(controller.get()).note());
        }
    }
    @Test public void menuOpensTasksAndAddsAnItem() {
        try (ActivityController<PetToolsActivity> controller = open("menu")) {
            PetToolsActivity activity = controller.get(); find(root(activity), "待办清单  ›").performClick();
            edit(root(activity)).setText("整理桌面"); find(root(activity), "添加待办").performClick();
            assertNotNull(find(root(activity), "整理桌面")); assertEquals(1, new PetToolsStore(activity).tasks().size());
        }
    }
}
