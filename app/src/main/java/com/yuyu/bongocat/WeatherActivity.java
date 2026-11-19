package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WeatherActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout choices;
    private TextView status, statusSub;
    private EditText query;
    private int generation;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        UiStyle.applySystemBars(this);
        prefs = Weather.preferences(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiStyle.BG);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        layout.setPadding(pad, pad, pad, dp(28));
        scroll.addView(layout);
        setContentView(scroll);

        // 顶栏：返回按钮 + 标题
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(22);
        back.setPadding(0, 0, 0, 0);
        back.setMinWidth(0);
        back.setMinimumWidth(0);
        back.setMinHeight(0);
        back.setMinimumHeight(0);
        UiStyle.styleSurfaceButton(this, back);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        TextView title = new TextView(this);
        title.setText("天气窗台");
        title.setTextSize(22);
        title.setTextColor(UiStyle.INK);
        title.setTypeface(null, Typeface.BOLD);
        TextView sub = new TextView(this);
        sub.setText("每天播报气温与是否下雨，并提醒穿衣带伞");
        sub.setTextSize(13);
        sub.setTextColor(UiStyle.MUTED);
        titleBox.addView(title);
        titleBox.addView(sub);
        header.addView(titleBox, titleLp);
        layout.addView(header);

        // 当前地点与今日天气大卡片
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusCard.setBackground(UiStyle.gradientCard(this, 0xFFF3F8FE, 0xFFFCF6F2, 0xFFD4E3F3, 22f));
        LinearLayout.LayoutParams statusCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusCardLp.topMargin = dp(16);

        status = new TextView(this);
        status.setTextSize(19);
        status.setTextColor(UiStyle.INK);
        status.setTypeface(null, Typeface.BOLD);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusCard.addView(status);

        statusSub = new TextView(this);
        statusSub.setTextSize(13);
        statusSub.setTextColor(UiStyle.MUTED);
        statusSub.setLineSpacing(dp(3), 1f);
        statusSub.setPadding(0, dp(6), 0, 0);
        statusCard.addView(statusSub);

        // 手动刷新 & 桌面预览云朵播报快捷操作行
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        quickLp.topMargin = dp(12);

        Button refreshNow = new Button(this);
        refreshNow.setText("立即刷新");
        refreshNow.setTextSize(13);
        refreshNow.setTypeface(null, Typeface.BOLD);
        UiStyle.styleSurfaceButton(this, refreshNow);
        refreshNow.setOnClickListener(v -> refreshCurrentCity());
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        rLp.rightMargin = dp(6);
        quickRow.addView(refreshNow, rLp);

        Button previewBtn = new Button(this);
        previewBtn.setText("桌面预览播报");
        previewBtn.setTextSize(13);
        previewBtn.setTypeface(null, Typeface.BOLD);
        UiStyle.styleSecondaryButton(this, previewBtn);
        previewBtn.setOnClickListener(v -> previewOnDesktop());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, dp(40), 1.15f);
        pLp.leftMargin = dp(6);
        quickRow.addView(previewBtn, pLp);

        statusCard.addView(quickRow, quickLp);
        layout.addView(statusCard, statusCardLp);
        showCity();

        // 随天气自动换装开关卡片
        PetSettings petSettings = new PetSettings(this);
        LinearLayout dressCard = new LinearLayout(this);
        dressCard.setOrientation(LinearLayout.VERTICAL);
        dressCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        dressCard.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 20f));
        LinearLayout.LayoutParams dressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dressLp.topMargin = dp(12);
        android.widget.Switch autoDress = new android.widget.Switch(this);
        autoDress.setText("随天气自动换装");
        autoDress.setTextSize(15);
        autoDress.setTextColor(UiStyle.INK);
        autoDress.setTypeface(null, Typeface.BOLD);
        autoDress.setChecked(petSettings.isWeatherCostumeEnabled());
        autoDress.setThumbTintList(android.content.res.ColorStateList.valueOf(UiStyle.ACCENT));
        autoDress.setTrackTintList(android.content.res.ColorStateList.valueOf(0xFFEBD3DC));
        autoDress.setOnCheckedChangeListener((b, enabled) -> {
            petSettings.setWeatherCostumeEnabled(enabled);
            if (petSettings.isVisible()) {
                startService(new Intent(this, FloatingPetService.class)
                        .setAction(FloatingPetService.ACTION_UPDATE_SETTINGS));
            }
        });
        dressCard.addView(autoDress);
        TextView dressHint = new TextView(this);
        dressHint.setText("下雨自动戴雨帽 · 降温裹围巾 · 晴热戴墨镜");
        dressHint.setTextSize(12);
        dressHint.setTextColor(UiStyle.MUTED);
        dressCard.addView(dressHint);
        layout.addView(dressCard, dressLp);

        // 高精度地点搜索卡片
        LinearLayout searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.VERTICAL);
        searchCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        searchCard.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 22f));
        LinearLayout.LayoutParams searchCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchCardLp.topMargin = dp(14);

        TextView searchLabel = new TextView(this);
        searchLabel.setText("精确搜索区县 / 街道 / 商圈");
        searchLabel.setTextSize(15);
        searchLabel.setTextColor(UiStyle.INK);
        searchLabel.setTypeface(null, Typeface.BOLD);
        searchCard.addView(searchLabel);

        query = new EditText(this);
        query.setSingleLine();
        query.setHint("如：深圳南山区、杭州余杭、徐家汇");
        query.setTextSize(15);
        query.setTextColor(UiStyle.INK);
        query.setHintTextColor(UiStyle.MUTED);
        query.setPadding(dp(14), dp(12), dp(14), dp(12));
        query.setBackground(UiStyle.card(this, UiStyle.BG, UiStyle.BORDER, 14f));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        queryLp.topMargin = dp(10);
        searchCard.addView(query, queryLp);

        Button searchBtn = button(searchCard, "搜索地点", v -> search(), true);
        searchBtn.setTextSize(15);
        query.setOnEditorActionListener((v, id, event) -> { search(); return true; });

        choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams choicesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        choicesLp.topMargin = dp(6);
        searchCard.addView(choices, choicesLp);
        layout.addView(searchCard, searchCardLp);

        Button closeBtn = button(layout, "关闭天气提醒", v -> {
            generation++;
            new DailyWeather(prefs).setCity("", "");
            finish();
        }, false);
        ((LinearLayout.LayoutParams) closeBtn.getLayoutParams()).topMargin = dp(14);

        TextView credit = new TextView(this);
        credit.setText("天气数据 · Open-Meteo · 地点检索 · OpenStreetMap");
        credit.setTextSize(12);
        credit.setTextColor(UiStyle.MUTED);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(16), 0, dp(8));
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))));
        layout.addView(credit);
    }

    private int dp(float value) {
        return UiStyle.dp(this, value);
    }

    private void showCity() {
        String name = prefs.getString("city", "");
        if (name.isEmpty()) {
            status.setText("天气窗台 · 选择位置");
            statusSub.setText("支持精确到区县、街道和商圈，选定后每日自动播报气温、是否下雨及穿衣带伞建议");
        } else if (Weather.fresh(prefs.getLong("observed", 0), System.currentTimeMillis(), 2 * Weather.HOUR)) {
            double temp = prefs.getFloat("temp", 0);
            double minTemp = prefs.contains("tempMin") ? prefs.getFloat("tempMin", Float.NaN) : Double.NaN;
            double maxTemp = prefs.contains("tempMax") ? prefs.getFloat("tempMax", Float.NaN) : Double.NaN;
            boolean rain = prefs.getBoolean("rain", false);
            String label = Weather.weatherLabel(prefs.getInt("weatherCode", -1));
            String head = name + " · " + Math.round(temp) + "°C" + (label.isEmpty() ? "" : " " + label);
            status.setText(head);
            StringBuilder detail = new StringBuilder();
            if (Double.isFinite(minTemp) && Double.isFinite(maxTemp)) {
                detail.append("今日 ").append(Math.round(minTemp)).append("°~").append(Math.round(maxTemp)).append("°C · ");
            }
            detail.append(rain ? "会下雨" : "不下雨").append("\n");
            detail.append(Weather.advice(temp, minTemp, maxTemp, rain));
            statusSub.setText(detail.toString());
        } else {
            status.setText(name + " · 天气播报已开启");
            statusSub.setText("悬浮猫在桌面陪伴时会自动获取当地今日气温与降雨情况");
        }
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener listener, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(null, primary ? Typeface.BOLD : Typeface.NORMAL);
        if (primary) UiStyle.stylePrimaryButton(this, button);
        else UiStyle.styleSecondaryButton(this, button);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        button.setOnClickListener(listener);
        parent.addView(button, lp);
        return button;
    }

    private Button choiceButton(LinearLayout parent, Weather.Place place, View.OnClickListener listener) {
        Button button = new Button(this);
        String textStr = place.detail.isEmpty() ? place.title : place.title + "\n" + place.detail;
        SpannableString span = new SpannableString(textStr);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, place.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(UiStyle.INK), 0, place.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!place.detail.isEmpty()) {
            span.setSpan(new RelativeSizeSpan(0.84f), place.title.length() + 1, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(UiStyle.MUTED), place.title.length() + 1, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        button.setText(span);
        button.setTextSize(14);
        button.setLineSpacing(dp(2), 1f);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        UiStyle.styleSurfaceButton(this, button);
        button.setBackground(UiStyle.ripple(this, UiStyle.SURFACE_SOFT, UiStyle.BORDER, 1f, 14f, UiStyle.RIPPLE_PINK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        button.setMinHeight(dp(52));
        button.setOnClickListener(listener);
        parent.addView(button, lp);
        return button;
    }

    private void search() {
        String text = query.getText().toString().trim();
        int request = ++generation;
        choices.removeAllViews();
        if (text.isEmpty()) { query.setError("请输入城市、区县或街道名"); return; }
        status.setText("正在查找精确位置…");
        statusSub.setText("支持省市区、乡镇街道及商圈检索");
        worker.execute(() -> {
            try {
                List<Weather.Place> results = Weather.searchPlaces(text);
                reply(request, () -> {
                    status.setText(results.isEmpty() ? "没有找到位置，试试带上省市名" : "选择精确位置");
                    statusSub.setText(results.isEmpty() ? "例如输入：深圳南山区、杭州余杭区、北京海淀" : "已按区县/街道级精度返回，轻点即可绑定");
                    for (Weather.Place place : results) {
                        choiceButton(choices, place, v -> select(place));
                    }
                });
            } catch (Exception error) {
                reply(request, () -> {
                    status.setText("位置查询失败，请重试");
                    statusSub.setText("请检查网络连接后再次搜索");
                });
            }
        });
    }

    private void select(Weather.Place place) {
        int request = ++generation;
        String coords = place.coords();
        new DailyWeather(prefs).setCity(place.displayName, coords);
        choices.removeAllViews();
        status.setText(place.displayName + " · 正在获取今日天气…");
        statusSub.setText("已绑定高精度坐标（" + coords + "）");
        worker.execute(() -> {
            try {
                Weather latest = Weather.fetch(coords);
                reply(request, () -> {
                    if (coords.equals(prefs.getString("coords", ""))) {
                        latest.save(prefs.edit(), System.currentTimeMillis());
                        showCity();
                    }
                });
            } catch (Exception ignored) {
                reply(request, this::showCity);
            }
        });
    }

    private void refreshCurrentCity() {
        String coords = prefs.getString("coords", "");
        if (coords.isEmpty()) {
            status.setText("请先在下方搜索并绑定位置");
            return;
        }
        int request = ++generation;
        status.setText(prefs.getString("city", "") + " · 正在刷新…");
        worker.execute(() -> {
            try {
                Weather latest = Weather.fetch(coords);
                reply(request, () -> {
                    if (coords.equals(prefs.getString("coords", ""))) {
                        latest.save(prefs.edit(), System.currentTimeMillis());
                        showCity();
                    }
                });
            } catch (Exception ignored) {
                reply(request, () -> {
                    status.setText("刷新失败，请稍后重试");
                    statusSub.setText("请检查网络连接");
                });
            }
        });
    }

    private void previewOnDesktop() {
        if (prefs.getString("coords", "").isEmpty()) {
            android.widget.Toast.makeText(this, "请先搜索并选择一个位置", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        startService(new Intent(this, FloatingPetService.class)
                .setAction(FloatingPetService.ACTION_PREVIEW_WEATHER));
        android.widget.Toast.makeText(this, "已在桌面小猫头顶预览播报", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void reply(int request, Runnable action) {
        runOnUiThread(() -> { if (request == generation && !isFinishing() && !isDestroyed()) action.run(); });
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
