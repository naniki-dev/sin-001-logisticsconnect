// TODO (Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).)
// Add domain endpoints for delay-stage-service here.
// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

package co.wethinkcode.logisticsconnect;

// javalin is a lightweight java & kotlin web framework
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public class DelayStageServiceApp {

    // in-memory store of each hub's current delay stage, keyed by hubId
    static Map<String, Integer> stages = new HashMap<>();

    public static void main(String[] args) {
        // builds the Javalin app around the shared stages map, then starts listening on port 7052
        createApp(stages).start(7052);
    }

    // builds (but does not start) the Javalin app - takes the stages map as a parameter
    // so tests can pass in their own empty/pre-filled map instead of the shared static one
    static Javalin createApp(Map<String, Integer> stages) {
        Javalin app = Javalin.create();

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // returns the current delay stage for a given hub
        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            // looks up the stored stage, defaulting to 0 (no delay) if this hub has never been set
            ctx.json(Map.of("hubId", hubId, "stage", stages.getOrDefault(hubId, 0)));
        });

        // updates a hub's delay stage - this is the stage/state-change endpoint from common/README.md
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            // reads the "stage" field out of the JSON request body and converts it to an int
            int stage = ((Number) ctx.bodyAsClass(Map.class).get("stage")).intValue();
            // stores the new stage value, overwriting any previous value for this hub
            stages.put(hubId, stage);
            // MQ TODO: publish this stage change onto MqConfig.TOPIC here for stage 3
            // responds with the hub ID and the stage that was just set, confirming the update
            ctx.status(200).json(Map.of("hubId", hubId, "stage", stage));
        });

        return app;
    }
}
