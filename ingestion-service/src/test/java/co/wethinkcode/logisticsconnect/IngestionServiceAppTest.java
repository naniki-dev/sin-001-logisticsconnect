package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static co.wethinkcode.logisticsconnect.IngestionServiceApp.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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

    @Test
    void parseLineCleansAllFieldsTogether() {
        Hubs hub = IngestionServiceApp.parseLine(
                " h-502 ,gauteng,Pretoria  North,0",
                IngestionServiceApp.provinceLookup(),
                IngestionServiceApp.activeLookup());

        assertEquals("H-502", hub.getHubId());
        assertEquals("Gauteng", hub.getProvince());
        assertEquals("Pretoria North", hub.getSortingCenter());
        assertEquals(false, hub.getActive());
    }


    @Test
    void tiedActiveVoteResolvesToNull() {
        List<Hubs> group = List.of(
                new Hubs("H-501", "Western Cape", "Cape Town Port", true),
                new Hubs("H-502", "Western Cape", "Cape Town Port", false)
        );
        Hubs resolved = IngestionServiceApp.resolveDuplicate(group);
        assertNull(resolved.getActive());
    }


    @Test
    void loadAndCleanHubsSkipsHeader() throws IOException {
        String csv = String.join("\n",
                "hub_id,Province,sorting_center,active",
                "H-500,Gauteng,Johannesburg Central,Y",
                "H-504,gauteng,johannesburg central,true",
                "H-510,Gauteng,Johannesburg Central,FALSE"
        );

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            List<Hubs> cleaned = IngestionServiceApp.loadAndCleanHubs(reader);

            assertEquals(1, cleaned.size());
            Hubs hub = cleaned.get(0);
            assertEquals("H-500", hub.getHubId());
            assertEquals(true, hub.getActive());
        }
    }
}
