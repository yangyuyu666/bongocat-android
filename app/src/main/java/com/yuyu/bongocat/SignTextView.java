package com.yuyu.bongocat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** 文字沿用 Rive 的 500×500 坐标，放在两只爪子之间。 */
public final class SignTextView extends View {
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private String text = "";
    private StaticLayout layout;
    public SignTextView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void setText(String value) {
        if (text.equals(value)) return;
        text = value; paint.setColor(0xff614a42); paint.setTextSize(24);
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 194)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(194).build();
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null) return;
        float scale = Math.min(getWidth(), getHeight()) / 500f;
        canvas.save(); canvas.translate((getWidth() - 500 * scale)/2, (getHeight() - 500 * scale)/2); canvas.scale(scale, scale);
        canvas.translate(143, 339 + (55 - layout.getHeight())/2f); layout.draw(canvas);
        canvas.restore();
    }
    public boolean hits(float x, float y) {
        float scale = Math.min(getWidth(), getHeight()) / 500f;
        if (scale <= 0) return false;
        float localX = (x - (getWidth() - 500 * scale)/2)/scale;
        float localY = (y - (getHeight() - 500 * scale)/2)/scale;
        return localX >= 118 && localX <= 362 && localY >= 308 && localY <= 432;
    }
}
