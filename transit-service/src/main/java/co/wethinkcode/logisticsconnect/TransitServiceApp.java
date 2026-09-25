// TODO (Calculates estimated arrival windows based on hub and delay stage.)
// Add domain endpoints for transit-service here.
// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

package co.wethinkcode.logisticsconnect;

// javalin is a lightweight java & kotlin web framework
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class TransitServiceApp {

    static final String HUB_SERVICE_URL = "http://localhost:7051";
    static final String DELAY_STAGE_SERVICE_URL = "http://localhost:7052";


    public static void main(String[] args) {
        // builds the Javalin app and starts listening on port 7053
        createApp().start(7053);
    }

    // calculates an ETA in minutes from a base travel time plus a per-delay-stage penalty
    // pure arithmetic - no network, no server, easy to unit test in isolation
    static int calculateEta(int baseMinutes, int stage) {
        // each delay stage adds 30 minutes on top of the base travel time
        return baseMinutes + (stage * 30);
    }

    // builds (but does not start) the Javalin app
    static Javalin createApp() {
        Javalin app = Javalin.create();

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: /eta/{hubId} goes here - will call hub-service for location data
        // and delay-stage-service for the current stage, then combine them via calculateEta

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // fetch hub details
            HttpRequest hubReq = HttpRequest.newBuilder(
                    URI.create(HUB_SERVICE_URL + "/hubs/" + hubId)).GET().build();
            HttpResponse<String> hubResp = client.send(hubReq, HttpResponse.BodyHandlers.ofString());

            if (hubResp.statusCode() == 404) {
                ctx.status(404).json(Map.of("error", "hub not found: " + hubId));
                return;
            }

            JsonNode hub = mapper.readTree(hubResp.body());
            int baseMinutes = hub.get("baseMinutes").asInt(); // <-- confirm real field name

            // fetch current delay stage
            HttpRequest stageReq = HttpRequest.newBuilder(
                    URI.create(DELAY_STAGE_SERVICE_URL + "/delay-stage/" + hubId)).GET().build();
            HttpResponse<String> stageResp = client.send(stageReq, HttpResponse.BodyHandlers.ofString());

            JsonNode stageNode = mapper.readTree(stageResp.body());
            int stage = stageNode.get("stage").asInt();

            // combine it
            int eta = calculateEta(baseMinutes, stage);

            // response
            ctx.json(Map.of(
                    "hubId", hubId,
                    "stage", stage,
                    "etaMinutes", eta
            ));
        });

        return app;
    }
}
