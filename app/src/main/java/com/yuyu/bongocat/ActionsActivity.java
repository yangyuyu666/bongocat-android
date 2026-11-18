package com.yuyu.bongocat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/** 动作页：点一下，浮窗里的猫就做这个动作。 */
public class ActionsActivity extends Activity {
    private static final String[] ACTION_NAMES = {
            "眨眼", "舔爪", "摇尾", "伸懒腰", "打哈欠", "东张西望", "爬一段", "翻肚皮", "趴地爬"};
    private static final String[] ACTION_TRIGGERS = {
            "playBlink", "playLick", "playTail", "playStretch", "playYawn", "playLook",
            "playCrawl", "playRoll", "playLieCrawl"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);
        LinearLayout container = findViewById(R.id.actions_container);
        LinearLayout row = null;
        for (int i = 0; i < ACTION_NAMES.length; i++) {
            if (i % 3 == 0) row = addButtonRow(container);
            final int idx = i;
            Button b = new Button(this);
            b.setText(ACTION_NAMES[i]);
            b.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int margin = dp(2);
            lp.setMargins(margin, 0, margin, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(dp(48));
            b.setPadding(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    playAction(idx);
                }
            });
            row.addView(b);
        }
    }

    private void playAction(int idx) {
        Intent i = new Intent(this, FloatingPetService.class);
        i.setAction(FloatingPetService.ACTION_PLAY);
        i.putExtra(FloatingPetService.EXTRA_TRIGGER, ACTION_TRIGGERS[idx]);
        startService(i);
        Toast.makeText(this, ACTION_NAMES[idx], Toast.LENGTH_SHORT).show();
    }

    private LinearLayout addButtonRow(LinearLayout container) {
        LinearLayout row = new LinearLayout(this);
        container.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
