package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static co.wethinkcode.logisticsconnect.TransitServiceApp.*;

class TransitServiceAppTest {

    // unit test - tests calculateEta() directly with a nonzero delay stage
    @Test
    void higherDelayStageIncreasesEta() {
        assertEquals(60, TransitServiceApp.calculateEta(60, 0));
        assertEquals(150, TransitServiceApp.calculateEta(60, 3));
    }

    // unit test - tests calculateEta()'s zero-input edge case
    @Test
    void zeroBaseWithZeroStageIsZero() {
        assertEquals(0, TransitServiceApp.calculateEta(0, 0));
    }

    // unit test - confirms a known province resolves to its mapped base time
    @Test
    void knownProvinceResolvesToMappedBaseMinutes() {
        assertEquals(45, TransitServiceApp.baseMinutesForProvince("Gauteng"));
        assertEquals(90, TransitServiceApp.baseMinutesForProvince("Northern Cape"));
    }

    // unit test - confirms an unmapped/unknown province falls back to the default
    @Test
    void unknownProvinceFallsBackToDefault() {
        assertEquals(DEFAULT_BASE_MINUTES, TransitServiceApp.baseMinutesForProvince("Unknown"));
        assertEquals(DEFAULT_BASE_MINUTES, TransitServiceApp.baseMinutesForProvince("Atlantis"));
    }

    // integration-style test - confirms the two pieces compose correctly end-to-end
    // without needing the actual HTTP server running
    @Test
    void combinesProvinceLookupAndDelayStageCorrectly() {
        int baseMinutes = TransitServiceApp.baseMinutesForProvince("Gauteng");
        assertEquals(135, TransitServiceApp.calculateEta(baseMinutes, 3));
    }
}