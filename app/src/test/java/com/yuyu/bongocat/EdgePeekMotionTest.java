package com.yuyu.bongocat;

import org.junit.Test;
import static org.junit.Assert.*;

public class EdgePeekMotionTest {
    @Test public void runsToNearestBottomCornerThenOnlyAllocatesHeadWindow() {
        EdgePeekMotion motion = new EdgePeekMotion();
        motion.enter(40, 80, 1920, 1080, 300, 0);
        assertFalse(motion.right);
        int[] start = motion.bounds(0);
        assertArrayEquals(new int[]{40, 80, 300, 300}, start);
        int[] end = motion.bounds(3000);
        assertEquals(EdgePeekMotion.PEEK, motion.phase);
        assertEquals(186, end[2]); assertEquals(135, end[3]);
        assertEquals(1080, end[1] + end[3]);
        assertTrue(end[0] >= 0 && end[0] + end[2] <= 1920);
    }

    @Test public void rightCornerAndAllIntermediateWindowsStayInsideScreen() {
        EdgePeekMotion motion = new EdgePeekMotion();
        motion.enter(1750, 900, 1920, 1080, 300, 0);
        assertTrue(motion.right);
        for (long t = 0; t < 3000; t += 16) {
            int[] b = motion.bounds(t);
            assertTrue(b[0] >= 0 && b[1] >= 0);
            assertTrue(b[0] + b[2] <= 1920 && b[1] + b[3] <= 1080);
        }
    }

    @Test public void rotateBackRestoresPortraitPositionEvenAfterRestartAndResize() {
        EdgePeekMotion motion = new EdgePeekMotion();
        motion.enter(620, 1400, 1920, 1080, 300, 0);
        motion.bounds(3000);
        motion.resize(1600, 900, 240, 3500);
        assertEquals(EdgePeekMotion.PEEK, motion.phase);
        motion.enter(900, 500, 1600, 900, 240, 4000);
        assertArrayEquals(new int[]{620, 1400}, motion.leave(1080, 1920, 300));
        assertFalse(motion.active);
    }

    @Test public void interruptedRunAndTouchPauseDoNotJumpToFinalCorner() {
        EdgePeekMotion motion = new EdgePeekMotion();
        motion.enter(100, 120, 1920, 1080, 300, 0);
        int[] before = motion.bounds(200);
        motion.pauseFor(2000);
        assertArrayEquals(before, motion.bounds(2200));
        assertArrayEquals(new int[]{100, 120}, motion.leave(1080, 1920, 300));
        motion.enter(100, 120, 1920, 1080, 300, 2300);
        assertEquals(EdgePeekMotion.RUN, motion.phase);
    }
}
