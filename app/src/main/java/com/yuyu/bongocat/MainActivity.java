package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 保存外观选择，并同步更新预览和正在显示的悬浮猫。 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1;
    private PetSettings settings;
    private RiveView preview;
    private TextView statusPill, bondTitle, bondDetail;
    private ProgressBar bondProgress;
    private Button btnActions, btnTools, btnWeather, btnFeed;
    private Switch touchThrough, musicDance, weatherCostume, hapticEnabled;
    private boolean waitingForOverlay;
    private final java.util.List<Button> costumeButtons = new java.util.ArrayList<Button>();
    private final java.util.List<Button> patternButtons = new java.util.ArrayList<Button>();
    private final java.util.List<Button> colorButtons = new java.util.ArrayList<Button>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyle.applySystemBars(this);
        setContentView(R.layout.activity_main);
        settings = new PetSettings(this);
        waitingForOverlay = savedInstanceState != null
                && savedInstanceState.getBoolean("waiting_for_overlay", false);

        styleStaticContainers();

        preview = findViewById(R.id.preview_cat);
        preview.setBondListener(this::refreshBondCard);
        applyPreview();

        bondTitle = findViewById(R.id.bond_title);
        bondDetail = findViewById(R.id.bond_detail);
        bondProgress = findViewById(R.id.bond_progress);
        bondProgress.setProgressTintList(ColorStateList.valueOf(UiStyle.ACCENT));
        bondProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFF3E4EA));
        btnFeed = findViewById(R.id.btn_feed);
        UiStyle.styleSecondaryButton(this, btnFeed);
        btnFeed.setOnClickListener(v -> feedFish());
        refreshBondCard();

        buildCostumeRow();
        buildPatternRow();
        buildColorRow();
        buildSliders();
        buildSwitches();

        refreshButtonStates();

        btnActions = findViewById(R.id.btn_actions);
        btnTools = findViewById(R.id.btn_tools);
        btnWeather = findViewById(R.id.btn_weather);
        btnActions.setOnClickListener(v -> startActivity(new Intent(this, ActionsActivity.class)));
        btnTools.setOnClickListener(v -> startActivity(new Intent(this, PetToolsActivity.class)));
        btnWeather.setOnClickListener(v -> startActivity(new Intent(this, WeatherActivity.class)));
        refreshHubCards();

        Button btnShow = findViewById(R.id.btn_show);
        UiStyle.stylePrimaryButton(this, btnShow);
        btnShow.setOnClickListener(v -> showPet());

        Button btnHide = findViewById(R.id.btn_hide);
        UiStyle.styleSecondaryButton(this, btnHide);
        btnHide.setOnClickListener(v -> {
            settings.setVisible(false);
            stopService(new Intent(MainActivity.this, FloatingPetService.class));
            PetQuickTileService.requestSync(this);
            refreshStatusPill();
            Toast.makeText(MainActivity.this, "猫收起来了", Toast.LENGTH_SHORT).show();
        });

        statusPill = findViewById(R.id.status_pill);
        refreshStatusPill();
    }

    private void buildSwitches() {
        touchThrough = styleSwitch(R.id.touch_through, settings.isTouchThrough(), enabled -> {
            settings.setTouchThrough(enabled);
            refresh();
        });
        musicDance = styleSwitch(R.id.music_dance, settings.isMusicDanceEnabled(), enabled -> {
            settings.setMusicDanceEnabled(enabled);
            refresh();
        });
        weatherCostume = styleSwitch(R.id.weather_costume, settings.isWeatherCostumeEnabled(), enabled -> {
            settings.setWeatherCostumeEnabled(enabled);
            refresh();
        });
        hapticEnabled = styleSwitch(R.id.haptic_enabled, settings.isHapticEnabled(), enabled -> {
            settings.setHapticEnabled(enabled);
            refresh();
        });
    }

    private Switch styleSwitch(int id, boolean checked, java.util.function.Consumer<Boolean> onToggle) {
        Switch sw = findViewById(id);
        sw.setChecked(checked);
        sw.setThumbTintList(ColorStateList.valueOf(UiStyle.ACCENT));
        sw.setTrackTintList(ColorStateList.valueOf(0xFFEBD3DC));
        sw.setOnCheckedChangeListener((button, enabled) -> onToggle.accept(enabled));
        return sw;
    }

    private void feedFish() {
        PetBondStore bond = new PetBondStore(this);
        if (bond.feedFish()) {
            preview.playAction("playLick");
            preview.burstHearts();
            preview.triggerHaptic();
            refreshBondCard();
            Toast.makeText(this, "喂了小鱼干，亲密度 +6！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "小鱼干不足（摸头、完成待办或番茄钟可获得）", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshBondCard() {
        if (bondTitle == null) return;
        PetBondStore bond = new PetBondStore(this);
        bondTitle.setText("Lv." + bond.level() + " " + bond.levelTitle());
        bondDetail.setText(bond.nextLevelHint() + " · 小鱼干 " + bond.fish());
        bondProgress.setProgress(bond.progressPercent());
        btnFeed.setText("喂小鱼干 (" + PetBondStore.FEED_COST + ")");
    }

    private void styleStaticContainers() {
        View stage = findViewById(R.id.stage_card);
        stage.setBackground(UiStyle.gradientCard(this, UiStyle.STAGE_TOP, UiStyle.STAGE_BOTTOM, UiStyle.BORDER, 24f));
        View hint = findViewById(R.id.stage_hint);
        hint.setBackground(UiStyle.card(this, 0xCCFFFFFF, 0xFFF2E2E8, 99f));

        View bond = findViewById(R.id.card_bond);
        bond.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 20f));

        View appearance = findViewById(R.id.card_appearance);
        appearance.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 22f));

        View display = findViewById(R.id.card_display);
        display.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 22f));
    }

    private void refreshStatusPill() {
        if (statusPill == null) return;
        boolean visible = settings.isVisible() && Settings.canDrawOverlays(this);
        statusPill.setText(visible ? "● 桌面陪伴中" : "○ 未开启");
        statusPill.setTextColor(visible ? UiStyle.SUCCESS : UiStyle.MUTED);
        statusPill.setBackground(UiStyle.card(this,
                visible ? UiStyle.SUCCESS_SOFT : UiStyle.SURFACE,
                visible ? 0xFFBDE3CE : UiStyle.BORDER, 99f));
    }

    private void refreshHubCards() {
        styleHubButton(btnActions, "动作剧场", "9 种表演", 0xFFFFF4F7, 0xFFF4D3DF);
        PetToolsStore tools = new PetToolsStore(this);
        String toolsSub = tools.status() == PetToolsStore.RUNNING
                ? "专注 " + PetToolsStore.formatTime(tools.remaining())
                : tools.status() == PetToolsStore.PAUSED ? "番茄已暂停" : "番茄·便签";
        styleHubButton(btnTools, "猫咪工具箱", toolsSub, 0xFFFFF7EE, 0xFFF3DEC6);

        SharedPreferences weatherPrefs = Weather.preferences(this);
        String city = weatherPrefs.getString("city", "");
        String shortCity = city.contains("·") ? city.substring(city.lastIndexOf('·') + 1) : city;
        String weatherSub;
        if (city.isEmpty()) {
            weatherSub = "选择位置";
        } else if (Weather.fresh(weatherPrefs.getLong("observed", 0), System.currentTimeMillis(), 2 * Weather.HOUR)) {
            boolean rain = weatherPrefs.getBoolean("rain", false);
            weatherSub = shortCity + " " + Math.round(weatherPrefs.getFloat("temp", 0)) + "°" + (rain ? " 有雨" : "");
        } else {
            weatherSub = shortCity;
        }
        styleHubButton(btnWeather, "天气窗台", weatherSub, 0xFFF2F8FE, 0xFFCFE2F5);
    }

    private void styleHubButton(Button button, String title, String subtitle, int fill, int border) {
        SpannableString label = new SpannableString(title + "\n" + subtitle);
        label.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(UiStyle.INK), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(0.84f), title.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(UiStyle.MUTED), title.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(label);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setBackground(UiStyle.ripple(this, fill, border, 1f, 18f, UiStyle.RIPPLE_PINK));
    }

    private void buildCostumeRow() {
        LinearLayout container = findViewById(R.id.costume_row);
        LinearLayout row = null;
        int cols = 4;
        for (int i = 0; i < PetSettings.COSTUME_NAMES.length; i++) {
            if (i % cols == 0) row = addButtonRow(container, i > 0);
            final int idx = i;
            Button b = choiceChip(PetSettings.COSTUME_NAMES[i], 12);
            b.setOnClickListener(v -> {
                settings.setCostume(idx);
                refresh();
            });
            row.addView(b);
            costumeButtons.add(b);
        }
        // 最后一行补齐占位保持对齐。
        int rem = PetSettings.COSTUME_NAMES.length % cols;
        if (rem != 0 && row != null) {
            for (int k = rem; k < cols; k++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.setMargins(dp(3), 0, dp(3), 0);
                row.addView(spacer, lp);
            }
        }
    }

    private void buildPatternRow() {
        LinearLayout container = findViewById(R.id.pattern_row);
        LinearLayout row = addButtonRow(container, false);
        for (int i = 0; i < PetSettings.PATTERN_NAMES.length; i++) {
            final int idx = i;
            Button b = choiceChip(PetSettings.PATTERN_NAMES[i], 12);
            b.setOnClickListener(v -> {
                settings.setPattern(idx);
                refresh();
            });
            row.addView(b);
            patternButtons.add(b);
        }
    }

    private Button choiceChip(String text, float textSizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(textSizeSp);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(44));
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return UiStyle.dp(this, value);
    }

    private LinearLayout addButtonRow(LinearLayout container, boolean topGap) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (topGap) lp.topMargin = dp(8);
        container.addView(row, lp);
        return row;
    }

    private void refreshButtonStates() {
        for (int i = 0; i < costumeButtons.size(); i++) {
            styleChipChoice(costumeButtons.get(i), i == settings.getCostume());
        }
        for (int i = 0; i < patternButtons.size(); i++) {
            styleChipChoice(patternButtons.get(i), i == settings.getPattern());
        }
        for (int i = 0; i < colorButtons.size(); i++) {
            styleColorChoice(colorButtons.get(i), PetSettings.COLOR_NAMES[i],
                    i == settings.getColor(), PetSettings.COLOR_TINTS[i]);
        }
    }

    private void styleChipChoice(Button button, boolean selected) {
        button.setSelected(selected);
        button.setBackground(UiStyle.ripple(this,
                selected ? UiStyle.ACCENT_SOFT : UiStyle.SURFACE_SOFT,
                selected ? UiStyle.ACCENT : UiStyle.BORDER,
                selected ? 1.8f : 1f, 14f, UiStyle.RIPPLE_PINK));
        button.setTextColor(selected ? UiStyle.ACCENT_DARK : UiStyle.INK);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setContentDescription(button.getText() + (selected ? "，已选中" : ""));
    }

    private void styleColorChoice(Button button, String name, boolean selected, int tint) {
        button.setSelected(selected);
        button.setText(selected ? "✓ " + name : name);
        int borderColor = selected ? UiStyle.ACCENT : (tint == 0xFFFFFFFF ? 0xFFE3D7D0 : 0x223E3440);
        button.setBackground(UiStyle.ripple(this, tint, borderColor,
                selected ? 2.4f : 1f, 14f, UiStyle.RIPPLE_PINK));
        boolean dark = Color.luminance(tint) < 0.35f;
        button.setTextColor(dark ? Color.WHITE : UiStyle.INK);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setContentDescription(name + (selected ? "，已选中" : ""));
    }

    private void buildColorRow() {
        LinearLayout container = findViewById(R.id.color_row);
        LinearLayout row = null;
        for (int i = 0; i < PetSettings.COLOR_NAMES.length; i++) {
            if (i % 4 == 0) row = addButtonRow(container, i > 0);
            final int idx = i;
            Button b = choiceChip(PetSettings.COLOR_NAMES[i], 12);
            b.setOnClickListener(v -> {
                settings.setColor(idx);
                refresh();
            });
            row.addView(b);
            colorButtons.add(b);
        }
    }

    private void buildSliders() {
        final TextView labelSize = findViewById(R.id.label_size);
        SeekBar seekSize = findViewById(R.id.seek_size);
        UiStyle.styleSeekBar(seekSize);
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
        UiStyle.styleSeekBar(seekOpacity);
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
        refreshStatusPill();
        if (settings.isVisible()) {
            if (!Settings.canDrawOverlays(this)) {
                settings.setVisible(false);
                stopService(new Intent(this, FloatingPetService.class));
                refreshStatusPill();
                return;
            }
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
        stageParams.height = px + dp(56);
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
        PetQuickTileService.requestSync(this);
        refreshStatusPill();
        Toast.makeText(this, "已显示悬浮猫", Toast.LENGTH_SHORT).show();
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
        finishOverlayRequest();
        if (!Settings.canDrawOverlays(this)) {
            settings.setVisible(false);
            stopService(new Intent(this, FloatingPetService.class));
        }
        touchThrough.setChecked(settings.isTouchThrough());
        musicDance.setChecked(settings.isMusicDanceEnabled());
        weatherCostume.setChecked(settings.isWeatherCostumeEnabled());
        hapticEnabled.setChecked(settings.isHapticEnabled());
        applyPreview();
        refreshStatusPill();
        refreshHubCards();
        refreshBondCard();
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
