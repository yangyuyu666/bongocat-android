package com.yuyu.bongocat;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PetCompanionFeaturesTest {
    private Context context;

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("bongocat_pet", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("bongocat_bond", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("bongocat_tools", Context.MODE_PRIVATE).edit().clear().commit();
        Weather.preferences(context).edit().clear().commit();
    }

    @Test public void bondStoreTracksXpFishCooldownAndDayNightGreetings() {
        PetBondStore bond = new PetBondStore(context);
        assertEquals(1, bond.level());
        assertEquals(5, bond.fish());

        assertTrue(bond.rewardPet(10_000L));
        assertFalse(bond.rewardPet(12_000L));
        assertTrue(bond.rewardPet(16_000L));
        assertEquals(2, bond.xp());
        assertEquals(7, bond.fish());

        assertTrue(bond.feedFish());
        assertEquals(8, bond.xp());
        assertEquals(4, bond.fish());

        bond.rewardFocus();
        bond.rewardFocus();
        bond.rewardFocus();
        assertTrue(bond.level() >= 2);

        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        long morning = Instant.parse("2026-11-19T00:30:00Z").toEpochMilli(); // 08:30
        String[] gm = bond.consumeGreeting(morning, shanghai);
        assertNotNull(gm);
        assertTrue(gm[0].contains("早安"));
        assertNull(bond.consumeGreeting(morning + 60_000L, shanghai));

        long lateNight = Instant.parse("2026-11-19T15:30:00Z").toEpochMilli(); // 23:30
        String[] gn = bond.consumeGreeting(lateNight, shanghai);
        assertNotNull(gn);
        assertTrue(gn[0].contains("晚"));
        // 次日凌晨 00:30 归属同一夜，不重复提醒
        assertNull(bond.consumeGreeting(lateNight + 3_600_000L, shanghai));
    }

    @Test public void wardrobePatternsAndWeatherAutoCostumeWork() {
        PetSettings settings = new PetSettings(context);
        settings.setCostume(9); // 睡帽
        settings.setPattern(4); // 暹罗
        settings.setSignStyle(2); // 抹茶便签
        PetSettings reopened = new PetSettings(context);
        assertEquals(9, reopened.getCostume());
        assertEquals(4, reopened.getPattern());
        assertEquals(2, reopened.getSignStyle());
        assertTrue(reopened.isHapticEnabled());
        assertTrue(reopened.isMusicDanceEnabled());
        assertFalse(reopened.isWeatherCostumeEnabled());

        long now = System.currentTimeMillis();
        reopened.setWeatherCostumeEnabled(true);
        new Weather(18, now, true, 15, 20, 61).save(Weather.preferences(context).edit(), now);
        assertEquals(PetSettings.COSTUME_RAINHAT, reopened.effectiveCostume(context));

        new Weather(9, now, false, 6, 12, 0).save(Weather.preferences(context).edit(), now);
        assertEquals(PetSettings.COSTUME_SCARF, reopened.effectiveCostume(context));

        new Weather(30, now, false, 24, 33, 0).save(Weather.preferences(context).edit(), now);
        assertEquals(PetSettings.COSTUME_SUNGLASSES, reopened.effectiveCostume(context));
    }

    @Test public void pinnedTaskSignAdvancesToNextOpenTaskAndRewardsBond() {
        PetToolsStore store = new PetToolsStore(context);
        PetBondStore bond = new PetBondStore(context);
        int xpBefore = bond.xp();

        store.addTask("写周报");
        store.addTask("寄快递");
        store.pinTask(true);
        assertTrue(store.hasSign());
        assertEquals("待办：写周报", store.signText());

        store.setTaskDone(store.tasks().get(0).id, true);
        assertTrue(bond.xp() > xpBefore);
        assertEquals("待办：寄快递", store.signText());

        store.setTaskDone(store.tasks().get(1).id, true);
        assertFalse(store.hasSign());
    }

    @Test public void effectsAndCustomSignStylesRenderCleanly() {
        PetEffectView effects = new PetEffectView(context);
        effects.layout(0, 0, 500, 500);
        effects.burstHearts();
        effects.burstQuestion();
        effects.burstLightning();
        effects.burstMusicNote();
        Bitmap bmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
        effects.draw(new Canvas(bmp));

        SignTextView sign = new SignTextView(context);
        sign.layout(0, 0, 500, 500);
        sign.setText("待办：记得喝水");
        for (int s = 0; s < PetSettings.SIGN_STYLE_NAMES.length; s++) {
            sign.setStyle(s);
            sign.draw(new Canvas(bmp));
        }
        bmp.recycle();
    }
}
