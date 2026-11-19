package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 动作页：点一下，浮窗里的猫就做这个动作。 */
public class ActionsActivity extends Activity {
    private static final String[] ACTION_NAMES = {
            "眨眼", "舔爪", "摇尾", "伸懒腰", "打哈欠", "东张西望", "爬一段", "翻肚皮", "趴地爬"};
    private static final String[] ACTION_SUBS = {
            "俏皮连眨", "细心洗脸", "波浪甩尾", "舒展拱背", "困意来袭", "好奇张望", "迈步溜达", "撒娇打滚", "贴地前进"};
    private static final String[] ACTION_TRIGGERS = {
            "playBlink", "playLick", "playTail", "playStretch", "playYawn", "playLook",
            "playCrawl", "playRoll", "playLieCrawl"};
    private static final int[] TILE_FILLS = {
            0xFFFFF5F8, 0xFFFFF7EE, 0xFFF3F9F5,
            0xFFFFF6F0, 0xFFF5F3FC, 0xFFF2F8FE,
            0xFFF9F6EE, 0xFFFFF2F6, 0xFFF3F8F6};
    private static final int[] TILE_BORDERS = {
            0xFFF3D5DF, 0xFFF2DEC6, 0xFFCFE6D7,
            0xFFF3D9C8, 0xFFDED6F3, 0xFFCDE1F5,
            0xFFE9DFC5, 0xFFF4CCD9, 0xFFCDE3DA};

    private final java.util.List<Button> buttons = new java.util.ArrayList<Button>();
    private TextView subtitle;
    private int activeIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyle.applySystemBars(this);
        setContentView(R.layout.activity_actions);

        Button back = findViewById(R.id.btn_back);
        UiStyle.styleSurfaceButton(this, back);
        back.setOnClickListener(v -> finish());

        subtitle = findViewById(R.id.actions_subtitle);
        findViewById(R.id.actions_card).setBackground(UiStyle.card(this, UiStyle.SURFACE, UiStyle.BORDER, 22f));
        findViewById(R.id.tips_card).setBackground(UiStyle.gradientCard(this, UiStyle.STAGE_TOP, UiStyle.STAGE_BOTTOM, UiStyle.BORDER, 20f));

        LinearLayout container = findViewById(R.id.actions_container);
        LinearLayout row = null;
        for (int i = 0; i < ACTION_NAMES.length; i++) {
            if (i % 3 == 0) row = addButtonRow(container, i > 0);
            final int idx = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setStateListAnimator(null);
            b.setGravity(Gravity.CENTER);
            b.setLineSpacing(dp(3), 1f);
            b.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(82), 1f);
            int margin = dp(4);
            lp.setMargins(margin, 0, margin, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(6), dp(8), dp(6), dp(8));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> playAction(idx));
            row.addView(b);
            buttons.add(b);
        }
        refreshTiles();
    }

    private void refreshTiles() {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean selected = i == activeIndex;
            SpannableString text = new SpannableString(ACTION_NAMES[i] + "\n" + ACTION_SUBS[i]);
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, ACTION_NAMES[i].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(selected ? UiStyle.ACCENT_DARK : UiStyle.INK),
                    0, ACTION_NAMES[i].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.78f), ACTION_NAMES[i].length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(selected ? UiStyle.ACCENT : UiStyle.MUTED),
                    ACTION_NAMES[i].length() + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(text);
            b.setBackground(UiStyle.ripple(this,
                    selected ? UiStyle.ACCENT_SOFT : TILE_FILLS[i],
                    selected ? UiStyle.ACCENT : TILE_BORDERS[i],
                    selected ? 2f : 1f, 16f, UiStyle.RIPPLE_PINK));
        }
    }

    private void playAction(int idx) {
        activeIndex = idx;
        refreshTiles();
        View tile = buttons.get(idx);
        tile.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> tile.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
        subtitle.setText("正在播放：" + ACTION_NAMES[idx] + " · " + ACTION_SUBS[idx]);

        Intent i = new Intent(this, FloatingPetService.class);
        i.setAction(FloatingPetService.ACTION_PLAY);
        i.putExtra(FloatingPetService.EXTRA_TRIGGER, ACTION_TRIGGERS[idx]);
        startService(i);
        Toast.makeText(this, ACTION_NAMES[idx], Toast.LENGTH_SHORT).show();
    }

    private LinearLayout addButtonRow(LinearLayout container, boolean topGap) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (topGap) lp.topMargin = dp(8);
        container.addView(row, lp);
        return row;
    }

    private int dp(int value) {
        return UiStyle.dp(this, value);
    }
}
