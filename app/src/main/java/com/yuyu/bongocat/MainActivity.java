package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 保存外观选择，并同步更新预览和正在显示的悬浮猫。 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1;
    private PetSettings settings;
    private RiveView preview;
    private boolean waitingForOverlay;
    private final java.util.List<Button> costumeButtons = new java.util.ArrayList<Button>();
    private final java.util.List<Button> colorButtons = new java.util.ArrayList<Button>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_weather).setOnClickListener(view -> startActivity(new Intent(this, WeatherActivity.class)));
        settings = new PetSettings(this);
        waitingForOverlay = savedInstanceState != null
                && savedInstanceState.getBoolean("waiting_for_overlay", false);
        preview = findViewById(R.id.preview_cat);
        applyPreview();

        buildCostumeRow();
        buildColorRow();
        buildSliders();
        refreshButtonStates();
        findViewById(R.id.btn_tools).setOnClickListener(view -> startActivity(new Intent(this, PetToolsActivity.class)));

        Button btnShow = findViewById(R.id.btn_show);
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showPet();
            }
        });

        Button btnHide = findViewById(R.id.btn_hide);
        btnHide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                settings.setVisible(false);
                stopService(new Intent(MainActivity.this, FloatingPetService.class));
                Toast.makeText(MainActivity.this, "猫收起来了", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnActions = findViewById(R.id.btn_actions);
        btnActions.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ActionsActivity.class));
            }
        });
    }

    private void buildCostumeRow() {
        LinearLayout container = findViewById(R.id.costume_row);
        LinearLayout row = null;
        for (int i = 0; i < PetSettings.COSTUME_NAMES.length; i++) {
            if (i % 3 == 0) row = addButtonRow(container);
            final int idx = i;
            Button b = new Button(this);
            b.setText(PetSettings.COSTUME_NAMES[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(dp(48));
            b.setPadding(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    settings.setCostume(idx);
                    refresh();
                }
            });
            row.addView(b);
            costumeButtons.add(b);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout addButtonRow(LinearLayout container) {
        LinearLayout row = new LinearLayout(this);
        container.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** 用黄色和选中语义标出当前项，并保持深色按钮的文字可读。 */
    private void refreshButtonStates() {
        for (int i = 0; i < costumeButtons.size(); i++) {
            styleChoice(costumeButtons.get(i), i == settings.getCostume(), 0xFFE0E0E0);
        }
        for (int i = 0; i < colorButtons.size(); i++) {
            styleChoice(colorButtons.get(i), i == settings.getColor(), PetSettings.COLOR_TINTS[i]);
        }
    }

    private void styleChoice(Button button, boolean selected, int color) {
        int background = selected ? 0xFFFFE082 : color;
        button.setSelected(selected);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(Color.luminance(background) < 0.3f ? Color.WHITE : Color.BLACK);
        button.setContentDescription(button.getText() + (selected ? "，已选中" : ""));
    }

    private void buildColorRow() {
        LinearLayout container = findViewById(R.id.color_row);
        LinearLayout row = null;
        for (int i = 0; i < PetSettings.COLOR_NAMES.length; i++) {
            if (i % 4 == 0) row = addButtonRow(container);
            final int idx = i;
            Button b = new Button(this);
            b.setText(PetSettings.COLOR_NAMES[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(dp(48));
            b.setPadding(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    settings.setColor(idx);
                    refresh();
                }
            });
            row.addView(b);
            colorButtons.add(b);
        }
    }

    private void buildSliders() {
        final TextView labelSize = findViewById(R.id.label_size);
        SeekBar seekSize = findViewById(R.id.seek_size);
        seekSize.setProgress(settings.getSizeDp() - 120);
        labelSize.setText("大小：" + settings.getSizeDp() + "dp");
        seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int dp = 120 + p;
                settings.setSizeDp(dp);
                labelSize.setText("大小：" + dp + "dp");
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        final TextView labelOpacity = findViewById(R.id.label_opacity);
        SeekBar seekOpacity = findViewById(R.id.seek_opacity);
        seekOpacity.setProgress(settings.getOpacity() - 10);
        labelOpacity.setText("透明度：" + settings.getOpacity() + "%");
        seekOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int pct = 10 + p;
                settings.setOpacity(pct);
                labelOpacity.setText("透明度：" + pct + "%");
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void refresh() {
        applyPreview();
        refreshButtonStates();
        if (settings.isVisible()) {
            if (!Settings.canDrawOverlays(this)) {
                settings.setVisible(false);
                stopService(new Intent(this, FloatingPetService.class));
                return;
            }
            // 向现有服务发送外观设置，窗口坐标保持不变。
            Intent i = new Intent(this, FloatingPetService.class);
            i.setAction(FloatingPetService.ACTION_UPDATE_SETTINGS);
            startService(i);
        }
    }

    /** 把设置中的 dp 尺寸转换成像素，写入预览布局。 */
    private void applyPreview() {
        preview.applySettings(settings);
        int px = dp(settings.getSizeDp());
        android.view.ViewGroup.LayoutParams lp = preview.getLayoutParams();
        lp.width = px;
        lp.height = px;
        preview.setLayoutParams(lp);
        android.view.ViewGroup stage = (android.view.ViewGroup) preview.getParent();
        android.view.ViewGroup.LayoutParams stageParams = stage.getLayoutParams();
        stageParams.height = px + dp(64);
        stage.setLayoutParams(stageParams);
    }

    private void showPet() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            waitingForOverlay = true;
            startActivityForResult(i, REQ_OVERLAY);
            Toast.makeText(this, "请允许悬浮窗权限，返回后自动显示", Toast.LENGTH_LONG).show();
            return;
        }
        settings.setVisible(true);
        startForegroundService(new Intent(this, FloatingPetService.class));
        Toast.makeText(this, "猫出来了，拖着走；趴下或睡着后点一下起身", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) finishOverlayRequest();
    }

    private void finishOverlayRequest() {
        if (!waitingForOverlay) return;
        waitingForOverlay = false;
        if (Settings.canDrawOverlays(this)) showPet();
    }

    @Override protected void onResume() {
        super.onResume();
        preview.setBehaviorActive(true);
        // 部分系统的授权页面不回传结果，返回前台时再检查一次。
        finishOverlayRequest();
        if (!Settings.canDrawOverlays(this)) {
            settings.setVisible(false);
            stopService(new Intent(this, FloatingPetService.class));
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("waiting_for_overlay", waitingForOverlay);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onPause() {
        preview.setBehaviorActive(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        preview.release();
        super.onDestroy();
    }
}
