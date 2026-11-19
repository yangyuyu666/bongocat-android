package com.yuyu.bongocat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 系统下拉快捷设置磁贴：一键切换显示或收起桌面悬浮猫。 */
public final class PetQuickTileService extends TileService {

    public static void requestSync(Context context) {
        try {
            requestListeningState(context.getApplicationContext(),
                    new ComponentName(context.getApplicationContext(), PetQuickTileService.class));
        } catch (RuntimeException ignored) {
            // 部分测试环境或未添加磁贴时忽略。
        }
    }

    @Override public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override public void onClick() {
        super.onClick();
        PetSettings settings = new PetSettings(this);
        boolean active = settings.isVisible() && Settings.canDrawOverlays(this);
        if (active) {
            settings.setVisible(false);
            stopService(new Intent(this, FloatingPetService.class));
        } else if (Settings.canDrawOverlays(this)) {
            settings.setVisible(true);
            startForegroundService(new Intent(this, FloatingPetService.class));
        } else {
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(open);
        }
        syncTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        PetSettings settings = new PetSettings(this);
        boolean active = settings.isVisible() && Settings.canDrawOverlays(this);
        tile.setLabel("桌面小猫");
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
