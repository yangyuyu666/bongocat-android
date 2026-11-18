package com.yuyu.bongocat;

/** 将一次爬行动作映射成有边界的短路径，靠边时朝空间较大的一侧爬。 */
public final class CrawlMotion {
    private float fromX, fromY, toX, toY;
    public float x, y;
    public void begin(float x, float y, float minX, float minY, float maxX, float maxY, float dx, float dy) {
        fromX = this.x = clamp(x, minX, maxX);
        fromY = this.y = clamp(y, minY, maxY);
        toX = target(fromX, dx, minX, maxX);
        toY = target(fromY, dy, minY, maxY);
    }
    private static float target(float from, float delta, float min, float max) {
        float direct = clamp(from + delta, min, max);
        float reverse = clamp(from - delta, min, max);
        return Math.abs(direct - from) < Math.min(Math.abs(delta), (max - min) * 0.35f)
                && Math.abs(reverse - from) > Math.abs(direct - from) ? reverse : direct;
    }
    public boolean move(float progress) {
        float p = clamp(progress, 0, 1);
        x = fromX + (toX - fromX) * p;
        y = fromY + (toY - fromY) * p;
        return Math.abs(toX - fromX) + Math.abs(toY - fromY) > 0.01f;
    }
    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
}
