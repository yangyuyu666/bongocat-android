package com.yuyu.bongocat;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.yuyu.bongocat.PetInteraction.Action.*;

public class PetInteractionTest {
    @Test public void idlePartsSelectDifferentActions() {
        PetInteraction pet = new PetInteraction();
        assertEquals(TAIL, pet.tap(402, 300, 500, 500, false, 0, 0, 0));
        assertEquals(NOSE, pet.tap(240, 225, 500, 500, false, 0, 0, 0));
        assertEquals(HEAD, pet.tap(240, 155, 500, 500, false, 0, 0, 0));
        assertEquals(LEFT_PAW, pet.tap(184, 324, 500, 500, false, 0, 0, 0));
        assertEquals(RIGHT_PAW, pet.tap(296, 324, 500, 500, false, 0, 0, 0));
        assertEquals(STRETCH, pet.tap(240, 359, 500, 500, false, 0, 0, 0));
        assertEquals(NONE, pet.tap(20, 20, 500, 500, false, 0, 0, 0));
    }

    @Test public void containAndCoverMapBackToTheSamePart() {
        PetInteraction pet = new PetInteraction();
        assertEquals(TAIL, pet.tap(72.36f, 54, 90, 90, false, 0, 0, 0));
        assertEquals(TAIL, pet.tap(527, 300, 750, 500, false, 0, 0, 0));
        assertEquals(TAIL, pet.tap(603, 325, 750, 500, true, 0, 0, 0));
        assertEquals(NONE, pet.tap(20, 200, 750, 500, false, 0, 0, 0));
    }

    @Test public void bellyWorksOnlyWhileFacingUpAndSleepStillWakes() {
        PetInteraction pet = new PetInteraction();
        assertEquals(BELLY, pet.tap(277, 332, 500, 500, false, 13, .5f, 0));
        assertEquals(WAKE, pet.tap(277, 332, 500, 500, false, 13, .1f, 0));
        assertEquals(WAKE, pet.tap(402, 300, 500, 500, false, 10, 0, 0));
        assertEquals(WAKE, pet.tap(402, 300, 500, 500, false, 11, 0, 0));
    }

    @Test public void threeHeadTapsWorkDuringPettingAndExpireAfterPauseOrDrag() {
        PetInteraction pet = new PetInteraction();
        assertEquals(HEAD, pet.tap(240, 155, 500, 500, false, 0, 0, 0));
        assertEquals(NONE, pet.tap(240, 155, 500, 500, false, 17, 0, 200));
        assertEquals(SHY, pet.tap(240, 155, 500, 500, false, 17, 0, 400));
        assertEquals(NONE, pet.tap(240, 155, 500, 500, false, 17, 0, 1200));
        pet.reset();
        assertEquals(HEAD, pet.tap(240, 155, 500, 500, false, 0, 0, 1300));
    }
}
