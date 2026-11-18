package com.yuyu.bongocat;

/** 将视图触点换算到 500×500 画板，再按当前姿势选择回应。 */
public final class PetInteraction {
    public enum Action { NONE, WAKE, TAIL, HEAD, NOSE, LEFT_PAW, RIGHT_PAW, STRETCH, BELLY, SHY }
    private long lastHeadTap = -2000;
    private int headTaps;

    public Action tap(float x, float y, int width, int height, boolean cover,
                      int state, float progress, long now) {
        if (width <= 0 || height <= 0) return Action.NONE;
        float scale = (cover ? Math.max(width, height) : Math.min(width, height)) / 500f;
        float px = (x - (width - 500 * scale) / 2) / scale;
        float py = (y - (height - 500 * scale) / 2) / scale;
        if (px < 0 || px > 500 || py < 0 || py > 500) return Action.NONE;
        if (state == 13 && progress >= .34f && progress <= .66f
                && ellipse(px, py, 277, 332, 50, 48)) return Action.BELLY;
        if (state == 17 && ellipse(px, py, 240, 202, 116, 92)) return head(now, false);
        if (state != 0) {
            headTaps = 0;
            return state >= 17 ? Action.NONE : Action.WAKE;
        }
        if (ellipse(px, py, 240, 225, 20, 16)) { headTaps = 0; return Action.NOSE; }
        if (ellipse(px, py, 240, 202, 113, 87)) return head(now, true);
        headTaps = 0;
        if (ellipse(px, py, 184, 324, 27, 35)) return Action.LEFT_PAW;
        if (ellipse(px, py, 296, 324, 27, 35)) return Action.RIGHT_PAW;
        float[][] tail = {{352,367},{383,351},{405,320},{402,290}};
        for (int i = 1; i < tail.length; i++) {
            if (segment(px, py, tail[i - 1], tail[i]) <= 19) return Action.TAIL;
        }
        if (ellipse(px, py, 240, 350, 76, 51)) return Action.STRETCH;
        return Action.NONE;
    }

    private Action head(long now, boolean idle) {
        headTaps = now - lastHeadTap <= 650 ? headTaps + 1 : 1;
        lastHeadTap = now;
        if (headTaps >= 3) { headTaps = 0; return Action.SHY; }
        return idle ? Action.HEAD : Action.NONE;
    }

    public void reset() { headTaps = 0; lastHeadTap = -2000; }

    private static boolean ellipse(float x, float y, float cx, float cy, float rx, float ry) {
        float dx = (x - cx) / rx, dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1;
    }

    private static float segment(float x, float y, float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
        return (float) Math.hypot(x - a[0] - t * dx, y - a[1] - t * dy);
    }
}
