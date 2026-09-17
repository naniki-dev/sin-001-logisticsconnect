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
}