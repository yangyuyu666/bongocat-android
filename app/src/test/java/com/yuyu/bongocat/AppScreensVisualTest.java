package com.yuyu.bongocat;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppScreensVisualTest {
    private void capture(View view, int width, int height, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        int measuredHeight = Math.max(height, view.getMeasuredHeight());
        view.layout(0, 0, width, measuredHeight);
        assertTrue(view.getHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(width, Math.min(measuredHeight, 2673), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File directory = new File(System.getProperty("bongocat.projectDir"), "build/tools-preview");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }

    @Test public void renderUpgradedActionsAndWeatherScreens() throws Exception {
        try (var actions = Robolectric.buildActivity(ActionsActivity.class).setup()) {
            ViewGroup content = actions.get().findViewById(android.R.id.content);
            capture(content.getChildAt(0), 1233, 2673, "actions-screen");
        }
        try (var weather = Robolectric.buildActivity(WeatherActivity.class).setup()) {
            ViewGroup content = weather.get().findViewById(android.R.id.content);
            capture(content.getChildAt(0), 1233, 2673, "weather-screen");
        }
    }
}
