package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class TransitServiceApp {

    static final String HUB_SERVICE_URL = "http://localhost:7051";
    static final String DELAY_STAGE_SERVICE_URL = "http://localhost:7052";

    // stand-in base travel times per province, in minutes - used since the ingested
    // hub data has no real distance/travel-time field to draw from
    static final Map<String, Integer> PROVINCE_BASE_MINUTES = provinceBaseMinutes();
    static final int DEFAULT_BASE_MINUTES = 90; // fallback for an unmapped/"Unknown" province

    static Map<String, Integer> provinceBaseMinutes() {
        Map<String, Integer> map = new HashMap<>();
        map.put("Gauteng", 45);
        map.put("Western Cape", 60);
        map.put("Eastern Cape", 75);
        map.put("KwaZulu-Natal", 60);
        map.put("Free State", 70);
        map.put("Limpopo", 80);
        map.put("Mpumalanga", 65);
        map.put("North West", 70);
        map.put("Northern Cape", 90);
        return map;
    }

    public static void main(String[] args) {
        createApp().start(7053);
    }

    // calculates an ETA in minutes from a base travel time plus a per-delay-stage penalty
    // pure arithmetic - no network, no server, easy to unit test in isolation
    static int calculateEta(int baseMinutes, int stage) {
        // each delay stage adds 30 minutes on top of the base travel time
        return baseMinutes + (stage * 30);
    }

    // looks up the base travel time for a hub's province, falling back to a default
    // for any province not in the table like "Unknown"
    static int baseMinutesForProvince(String province) {
        return PROVINCE_BASE_MINUTES.getOrDefault(province, DEFAULT_BASE_MINUTES);
    }

    // builds (but does not start) the Javalin app
    static Javalin createApp() {
        Javalin app = Javalin.create();

        // health check endpoint - confirms the service is up
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // fetch hub details from hub-service
            HttpRequest hubReq = HttpRequest.newBuilder(
                    URI.create(HUB_SERVICE_URL + "/hubs/" + hubId)).GET().build();
            HttpResponse<String> hubResp;
            try {
                hubResp = client.send(hubReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                ctx.status(502).json(Map.of("error", "hub-service unreachable"));
                return;
            }

            if (hubResp.statusCode() == 404) {
                ctx.status(404).json(Map.of("error", "hub not found: " + hubId));
                return;
            }

            JsonNode hub = mapper.readTree(hubResp.body());
            String province = hub.get("province").asText();
            int baseMinutes = baseMinutesForProvince(province);

            // fetch current delay stage from delay-stage-service
            HttpRequest stageReq = HttpRequest.newBuilder(
                    URI.create(DELAY_STAGE_SERVICE_URL + "/delay-stage/" + hubId)).GET().build();
            HttpResponse<String> stageResp;
            try {
                stageResp = client.send(stageReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                ctx.status(502).json(Map.of("error", "delay-stage-service unreachable"));
                return;
            }

            JsonNode stageNode = mapper.readTree(stageResp.body());
            int stage = stageNode.get("stage").asInt();

            // combine into an ETA
            int eta = calculateEta(baseMinutes, stage);

            // response
            ctx.json(Map.of(
                    "hubId", hubId,
                    "province", province,
                    "stage", stage,
                    "etaMinutes", eta
            ));
        });

        return app;
    }
}