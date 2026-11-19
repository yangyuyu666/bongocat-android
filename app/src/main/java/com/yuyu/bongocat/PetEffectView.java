package com.yuyu.bongocat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 轻量级互动粒子层（爱心、问号、充电小闪电、听歌音符），与 500×500 画板对齐，空闲零开销。 */
public final class PetEffectView extends View {
    public static final int TYPE_HEART = 0;
    public static final int TYPE_QUESTION = 1;
    public static final int TYPE_LIGHTNING = 2;
    public static final int TYPE_NOTE = 3;

    private static final class Particle {
        final int type;
        final float startX, startY, driftX, riseY, baseScale;
        final long startTime, duration;
        final int color;

        Particle(int type, float x, float y, float driftX, float riseY, float scale, long start, long duration, int color) {
            this.type = type;
            this.startX = x;
            this.startY = y;
            this.driftX = driftX;
            this.riseY = riseY;
            this.baseScale = scale;
            this.startTime = start;
            this.duration = duration;
            this.color = color;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int noteToggle;

    public PetEffectView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void burstHearts() {
        long now = SystemClock.uptimeMillis();
        add(new Particle(TYPE_HEART, 212, 138, -22, 68, 1.0f, now, 950, 0xFFF27EA3));
        add(new Particle(TYPE_HEART, 268, 128, 20, 74, 0.85f, now + 90, 950, 0xFFF7A0BD));
        add(new Particle(TYPE_HEART, 240, 118, 4, 82, 0.72f, now + 180, 900, 0xFFE86490));
    }

    public void burstQuestion() {
        long now = SystemClock.uptimeMillis();
        add(new Particle(TYPE_QUESTION, 316, 136, 14, 48, 1.05f, now, 900, 0xFFD96B90));
    }

    /** 充电接通时冒出可爱小闪电与小金星。 */
    public void burstLightning() {
        long now = SystemClock.uptimeMillis();
        add(new Particle(TYPE_LIGHTNING, 242, 118, 0, 62, 1.25f, now, 1150, 0xFFFFD447));
        add(new Particle(TYPE_LIGHTNING, 198, 138, -18, 52, 0.82f, now + 110, 980, 0xFFFFE57F));
        add(new Particle(TYPE_LIGHTNING, 286, 136, 18, 54, 0.82f, now + 180, 980, 0xFFFFC130));
    }

    public void burstMusicNote() {
        long now = SystemClock.uptimeMillis();
        boolean left = (noteToggle++ % 2) == 0;
        add(new Particle(TYPE_NOTE, left ? 166 : 316, 152, left ? -22 : 22, 68, 1.0f, now, 1050,
                left ? 0xFFE878A2 : 0xFF8C8AE0));
    }

    private void add(Particle p) {
        if (particles.size() > 16) particles.remove(0);
        particles.add(p);
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth(), getHeight()) / 500f;
        if (scale <= 0) return;

        canvas.save();
        canvas.translate((getWidth() - 500 * scale) / 2f, (getHeight() - 500 * scale) / 2f);
        canvas.scale(scale, scale);

        boolean hasActive = false;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (now < p.startTime) { hasActive = true; continue; }
            float t = (now - p.startTime) / (float) p.duration;
            if (t >= 1f) { it.remove(); continue; }
            hasActive = true;

            float easeOut = 1f - (1f - t) * (1f - t);
            float pop = t < 0.22f ? (t / 0.22f) * 1.15f : 1.15f - 0.15f * Math.min(1f, (t - 0.22f) / 0.25f);
            int alpha = t > 0.65f ? Math.round(255 * (1f - (t - 0.65f) / 0.35f)) : 255;
            float x = p.startX + p.driftX * easeOut + (float) Math.sin(t * Math.PI * 2.5) * 5f;
            float y = p.startY - p.riseY * easeOut;

            canvas.save();
            canvas.translate(x, y);
            float s = p.baseScale * pop;
            canvas.scale(s, s);
            drawParticle(canvas, p.type, p.color, alpha);
            canvas.restore();
        }
        canvas.restore();
        if (hasActive) postInvalidateOnAnimation();
    }

    private void drawParticle(Canvas canvas, int type, int color, int alpha) {
        paint.setColor(color);
        paint.setAlpha(alpha);
        strokePaint.setColor(0xFF463C49);
        strokePaint.setAlpha(Math.round(alpha * 0.85f));
        strokePaint.setStrokeWidth(2.6f);

        if (type == TYPE_HEART) {
            path.reset();
            path.moveTo(0, 12);
            path.cubicTo(-18, -2, -19, -17, -7, -17);
            path.cubicTo(-2, -17, 0, -12, 0, -9);
            path.cubicTo(0, -12, 2, -17, 7, -17);
            path.cubicTo(19, -17, 18, -2, 0, 12);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            canvas.drawPath(path, strokePaint);
        } else if (type == TYPE_LIGHTNING) {
            path.reset();
            path.moveTo(4, -22);
            path.lineTo(-11, 2);
            path.lineTo(-1, 2);
            path.lineTo(-6, 22);
            path.lineTo(12, -3);
            path.lineTo(2, -3);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            strokePaint.setColor(0xFF9C6D14);
            strokePaint.setAlpha(alpha);
            canvas.drawPath(path, strokePaint);
        } else if (type == TYPE_QUESTION) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(34f);
            canvas.drawText("?", 0, 11, paint);
        } else if (type == TYPE_NOTE) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(30f);
            canvas.drawText("♪", 0, 10, paint);
        }
    }
}
