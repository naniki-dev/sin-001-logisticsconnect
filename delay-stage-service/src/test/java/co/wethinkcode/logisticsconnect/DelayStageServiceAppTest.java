package co.wethinkcode.logisticsconnect;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import static co.wethinkcode.logisticsconnect.DelayStageServiceApp.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DelayStageServiceAppTest {

    // integration test - real Javalin app, real HTTP GET, checks the default-to-zero behavior
    @Test
    void unknownHubDefaultsToStageZero() {
        JavalinTest.test(DelayStageServiceApp.createApp(new HashMap<>()), (server, client) -> {
            var res = client.get("/delay-stage/H-999");
            assertEquals(200, res.code());
            assertTrue(res.body().string().contains("\"stage\":0"));
        });
    }

    // integration test - real Javalin app, POST then GET, checks the state actually persists between requests
    @Test
    void postThenGetReturnsUpdatedStage() {
        Map<String, Integer> stages = new HashMap<>();
        JavalinTest.test(DelayStageServiceApp.createApp(stages), (server, client) -> {
            var post = client.post("/delay-stage/H-500", "{\"stage\":3}");
            assertEquals(200, post.code());

            var get = client.get("/delay-stage/H-500");
            assertTrue(get.body().string().contains("\"stage\":3"));
        });
    }
}