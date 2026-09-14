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

// gets hubs from ingestion-service once at startup and caches them
// then gives them by ID
public class HubServiceApp {
    static Map<String, JsonNode> hubsByid = new HashMap<>();

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:7050/hubs")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        for (JsonNode hub : mapper.readTree(response.body())) {
            hubsByid.put(hub.get("hubId").asText(), hub);
        }

        Javalin app = Javalin.create().start(7051);
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.
        app.get("/hubs/{hubId}", ctx -> {
            JsonNode hub = hubsByid.get(ctx.pathParam("hubId"));
            if (hub == null) { ctx.status(404); return; }
            ctx.json(hub);
        });

    }
}
