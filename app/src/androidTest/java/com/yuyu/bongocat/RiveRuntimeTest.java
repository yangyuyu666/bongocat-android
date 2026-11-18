package com.yuyu.bongocat;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.rive.runtime.kotlin.RiveAnimationView;
import app.rive.runtime.kotlin.core.Direction;
import app.rive.runtime.kotlin.core.Loop;
import app.rive.runtime.kotlin.core.ViewModelInstance;
import app.rive.runtime.kotlin.core.errors.ViewModelException;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 在 Android 原生运行时读取 APK 中的正式 Rive 模型。 */
@RunWith(AndroidJUnit4.class)
public class RiveRuntimeTest {
    private static RiveAnimationView renderer(RiveView view) {
        return (RiveAnimationView) view.getChildAt(0);
    }

    private static ViewModelInstance model(RiveView view) {
        return renderer(view).getController().getActiveArtboard().getViewModelInstance();
    }

    private static void advance(RiveView view, int frames) {
        renderer(view).play("PetStateMachine", Loop.AUTO, Direction.AUTO, true, false);
        for (int i = 0; i < frames; i++) renderer(view).getController().advance(1f / 60);
    }

    @Test public void twoInstancesBindAllAppearancesIndependently() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            // 测试使用单独的首选项文件，不改用户已经保存的外观和坐标。
            Context testContext = new ContextWrapper(app) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences("rive_test_" + name, mode);
                }
            };
            SharedPreferences prefs = testContext.getSharedPreferences("bongocat_pet", Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            RiveView first = new RiveView(app);
            RiveView second = new RiveView(app);
            try {
                PetSettings settings = new PetSettings(testContext);
                second.applySettings(settings);
                advance(second, 1);
                assertNotSame(model(first), model(second));
                for (int costume = 0; costume < 6; costume++) {
                    for (int color = 0; color < 8; color++) {
                        settings.setCostume(costume);
                        settings.setColor(color);
                        settings.setOpacity(35);
                        first.applySettings(settings);
                        advance(first, 1);
                        assertEquals(costume, model(first).getNumberProperty("costume").getValue(), 0);
                        assertEquals(PetSettings.COLOR_TINTS[color], model(first).getColorProperty("bodyColor").getValue().intValue());
                        assertEquals(0.35f, first.getAlpha(), 0.001f);
                        assertEquals(0, model(second).getNumberProperty("costume").getValue(), 0);
                        assertEquals(PetSettings.COLOR_TINTS[0], model(second).getColorProperty("bodyColor").getValue().intValue());
                    }
                }
            } catch (ViewModelException e) {
                throw new AssertionError("外观字段未绑定", e);
            } finally {
                first.release();
                second.release();
                prefs.edit().clear().commit();
            }
        });
    }

    @Test public void packagedModelSleepsAndWakesThroughTheSharedTouchEntry() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RiveView view = new RiveView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                advance(view, 5401);
                assertEquals(9, Math.round(model(view).getNumberProperty("state").getValue()));
                advance(view, 362);
                assertEquals(10, Math.round(model(view).getNumberProperty("state").getValue()));
                view.tapAlternate();
                advance(view, 51);
                assertEquals(14, Math.round(model(view).getNumberProperty("state").getValue()));
                advance(view, 5);
                assertEquals(0, Math.round(model(view).getNumberProperty("state").getValue()));
                assertTrue(model(view).getNumberProperty("inactive").getValue() < 1);
            } catch (ViewModelException e) {
                throw new AssertionError("睡眠字段未绑定", e);
            } finally {
                view.release();
            }
        });
    }

    @Test public void signHoldsAndReleasesUsingThePackagedModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RiveView view = new RiveView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                view.layout(0, 0, 500, 500);
                view.setSign("记得取快递"); advance(view, 60);
                assertEquals(26, Math.round(model(view).getNumberProperty("state").getValue()));
                view.playAction("playRoll"); advance(view, 60);
                assertEquals(26, Math.round(model(view).getNumberProperty("state").getValue()));
                view.setSign(null); advance(view, 3);
                assertEquals(0, Math.round(model(view).getNumberProperty("state").getValue()));
            } catch (ViewModelException exception) { throw new AssertionError("举牌字段未绑定", exception); }
            finally { view.release(); }
        });
    }

    @Test public void partClicksAndEdgeModeUseThePackagedBindings() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RiveView view = new RiveView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                view.layout(0, 0, 500, 500);
                advance(view, 1);
                view.performPartClick(402, 300);
                advance(view, 30);
                assertEquals(3, Math.round(model(view).getNumberProperty("state").getValue()));
                advance(view, 152);
                view.performPartClick(240, 155);
                advance(view, 24);
                assertEquals(17, Math.round(model(view).getNumberProperty("state").getValue()));
                view.setEdgeMode(true, false, true);
                advance(view, 30);
                assertEquals(21, Math.round(model(view).getNumberProperty("state").getValue()));
                view.setEdgeMode(true, true, true);
                advance(view, 30);
                assertEquals(23, Math.round(model(view).getNumberProperty("state").getValue()));
                view.setEdgePresentation(500, true);
                android.widget.FrameLayout.LayoutParams layout = (android.widget.FrameLayout.LayoutParams) renderer(view).getLayoutParams();
                assertEquals(500, layout.height);
                assertEquals(-275, layout.topMargin);
                view.setEdgeMode(false, false, false);
                advance(view, 3);
                assertEquals(0, Math.round(model(view).getNumberProperty("state").getValue()));
            } catch (ViewModelException e) {
                throw new AssertionError("交互字段未绑定", e);
            } finally { view.release(); }
        });
    }
}
