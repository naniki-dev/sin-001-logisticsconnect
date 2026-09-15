package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static co.wethinkcode.logisticsconnect.IngestionServiceApp.*;

import java.util.List;

public class IngestionServiceAppTest {
    @Test
    void cleansProvinceCasing() {
        assertEquals("Gauteng", IngestionServiceApp.cleanProvince("gauteng", provinceLookup()));
        assertEquals("Gauteng", IngestionServiceApp.cleanProvince(" Gauteng ", provinceLookup()));
    }

    @Test
    void unknownProvince() {
        assertEquals("Unknown", IngestionServiceApp.cleanProvince("", provinceLookup()));
    }

    @Test
    void removesDoubleSpacesAndTitleCases() {
        assertEquals("Cape Town Port", IngestionServiceApp.titleCase("cape town  port"));
    }

    @Test
    void cleansTruthyBooleans() {
        for (String v : List.of("Y", "yes", "1", "true", "TRUE")) {
            assertEquals(true, IngestionServiceApp.cleanActive(v, activeLookup()));
        }
    }

    @Test
    void resolvesDuplicateGroupByLowestIdAndMajorityVote() {
        List<Hubs> group = List.of(
                new Hubs("H-504", "Gauteng", "Johannesburg Central", true),
                new Hubs("H-500", "Gauteng", "Johannesburg Central", true),
                new Hubs("H-510", "Gauteng", "Johannesburg Central", false)
        );
        Hubs resolved = IngestionServiceApp.resolveDuplicate(group);
        // lowest ID wins the identity
        assertEquals("H-500", resolved.getHubId());
        // 2 true vs 1 false
        assertEquals(true, resolved.getActive());
    }
}
