package com.yuyu.bongocat;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PetToolsVisualTest {
    private void capture(View view, int width, int maximumHeight, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, width, view.getMeasuredHeight());
        assertTrue(view.getHeight() > 0 && view.getHeight() <= maximumHeight);
        Bitmap bitmap = Bitmap.createBitmap(width, view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File directory = new File(System.getProperty("bongocat.projectDir"), "build/tools-preview");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        bitmap.recycle();
    }
    @Test public void renderActualAndroidPanelsAndSignText() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("bongocat_tools", 0).edit().clear().commit();
        PetToolsStore store = new PetToolsStore(app);
        store.saveNote("记得取快递\n晚上给花浇水", true); store.addTask("准备明天的汇报"); store.addTask("喝一杯水");
        for (String page : new String[]{"menu", "timer", "note", "read", "tasks"}) {
            try (ActivityController<PetToolsActivity> controller = Robolectric.buildActivity(PetToolsActivity.class, new Intent().putExtra("page", page)).setup()) {
                ViewGroup content = controller.get().findViewById(android.R.id.content);
                capture(content.getChildAt(0), 1008, 2100, page);
            }
        }
        store.start(25);
        try (ActivityController<PetToolsActivity> controller = Robolectric.buildActivity(PetToolsActivity.class, new Intent().putExtra("page", "timer")).setup()) {
            ViewGroup content = controller.get().findViewById(android.R.id.content);
            capture(content.getChildAt(0), 1008, 2100, "running");
        }
        SignTextView sign = new SignTextView(app); sign.setMinimumHeight(500); sign.setText(store.note());
        capture(sign, 500, 500, "sign-text");
        assertTrue(sign.hits(240, 360)); assertFalse(sign.hits(240, 220));
    }
}
