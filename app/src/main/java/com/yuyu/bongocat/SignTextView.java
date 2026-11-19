package com.yuyu.bongocat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** 文字与自定义手账纸风格沿用 Rive 的 500×500 坐标，放在两只爪子之间。 */
public final class SignTextView extends View {
    private static final int[] PAPER_FILL = {0x00000000, 0xFFFFEEF4, 0xFFEFF8EE};
    private static final int[] PAPER_STROKE = {0x00000000, 0xFFF2C2D4, 0xFFC4DFBE};
    private static final int[] TEXT_COLORS = {0xFF614A42, 0xFF7A3E57, 0xFF3E5E44};
    private static final int[] DOT_COLORS = {0x00000000, 0xFFE88AA9, 0xFF7CB686};

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF innerCard = new RectF(134, 323, 346, 414);
    private String text = "";
    private int style;
    private StaticLayout layout;

    public SignTextView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setStyle(int nextStyle) {
        int clamped = nextStyle >= 0 && nextStyle < TEXT_COLORS.length ? nextStyle : 0;
        if (style == clamped) return;
        style = clamped;
        rebuildLayout();
        invalidate();
    }

    public void setText(String value) {
        if (text.equals(value)) return;
        text = value;
        rebuildLayout();
        invalidate();
    }

    private void rebuildLayout() {
        paint.setColor(TEXT_COLORS[style]);
        paint.setTextSize(24);
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 194)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(194).build();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null) return;
        float scale = Math.min(getWidth(), getHeight()) / 500f;
        canvas.save();
        canvas.translate((getWidth() - 500 * scale) / 2, (getHeight() - 500 * scale) / 2);
        canvas.scale(scale, scale);

        // 非默认奶油手账时，在内框叠加樱花粉纸或抹茶便签纸面。
        if (style > 0) {
            cardPaint.setStyle(Paint.Style.FILL);
            cardPaint.setColor(PAPER_FILL[style]);
            canvas.drawRoundRect(innerCard, 9, 9, cardPaint);
            cardPaint.setStyle(Paint.Style.STROKE);
            cardPaint.setStrokeWidth(2f);
            cardPaint.setColor(PAPER_STROKE[style]);
            canvas.drawRoundRect(innerCard, 9, 9, cardPaint);
            cardPaint.setStyle(Paint.Style.FILL);
            cardPaint.setColor(DOT_COLORS[style]);
            canvas.drawCircle(146, 331, 3.2f, cardPaint);
            canvas.drawCircle(334, 331, 3.2f, cardPaint);
        }

        canvas.translate(143, 339 + (55 - layout.getHeight()) / 2f);
        layout.draw(canvas);
        canvas.restore();
    }

    public boolean hits(float x, float y) {
        float scale = Math.min(getWidth(), getHeight()) / 500f;
        if (scale <= 0) return false;
        float localX = (x - (getWidth() - 500 * scale) / 2) / scale;
        float localY = (y - (getHeight() - 500 * scale) / 2) / scale;
        return localX >= 118 && localX <= 362 && localY >= 308 && localY <= 432;
    }
}
