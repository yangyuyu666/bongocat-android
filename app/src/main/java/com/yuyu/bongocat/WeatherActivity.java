package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

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
        sub.setText("降温或下雨时，悬浮猫会冒出云朵提醒你");
        sub.setTextSize(13);
        sub.setTextColor(UiStyle.MUTED);
        titleBox.addView(title);
        titleBox.addView(sub);
        header.addView(titleBox, titleLp);
        layout.addView(header);

        // 当前城市状态大卡片
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusCard.setBackground(UiStyle.gradientCard(this, 0xFFF3F8FE, 0xFFFCF6F2, 0xFFD4E3F3, 22f));
        LinearLayout.LayoutParams statusCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusCardLp.topMargin = dp(16);

        status = new TextView(this);
        status.setTextSize(20);
        status.setTextColor(UiStyle.INK);
        status.setTypeface(null, Typeface.BOLD);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusCard.addView(status);

        statusSub = new TextView(this);
        statusSub.setTextSize(13);
        statusSub.setTextColor(UiStyle.MUTED);
        statusSub.setPadding(0, dp(4), 0, 0);
        statusCard.addView(statusSub);
        layout.addView(statusCard, statusCardLp);
        showCity();

        // 城市搜索卡片
        LinearLayout searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.VERTICAL);
        searchCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        searchCard.setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 22f));
        LinearLayout.LayoutParams searchCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchCardLp.topMargin = dp(14);

        TextView searchLabel = new TextView(this);
        searchLabel.setText("选择或更换城市");
        searchLabel.setTextSize(15);
        searchLabel.setTextColor(UiStyle.INK);
        searchLabel.setTypeface(null, Typeface.BOLD);
        searchCard.addView(searchLabel);

        query = new EditText(this);
        query.setSingleLine();
        query.setHint("输入城市名或拼音");
        query.setTextSize(15);
        query.setTextColor(UiStyle.INK);
        query.setHintTextColor(UiStyle.MUTED);
        query.setPadding(dp(14), dp(12), dp(14), dp(12));
        query.setBackground(UiStyle.card(this, UiStyle.BG, UiStyle.BORDER, 14f));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        queryLp.topMargin = dp(10);
        searchCard.addView(query, queryLp);

        Button searchBtn = button(searchCard, "搜索城市", v -> search(), true);
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
        credit.setText("天气数据 · Open-Meteo");
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
            status.setText("天气窗台 · 选择城市");
            statusSub.setText("还没有绑定城市，输入中文或拼音搜索即可开启每日提醒");
        } else if (Weather.fresh(prefs.getLong("observed", 0), System.currentTimeMillis(), 2 * Weather.HOUR)) {
            status.setText(name + " · " + Math.round(prefs.getFloat("temp", 0)) + "°C");
            statusSub.setText("今日已同步天气 · 降温或有雨时小猫会贴心提醒");
        } else {
            status.setText(name + " · 天气提醒已开启");
            statusSub.setText("桌面悬浮猫陪伴时将自动查询当地最新天气");
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

    private Button choiceButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        UiStyle.styleSurfaceButton(this, button);
        button.setBackground(UiStyle.ripple(this, UiStyle.SURFACE_SOFT, UiStyle.BORDER, 1f, 14f, UiStyle.RIPPLE_PINK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        button.setMinHeight(dp(46));
        button.setOnClickListener(listener);
        parent.addView(button, lp);
        return button;
    }

    private void search() {
        String text = query.getText().toString().trim();
        int request = ++generation;
        choices.removeAllViews();
        if (text.isEmpty()) { query.setError("请输入城市名"); return; }
        status.setText("正在查找城市…");
        statusSub.setText("正在连接 Open-Meteo 城市列表");
        worker.execute(() -> {
            try {
                JSONArray results = Weather.cities(text);
                reply(request, () -> {
                    status.setText(results.length() == 0 ? "没有找到城市，试试拼音" : "选择你的城市");
                    statusSub.setText(results.length() == 0 ? "例如输入 Beijing、Shanghai 或 Hangzhou" : "轻点下方结果即可绑定该城市");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject city = results.optJSONObject(i);
                        if (city == null || city.isNull("latitude") || city.isNull("longitude")) continue;
                        String name = city.optString("name");
                        String label = android.text.TextUtils.join(" · ", new String[]{name, city.optString("admin1"), city.optString("country")});
                        String coords = city.optString("latitude") + "," + city.optString("longitude");
                        choiceButton(choices, label, v -> select(name, coords));
                    }
                });
            } catch (Exception error) {
                reply(request, () -> {
                    status.setText("城市查询失败，请重试");
                    statusSub.setText("请检查网络连接后再次搜索");
                });
            }
        });
    }

    private void select(String city, String coords) {
        generation++;
        new DailyWeather(prefs).setCity(city, coords);
        choices.removeAllViews();
        showCity();
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
