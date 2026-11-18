package com.yuyu.bongocat;

/** 保存竖屏坐标，并计算横屏跑到底角、藏身和探头窗口的位置。 */
public final class EdgePeekMotion {
    public static final int RUN = 1, TUCK = 2, PEEK = 3;
    public boolean active, right;
    public int phase, restoreX, restoreY;
    private int screenWidth, screenHeight, size;
    private float startX, startY;
    private long started, duration;

    public void enter(int x, int y, int width, int height, int base, long now) {
        if (!active) { restoreX = x; restoreY = y; }
        active = true;
        screenWidth = width; screenHeight = height; size = base;
        startX = clamp(x, width - base); startY = clamp(y, height - base);
        right = startX + base / 2f >= width / 2f;
        started = now; phase = RUN;
        duration = (long) Math.max(600, Math.min(1600,
                Math.hypot(targetX() - startX, height - base - startY) / Math.max(1, base) * 550));
    }

    public void resize(int width, int height, int base, long now) {
        if (!active) return;
        boolean wasPeeking = phase == PEEK;
        int[] current = bounds(now);
        boolean oldRight = right;
        enter(current[0], current[1], width, height, base, now);
        if (wasPeeking) { right = oldRight; started = now - duration - 420; phase = PEEK; }
    }

    public void pauseFor(long milliseconds) { started += Math.max(0, milliseconds); }

    public int[] bounds(long now) {
        long elapsed = Math.max(0, now - started);
        phase = elapsed < duration ? RUN : elapsed < duration + 420 ? TUCK : PEEK;
        if (phase == PEEK) {
            int width = Math.round(size * .62f), height = Math.round(size * .45f);
            return new int[]{targetX() + (size - width) / 2, screenHeight - height, width, height};
        }
        float t = Math.min(1, elapsed / (float) duration);
        t = t * t * (3 - 2 * t);
        return new int[]{Math.round(startX + (targetX() - startX) * t),
                Math.round(startY + (screenHeight - size - startY) * t), size, size};
    }

    public int[] leave(int width, int height, int base) {
        active = false; phase = 0;
        return new int[]{clamp(restoreX, width - base), clamp(restoreY, height - base)};
    }

    private int targetX() { return right ? screenWidth - size : 0; }
    private static int clamp(int value, int max) { return Math.max(0, Math.min(Math.max(0, max), value)); }
}
