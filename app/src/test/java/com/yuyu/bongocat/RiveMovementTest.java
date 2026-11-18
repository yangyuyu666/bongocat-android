package com.yuyu.bongocat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RiveMovementTest {
    private static class Host implements RiveMovement.Host {
        int starts, stops;
        final CrawlMotion path = new CrawlMotion();
        float lastProgress;
        @Override public void begin(float dx, float dy) {
            starts++;
            path.begin(300, 400, 0, 0, 900, 1400, dx, dy);
        }
        @Override public boolean move(float p) { lastProgress = p; return path.move(p); }
        @Override public void stopped() { stops++; }
    }

    @Test public void riveCrawlCompletesAtDestinationAndSavesOnce() {
        RiveMovement movement = new RiveMovement();
        Host host = new Host();
        movement.update(7, 0.5f, 180, -50, host);
        assertEquals(390, host.path.x, 0.01);
        assertEquals(375, host.path.y, 0.01);
        movement.update(8, 0, 180, -50, host);
        assertEquals(480, host.path.x, 0.01);
        assertEquals(350, host.path.y, 0.01);
        movement.update(8, 0, 180, -50, host);
        assertEquals(1, host.starts);
        assertEquals(1, host.stops);
    }

    @Test public void draggingDoesNotRestartAnOldRiveFrameOrJumpToDestination() {
        RiveMovement movement = new RiveMovement();
        Host host = new Host();
        movement.update(7, 0.25f, 180, 50, host);
        movement.cancel(host);
        movement.update(7, 0.3f, 180, 50, host);
        movement.update(8, 0, 180, 50, host);
        assertEquals(345, host.path.x, 0.01);
        assertEquals(1, host.starts);
        assertEquals(1, host.stops);
        movement.update(7, 0, -180, -50, host);
        assertEquals(2, host.starts);
    }

    @Test public void edgesReverseAndProgressNeverEscapesWindow() {
        CrawlMotion motion = new CrawlMotion();
        motion.begin(0, 0, 0, 0, 900, 1400, -180, -50);
        motion.move(1);
        assertEquals(180, motion.x, 0.01);
        assertEquals(50, motion.y, 0.01);
        motion.begin(900, 1400, 0, 0, 900, 1400, 180, 50);
        motion.move(1);
        assertEquals(720, motion.x, 0.01);
        assertEquals(1350, motion.y, 0.01);
        motion.begin(0, 0, -74, -32, 74, 32, 180, 50);
        for (int frame = -20; frame <= 140; frame++) {
            assertTrue(motion.move(frame / 120f));
            assertTrue(motion.x >= -74 && motion.x <= 74 && motion.y >= -32 && motion.y <= 32);
        }
        motion.begin(0, 0, 0, 0, 0, 0, 180, 50);
        assertFalse(motion.move(0.5f));
    }

    @Test public void sleepInterruptsCrawlAtItsCurrentPosition() {
        RiveMovement movement = new RiveMovement();
        Host host = new Host();
        movement.update(7, 0.5f, 180, 0, host);
        movement.update(9, 0, 180, 0, host);
        assertEquals(390, host.path.x, 0.01);
        assertEquals(1, host.stops);
        assertFalse(movement.isMoving());
    }

    @Test public void lyingCrawlMovesLikeSittingCrawl() {
        RiveMovement movement = new RiveMovement();
        Host host = new Host();
        movement.update(11, 0.5f, 180, -50, host);
        assertEquals(390, host.path.x, 0.01);
        assertEquals(375, host.path.y, 0.01);
        movement.update(12, 0, 180, -50, host);
        assertEquals(480, host.path.x, 0.01);
        assertEquals(350, host.path.y, 0.01);
        movement.update(12, 0, 180, -50, host);
        assertEquals(1, host.starts);
        assertEquals(1, host.stops);
    }
}
