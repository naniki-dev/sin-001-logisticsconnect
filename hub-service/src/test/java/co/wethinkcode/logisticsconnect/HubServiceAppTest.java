package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HubServiceAppTest {

    // unit test - tests indexByHubId() as a pure transformation, no server, no network
    @Test
    void indexesHubsByHubId() throws Exception {
        JsonNode hubsArray = new ObjectMapper().readTree(
                "[{\"hubId\":\"H-500\",\"province\":\"Gauteng\"}]");
        Map<String, JsonNode> index = HubServiceApp.indexByHubId(hubsArray);

        assertTrue(index.containsKey("H-500"));
        assertEquals("Gauteng", index.get("H-500").get("province").asText());
    }

    // integration test - boots a real (in-memory) Javalin app and makes a real
    // HTTP request against it; fake data is injected so it doesn't depend on ingestion-service
    @Test
    void returnsHubByIdWhenPresent() throws Exception {
        JsonNode hub = new ObjectMapper().readTree("{\"hubId\":\"H-500\",\"province\":\"Gauteng\"}");
        JavalinTest.test(HubServiceApp.createApp(Map.of("H-500", hub)), (server, client) -> {
            var res = client.get("/hubs/H-500");
            assertEquals(200, res.code());
            assertTrue(res.body().string().contains("Gauteng"));
        });
    }

    // integration test - same as above, checks the 404 branch of the real endpoint
    @Test
    void returns404WhenHubMissing() {
        JavalinTest.test(HubServiceApp.createApp(Map.of()), (server, client) -> {
            var res = client.get("/hubs/H-999");
            assertEquals(404, res.code());
        });
    }
}